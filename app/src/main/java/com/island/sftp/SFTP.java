package com.island.sftp;

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
 Copyright (C) 2025,2026 Alain Knaff
 Copyright (C) 2019,2020 Riccardo Isola

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Objects;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.Properties;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.SocketException;
import java.net.ProtocolException;
import java.net.ConnectException;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import com.island.androidsftpdocumentsprovider.provider.Unlocked;
import com.island.androidsftpdocumentsprovider.account.Account;

import com.jcraft.jsch.UserInfo;

public class SFTP implements Closeable
{
	private static final String TAG = "SFTP";
	private static final int TIMEOUT=20000;
	private static final int BUFFER=1024;
	public static final String SCHEME="sftp://";
	public  Uri uri;
	private Account account;
	private Session session;
	private ChannelSftp channel;
	private Context context;
	private boolean disconnected;
	private JSch jsch;


	private Map<String,SftpFile> files = new HashMap<>();

	public static Uri parseUri(String name) {
		return Uri.parse(SFTP.SCHEME+name);
	}

        public SFTP(Context ctx, Uri uri, Account account)
                throws ConnectException
	{
		this(ctx, uri, account, null);
	}

        public SFTP(Context ctx, Uri uri,
		    Account account, UserInfo userInfo)
                throws ConnectException
        {
                Log.d(TAG,String.format("Created new connection for %s",account.getHostName()));
                BouncyCastle.trigger();
                this.context=ctx;
                checkArguments(account);
                this.uri=uri;
                this.account=account;
                String privKey = Keygen.readPrivateKey(ctx);
                jsch=new JSch();
                jsch.setLogger(new Logger());
		String startDirectory = account.getDirectory();
		if(startDirectory==null || startDirectory.length()==0)
			startDirectory="/";
		files.put(startDirectory,
			  SftpFile.makeDirectory(startDirectory));

                try {
			File dir = ctx.getFilesDir();
			jsch.setKnownHosts(new File(dir,"known_hosts")
					   .toString());
                        if(privKey != null)
                                jsch.addIdentity(privKey);
                        if(userInfo != null)
                                makeSession(userInfo);
                } catch(JSchException e) {
                        Log.e(TAG, "JschException during init: "+e, e);
                        ConnectException exception=new ConnectException(String.format("Can't connect to %s",uri));
                        exception.initCause(e);
                        throw exception;
                }
        }

	private void makeSession(UserInfo userInfo) throws JSchException {
		boolean noRetry=false;
		if(userInfo == null) {
			userInfo = new com.island.sftp.UserInfo();
		} else {
			noRetry = true;
		}
		session=jsch.getSession(account.getUserName(),
					account.getHostName(),
					account.getPort());
		Properties config=new Properties();
		config.put("StrictHostKeyChecking","ask");

		// check only Kexes that are actually proposed in
		// config.get("kex")
		// this eliminates the very slow to check
		// sntrup761x25519 KEXes without any ill effect
		// On Pixel7a, checkKexes now only take 23
		// milliseconds rather than 1300!
		config.put("CheckKexes","mlkem768x25519-sha256,curve25519-sha256,curve25519-sha256@libssh.org");

		session.setConfig(config);
		session.setUserInfo(userInfo);

		String socksProxy = account.getSocksProxy();
		if(socksProxy != null && !socksProxy.isEmpty()) {
			int socksPort;
			String socksHost;
			int idx = socksProxy.lastIndexOf(':');
			if(idx == -1) {
				socksHost=socksProxy;
				socksPort = 1080;
			} else {
				socksHost = socksProxy.substring(0,idx);
				socksPort = Integer.parseInt(socksProxy.substring(idx+1));
			}
			ProxySOCKS5 proxy = new ProxySOCKS5(socksHost,
							    socksPort);
			session.setProxy(proxy);
		}

		String password = account.getPassword();
		if(password != null && !password.isEmpty())
			session.setPassword(password);

		session.setTimeout(TIMEOUT);
		if(noRetry)
			session.connect();
		else
			retrySessionConnect();
		channel=(ChannelSftp)session.openChannel("sftp");
		channel.connect();
	}

	private void retrySessionConnect()
		throws JSchException
	{
		CompletableFuture<Boolean> wfu=null;
		for(int i=0; i<15; i++) {
			if(i==1)
				wfu = Unlocked.getWfu(context);
			else if(i == 2)
				Unlocked.waitForUnlock(wfu, 10);
			else if(i > 2) {
				Log.d(TAG, "Waiting for "+10000+" milliseconds");
				Unlocked.waitForUnlock(wfu, 10000);
				Log.d(TAG, "Waiting for "+10+" extra milliseconds");
				try {
					Thread.sleep(10);
				} catch(Exception e) {
				}
			}
			try {
				Log.d(TAG, this + " connecting session");
				session.connect();
				break;
			} catch(JSchException e) {
				Log.i(TAG, "Exception while connecting "+e+
				      " "+i);
				if(i == 14) {
					Log.e(TAG, "Giving up");
					throw e;
				}
			}
		}
	}

