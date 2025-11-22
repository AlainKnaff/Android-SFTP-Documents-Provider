package com.island.sftp;

import java.util.List;
import java.util.Objects;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Observer;
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

import android.net.Uri;
import android.util.Log;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;
import android.os.StrictMode;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import com.island.androidsftpdocumentsprovider.provider.SFTPProvider;

public class SFTP implements Closeable
{
	private static final int TIMEOUT=20000;
	private static final int BUFFER=1024;
	public static final String SCHEME="sftp://";
	public  Uri uri;
	private  String password;
	private  Session session;
	private  ChannelSftp channel;
	private final HashMap<File,Long>lastModified=new HashMap<>();
	private final HashMap<File,Long>size=new HashMap<>();
	private final HashMap<File,Boolean>directory=new HashMap<>();
	private boolean disconnected;

	public static Uri parseUri(String name) {
		return Uri.parse(SFTP.SCHEME+name);
	}

	public SFTP(Uri uri,String password)throws ConnectException
	{
		init(uri, password);
	}

	public SFTP() {
	}

	private JSch jsch;
	
	protected void init(Uri uri, String password) throws ConnectException {
		Log.d(SFTPProvider.TAG,String.format("Created new connection for %s",uri.getAuthority()));
		checkArguments(uri,password);
		cancelStrictMode();
		this.uri=uri;
		this.password=password;
		jsch=new JSch();
		directory.put(new File("/"),true);
		lastModified.put(new File("/"),0l);
		try
		{
			makeSession();
		}
		catch(JSchException e)
		{
			ConnectException exception=new ConnectException(String.format("Can't connect to %s",uri));
			exception.initCause(e);
			throw exception;
		}
	}

	private void makeSession() throws JSchException {
		session=jsch.getSession(uri.getUserInfo(),uri.getHost(),uri.getPort());
		session.setPassword(password);
		Properties config=new Properties();
		config.put("StrictHostKeyChecking","no");
		session.setConfig(config);
		session.setTimeout(TIMEOUT);
		session.setConfig("PreferredAuthentications","password");
		session.connect();
		channel=(ChannelSftp)session.openChannel("sftp");
		channel.connect();
	}

	private void cancelStrictMode() {
		// if an applications directly opens a file on this root from
		// its UI thread, the StrictMode attached to that thread
		// carries over to this application via binder, preventing it
		// to invoke network (for SFTP), although it's really the
		// calling app's fault. => cancel StrictMode this is
		// legitimate as we can't really do anything about it, as
		// Documents Provider API is syncrhonous (return from same
		// method, rather than calling a callback when done). Any
		// solution other than canceling StrictMode would involve
		// cheating by handing processing off to another thread, but
		// then waiting for that thread, blocking anyways
		StrictMode.ThreadPolicy gfgPolicy = 
			new StrictMode.ThreadPolicy.Builder()
			.permitAll()
			.build();
		StrictMode.setThreadPolicy(gfgPolicy);
	}

	private synchronized void reconnectIfNeeded() throws JSchException {
		cancelStrictMode();
		if(!session.isConnected()) {
			try {
				Log.d(SFTPProvider.TAG,"Reconnecting session");
				session.connect();
			} catch(JSchException e) {
				// if it fails, just re-create the session from scratch
				// https://stackoverflow.com/questions/16127200/jsch-how-to-keep-the-session-alive-and-up
				Log.d(SFTPProvider.TAG,
				      "Session unusable, create a new one");
				makeSession();
			}
		}
		if(!channel.isConnected()) {
			Log.d(SFTPProvider.TAG,"Reconnecting channel");
			channel.connect();
		}
	}

