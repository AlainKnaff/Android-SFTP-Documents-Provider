package com.island.sftp;

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
 Copyright (C) 2025,2026 Alain Knaff
 Copyright (C) 2019,2020 Riccardo Isola

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Objects;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
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
import android.util.Log;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import com.island.androidsftpdocumentsprovider.account.Account;

import com.jcraft.jsch.UserInfo;

public class SFTP extends SSH implements Closeable
{
	private static final String TAG = "SFTP";
	private static final int BUFFER=1024;
	public  String documentId;
	private ChannelSftp channel;
	private boolean disconnected;

	private Map<String,SftpFile> files = new HashMap<>();

	public static String accountToDocumentId(Account account) {
	  	return account.getName();
	}

        static Pattern accountPattern = Pattern.compile("(?:sftp://?)?([^/]*)(.*)");

        public static String documentIdToAccount(String documentId) {
                Matcher m = accountPattern.matcher(documentId);
                if(m.matches())
                        return m.group(1);
                else
                        return documentId;
        }

        // remove prefix of older scheme
        public static String normalize(String documentId) {
                return documentId.replaceAll("^sftp://", "");
        }

        public SFTP(Context ctx, String documentId, Account account)
                throws ConnectException
	{
		this(ctx, documentId, account, null);
	}

        public SFTP(Context ctx, String documentId,
		    Account account, UserInfo userInfo)
                throws ConnectException
        {
		super(ctx, account, userInfo);
		this.documentId=documentId;
		String startDirectory = account.getDirectory();
		if(startDirectory==null || startDirectory.length()==0)
			startDirectory="/";
		files.put(startDirectory,
			  SftpFile.makeDirectory(startDirectory));

        }

	protected Session makeSession() throws JSchException {
		Session session = super.makeSession();
		channel=(ChannelSftp)session.openChannel("sftp");
		channel.connect();
		return session;
	}

	private synchronized void reconnectIfNeeded() throws JSchException {
		if(getSession()==null)
			makeSession();
		if(!getSession().isConnected()) {
			try {
				Log.d(TAG,"Reconnecting session");
				retrySessionConnect(getSession());
			} catch(JSchException e) {
				// if it fails, just re-create the session from scratch
				// https://stackoverflow.com/questions/16127200/jsch-how-to-keep-the-session-alive-and-up
				Log.d(TAG,
				      "Session unusable, create a new one");
				makeSession();
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
                if(file.getIsDirectory() == null) {
                        listFile(file);
                }
                return file.getIsDirectory();
        }
	@Override
	public synchronized void close() throws IOException
	{
		if(getSession() != null)
			getSession().disconnect();
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
		SftpFile sfile = getFileForPath(file.getPath());
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

        public SftpFile getFile(String documentId)
        {
                Objects.requireNonNull(documentId);
                Matcher m = accountPattern.matcher(documentId);
                String path;
                if(m.matches())
                        path = m.group(2);
                else
                        path = "";
                if(path.length() == 0 || path.charAt(0) != '/')
                        path = getAccount().getDirectory()+"/"+path;
                return getFileForPath(path);
        }

        private SftpFile getFileForPath(String path) {
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
	public String getDocumentId(File file)
	{
		Objects.requireNonNull(file);
		return documentIdToAccount(documentId)+file.getPath();
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
			ProtocolException exception=new ProtocolException("sftp");
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