	private synchronized void reconnectIfNeeded() throws JSchException {
		if(session==null)
			makeSession(null);
		if(!session.isConnected()) {
			try {
				Log.d(TAG,"Reconnecting session");
				retrySessionConnect();
			} catch(JSchException e) {
				// if it fails, just re-create the session from scratch
				// https://stackoverflow.com/questions/16127200/jsch-how-to-keep-the-session-alive-and-up
				Log.d(TAG,
				      "Session unusable, create a new one");
				makeSession(null);
			}
		}
		if(!channel.isConnected()) {
			Log.d(TAG,"Reconnecting channel");
			channel.connect();
		}
	}
        public long lastModified(SftpFile file)throws IOException
        {
                checkArguments(file);
                if(file.getSftpLastModified() == -1)
                        listFile(file);
                return file.getSftpLastModified();
        }
        public long length(SftpFile file)throws IOException
        {
                checkArguments(file);
                if(file.getSize() == -1)
                        listFile(file);
                return file.getSize();
        }
        public boolean isDirectory(SftpFile file)throws IOException
        {
                checkArguments(file);
                if(file.getIsDirectory() == null)
                        listFile(file);
                return file.getIsDirectory();
        }
	@Override
	public synchronized void close() throws IOException
	{
		if(session != null)
			session.disconnect();
		if(channel != null)
			channel.quit();
		disconnected=true;
	}
        public synchronized void listFile(SftpFile file)
                throws IOException
        {
                Log.d(TAG, "List file "+file);
                checkArguments(file);
                try {
                        reconnectIfNeeded();
                        try {
                                SftpATTRS attrs = channel.stat(file.getPath());
                                Log.d(TAG, "Found "+file);
                                file.setAttrs(attrs);
                        } catch(SftpException e) {
                                if(e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                                        // all is ok, file simply doesn't exist
                                        Log.d(TAG, "Not found "+file);
                                        throw new FileNotFoundException(file.getPath());
                                }
                                throw e;
                        }
                } catch(JSchException e) {
                        throw getException(e);
                } catch(SftpException e) {
                        throw getException(e);
                }
        }
	public synchronized SftpFile[]listFiles(File directory)throws IOException
	{
		checkArguments(directory);
		try {
			reconnectIfNeeded();
			Vector vector=channel.ls(directory.getPath());
			List<SftpFile>files=new ArrayList<>(vector.size());
			for(Object obj:vector) {
				ChannelSftp.LsEntry entry=
					(ChannelSftp.LsEntry) obj;
				if(entry.getFilename().equals(".")||
				   entry.getFilename().equals(".."))
					continue;
				SftpFile file;
				SftpATTRS attrs=entry.getAttrs();
				if(attrs.isLink()) {
					File link = new File(directory,
							     entry.getFilename());
					try {
						attrs=channel.stat(link.getPath());
					} catch(Exception e) {
						Log.e(TAG,
						      "Could not read "+link.getPath());
						continue;
					}
				}
				String fileName = entry.getFilename();
				file=new SftpFile(directory, fileName, attrs);
				this.files.put(fileName, file);
				files.add(file);
			}
			return files.toArray(new SftpFile[0]);
		} catch(JSchException e) {
			throw getException(e);
		} catch(SftpException e) {
			throw getException(e);
		}
	}
	public synchronized void newFile(SftpFile file)throws IOException
	{
		checkArguments(file);
		try {
			reconnectIfNeeded();
			channel.put(file.getPath()).close();
                        file.markAsNewFile();
		} catch(JSchException e) {
			throw getException(e);
		} catch(SftpException e) {
			throw getException(e);
		}
	}
	public synchronized void delete(SftpFile file)throws IOException
	{
		checkArguments(file);
		try {
			reconnectIfNeeded();
			if(isDirectory(file)) {
				for(SftpFile child:listFiles(file))
                                        delete(child);
				channel.rmdir(file.getPath());
			} else
				channel.rm(file.getPath());
		} catch(JSchException e) {
			throw getException(e);
		} catch(SftpException e) {
			throw getException(e);
		}
	}
	public synchronized InputStream read(File file, long offset)throws IOException
	{
		checkArguments(file);
		try {
			reconnectIfNeeded();
			return channel.get(file.getPath(), null, offset);
		} catch(JSchException e) {
			throw getException(e);
		} catch(SftpException e) {
			throw getException(e);
		}
	}
	public synchronized void mkdirs(SftpFile file)throws IOException
	{
		checkArguments(file);
		try {
			reconnectIfNeeded();
			channel.mkdir(file.getPath());
                        file.markAsNewDirectory();
		} catch(JSchException e) {
			throw getException(e);
		} catch(SftpException e) {
			throw getException(e);
		}
	}
	public boolean exists(File file)throws IOException
	{
		checkArguments(file);
		SftpFile sfile = getFile(file.getPath());
		try {
                        if(sfile.getIsDirectory() == null)
                                listFile(sfile);
                        Log.d(TAG, "getIsDirectory of "+file+"="+
                              sfile.getIsDirectory());
                        return sfile.getIsDirectory() != null;
		} catch(FileNotFoundException e) {
			Log.d(TAG, "file "+file+" not found on server");
			return false;
		}
	}
	public synchronized void renameTo(File oldPath,File newPath)throws IOException
	{
		checkArguments(oldPath,newPath);
		try {
			reconnectIfNeeded();
			channel.rename(oldPath.getPath(),newPath.getPath());
		} catch(JSchException e) {
			throw getException(e);
		} catch(SftpException e) {
			throw getException(e);
		}
	}
	public synchronized OutputStream write(File file, int mode, long offset)throws IOException
	{
		checkArguments(file);
		try {
			reconnectIfNeeded();
			return channel.put(file.getPath(), null, mode, offset);
		} catch(JSchException e) {
			throw getException(e);
		} catch(SftpException e) {
			throw getException(e);
		}
	}
	public SftpFile getFile(Uri uri)
	{
		Objects.requireNonNull(uri);
                var path = uri.getPath();
                return getFile(path);
        }