	private<T>T getValue(Map<File,T>map,File file)throws IOException
	{
		checkArguments(map,file);
		if(!map.containsKey(file))
		{
			Log.d(SFTPProvider.TAG,"Requested file attributes are unknown");
			listFiles(file.getParentFile());
		}
		if(!map.containsKey(file))throw new FileNotFoundException(String.format("File %s is missing",file));
		return map.get(file);
	}
	public long lastModified(File file)throws IOException
	{
		checkArguments(file);
		return getValue(lastModified,file);
	}
	public long length(File file)throws IOException
	{
		checkArguments(file);
		return getValue(size,file);
	}
	public boolean isDirectory(File file)throws IOException
	{
		checkArguments(file);
		return getValue(directory,file);
	}
	@Override
	public synchronized void close() throws IOException
	{
		session.disconnect();
		channel.quit();
		disconnected=true;
	}
	public synchronized File[]listFiles(File file)throws IOException
	{
		checkArguments(file);
		try
		{
			reconnectIfNeeded();
			Vector vector=channel.ls(file.getPath());
			List<File>files=new ArrayList<>(vector.size()-2);
			for(Object obj:vector)
			{
				ChannelSftp.LsEntry entry=
				    (ChannelSftp.LsEntry) obj;
				if(entry.getFilename().equals(".")||entry.getFilename().equals(".."))continue;
				File newFile=new File(file,entry.getFilename());
				SftpATTRS attributes=entry.getAttrs();
				files.add(newFile);
				lastModified.put(newFile,attributes.getMTime()*1000L);
				size.put(newFile,attributes.getSize());
				directory.put(newFile,attributes.isDir());
			}
			return files.toArray(new File[0]);
		}
		catch(JSchException e)
		{
			throw getException(e);
		}
		catch(SftpException e)
		{
			throw getException(e);
		}
	}
	public synchronized void newFile(File file)throws IOException
	{
		checkArguments(file);
		try
		{
			reconnectIfNeeded();
			channel.put(file.getPath()).close();
		}
		catch(JSchException e)
		{
			throw getException(e);
		}
		catch(SftpException e)
		{
			throw getException(e);
		}
	}
	public synchronized void delete(File file)throws IOException
	{
		checkArguments(file);
		try
		{
			reconnectIfNeeded();
			if(isDirectory(file))
			{
				for(File child:listFiles(file))delete(child);
				channel.rmdir(file.getPath());
			}
			else channel.rm(file.getPath());
		}
		catch(JSchException e)
		{
			throw getException(e);
		}
		catch(SftpException e)
		{
			throw getException(e);
		}
	}
	public synchronized InputStream read(File file)throws IOException
	{
		checkArguments(file);
		try
		{
			reconnectIfNeeded();
			return new BufferedInputStream(channel.get(file.getPath()));
		}
		catch(JSchException e)
		{
			throw getException(e);
		}
		catch(SftpException e)
		{
			throw getException(e);
		}
	}
	public synchronized void mkdirs(File file)throws IOException
	{
		checkArguments(file);
		try
		{
			reconnectIfNeeded();
			channel.mkdir(file.getPath());
		}
		catch(JSchException e)
		{
			throw getException(e);
		}
		catch(SftpException e)
		{
			throw getException(e);
		}
	}
	public boolean exists(File file)throws IOException
	{
		checkArguments(file);
		try
		{
			getValue(directory,file);
			return true;
		}
		catch(FileNotFoundException e)
		{
			return false;
		}
	}
	public synchronized void renameTo(File oldPath,File newPath)throws IOException
	{
		checkArguments(oldPath,newPath);
		try
		{
			reconnectIfNeeded();
			channel.rename(oldPath.getPath(),newPath.getPath());
		}
		catch(JSchException e)
		{
			throw getException(e);
		}
		catch(SftpException e)
		{
			throw getException(e);
		}
	}
	public synchronized OutputStream write(File file)throws IOException
	{
		checkArguments(file);
		try
		{
			reconnectIfNeeded();
			return channel.put(file.getPath());
		}
		catch(JSchException e)
		{
			throw getException(e);
		}
		catch(SftpException e)
		{
			throw getException(e);
		}
	}
	public static File getFile(Uri uri)
	{
		Objects.requireNonNull(uri);
		return new File(uri.getPath());
	}
	public Uri getUri(File file)
	{
		Objects.requireNonNull(file);
		return Uri.parse(SCHEME+uri.getAuthority()+file.getPath());
	}
	public synchronized void get(File from,File to)throws IOException
	{
		checkArguments(from,to);
		try
		{
			Log.d(SFTPProvider.TAG, String.format("Get server file %s to %s", from.getAbsolutePath(), to.getAbsolutePath()));
			reconnectIfNeeded();
			channel.get(from.getPath(),to.getPath());
			long lastModified = lastModified(from);
			to.setLastModified(lastModified);
		}
		catch(JSchException e)
		{
			throw getException(e);
		}
		catch(SftpException e)
		{
			throw getException(e);
		}
	}
	public synchronized void copy(File from,File to)throws IOException
	{
		checkArguments(from,to);
		try
		{
			reconnectIfNeeded();
			InputStream input=new BufferedInputStream(channel.get(from.getPath()));
			OutputStream output=new BufferedOutputStream(channel.put(to.getPath()));
			byte[]buffer=new byte[BUFFER];
			while(true)if(write(input,output,buffer)==-1)break;
			input.close();
			output.close();
		}
		catch(JSchException e)
		{
			throw getException(e);
		}
		catch(SftpException e)
		{
			throw getException(e);
		}
	}
	public String getMimeType(File file)throws IOException
	{
		Objects.requireNonNull(file);
        if(isDirectory(file))
		{
			return DocumentsContract.Document.MIME_TYPE_DIR;
		}
		else
		{
			String name=file.getName();
			int lastDot=name.lastIndexOf('.');
			if(lastDot>=0)
			{
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
		if(bytesRead!=-1)
		{   
			output.write(buffer,0,bytesRead);
		}
		return bytesRead;
	}
	public static void writeAll(InputStream input,OutputStream output,Observer observer)throws IOException
	{
		Objects.requireNonNull(input);
		Objects.requireNonNull(output);
		input=new BufferedInputStream(input);
		output=new BufferedOutputStream(output);
		byte[]buffer=new byte[SFTP.BUFFER];
		int bytesRead=0;
		long wrote=0;
		while((bytesRead=SFTP.write(input,output,buffer))!=-1)
		{
			wrote+=bytesRead;
			observer.update(null,wrote);
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
		if(cause.getCause()!=null)
		{
			SocketException exception=new SocketException("Connection closed");
			exception.initCause(cause);
			return exception;
		}
		else
		{
			ProtocolException exception=new ProtocolException(uri.getScheme());
			exception.initCause(cause);
			return exception;
		}
	}
	private void checkArguments(Object...arguments)
	{
		cancelStrictMode();
		assert arguments!=null;
		for(Object argument:arguments)Objects.requireNonNull(argument,Arrays.toString(arguments));
		if(disconnected)throw new IllegalStateException("Connection already closed");
	}
}