        public SftpFile getFile(String path) {
                SftpFile cachedFile = files.get(path);
                if(cachedFile == null) {
                        Log.d(TAG, "File "+path+" not found in cache");
                        cachedFile = new SftpFile(path);
                        files.put(path, cachedFile);
                } else {
                        Log.d(TAG, "File "+path+" found in cache");
                }
                return cachedFile;
	}
	public Uri getUri(File file)
	{
		Objects.requireNonNull(file);
		return Uri.parse(SCHEME+uri.getAuthority()+file.getPath());
	}
	public synchronized void copy(File from,File to)throws IOException
	{
		checkArguments(from,to);
		try {
			reconnectIfNeeded();
			InputStream input=new BufferedInputStream(channel.get(from.getPath()));
			OutputStream output=new BufferedOutputStream(channel.put(to.getPath()));
			byte[]buffer=new byte[BUFFER];
			while(true)if(write(input,output,buffer)==-1)break;
			input.close();
			output.close();
		} catch(JSchException e) {
			throw getException(e);
		} catch(SftpException e) {
			throw getException(e);
		}
	}
	public String getMimeType(SftpFile file)throws IOException
	{
		Objects.requireNonNull(file);
		if(isDirectory(file)) {
			return DocumentsContract.Document.MIME_TYPE_DIR;
		} else {
			String name=file.getName();
			int lastDot=name.lastIndexOf('.');
			if(lastDot>=0) {
				String extension=name.substring(lastDot+1);
				String mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
				if(mime!=null)return mime;
			}
			return"application/octet-stream";
		}
	}
	private static int write(InputStream input,OutputStream output,byte[]buffer)throws IOException
	{
		assert input!=null;
		assert output!=null;
		assert buffer!=null;
		int bytesRead=input.read(buffer);
		if(bytesRead!=-1) {
			output.write(buffer,0,bytesRead);
		}
		return bytesRead;
	}

	public interface ProgressObserver {
		void update(long wrote);
	}

	public static void writeAll(InputStream input,
				    OutputStream output,
				    ProgressObserver progressNotification)
		throws IOException
	{
		Objects.requireNonNull(input);
		Objects.requireNonNull(output);
		input=new BufferedInputStream(input);
		output=new BufferedOutputStream(output);
		byte[]buffer=new byte[SFTP.BUFFER];
		int bytesRead=0;
		long wrote=0;
		while((bytesRead=SFTP.write(input,output,buffer))!=-1) {
			wrote+=bytesRead;
			progressNotification.update(wrote);
		}
		input.close();
		output.close();
	}
	public static void writeAll(InputStream input,OutputStream output)throws IOException
	{
		writeAll(input,output,null);
	}
	private IOException getException(Exception cause)
	{
		assert cause!=null;

		if(cause.getCause()!=null) {
			SocketException exception=new SocketException("Connection closed");
			exception.initCause(cause);
			return exception;
		} else {
			ProtocolException exception=new ProtocolException(uri.getScheme());
			exception.initCause(cause);
			return exception;
		}
	}
	private void checkArguments(Object...arguments)
	{
		assert arguments!=null;
		for(Object argument:arguments)Objects.requireNonNull(argument,Arrays.toString(arguments));
		if(disconnected)throw new IllegalStateException("Connection already closed");
	}

        public boolean isConnected() {
                return channel != null && channel.isConnected();
        }
}
