package com.island.androidsftpdocumentsprovider.provider;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.util.*;
import com.island.sftp.*;
import java.io.*;
import java.lang.ref.*;
import java.util.*;
public class AsyncCopy extends AsyncTask<File,Long,Void>implements Observer
{
	private final Uri uri;
	private final WeakReference<Context>context;
	private final File cacheDir;
	private File processing;
	private long total;
	private long step;
	AsyncCopy(Context context,Uri uri)
	{
		Objects.requireNonNull(context);
		Objects.requireNonNull(uri);
		this.uri=uri;
		this.context=new WeakReference<Context>(context);
		cacheDir=context.getCacheDir();
	}
	@Override
	protected Void doInBackground(File[]files)
	{
		Log.i(SFTPProvider.TAG,String.format("AsyncCopy doInBackground %s",Arrays.toString(files)));
		Objects.requireNonNull(files);
		try
		{
			try(SFTP sftp=new SFTP(uri,SFTPProvider.getToken(getContext(),uri)))
			{
				total=0;
				for(File file:files)
				{
					total+=new File(cacheDir,file.getName()).length();
				}
				step=0;
				for(File file:files)
				{
					File cache=new File(cacheDir,SFTP.getFile(uri).getName());
					Log.d(SFTPProvider.TAG, String.format("AsyncCopy doInBackground Transfer %s to %s", cache.getAbsolutePath(), file.getAbsolutePath()));
					processing=file;
					sftp.writeAll(new FileInputStream(cache),sftp.write(file),this);

				}
			}
		}
		catch(IOException e)
		{
			Log.e(SFTPProvider.TAG,"Can't asynchronously copy",e);
		}
		return null;
	}
	@Override
	public void update(Observable observable,Object value)
	{
		Objects.requireNonNull(value);
		long wrote=(Long)value;
		long update=total/100;
		if(update==0)update=1;
		if(wrote>step)
		{
			publishProgress(wrote);
			while(wrote>step)step+=update;
		}
	}
	@Override
	protected void onPostExecute(Void result)
	{
		Log.i(SFTPProvider.TAG,String.format("AsyncCopy onPostExecute %s %s",uri.toString(), result));
		Context context=getContext();
		NotificationManager notificationManager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancelAll();
		Intent intent=new Intent(context,UploaderService.class);
		context.stopService(intent);
		Intent intentBroadcast = new Intent(SFTPProvider.SFTP_UPLOAD_POST);
		intentBroadcast.setPackage(context.getPackageName());
		intentBroadcast.putExtra("uri", uri.toString());
		context.sendBroadcast(intentBroadcast);
	}
	@Override
	protected void onProgressUpdate(Long[]values)
	{
		Log.i(SFTPProvider.TAG,String.format("AsyncCopy onProgressUpdate %s %s",uri.toString(),Arrays.toString(values)));
		Objects.requireNonNull(values);
		if(values.length==0)throw new IllegalArgumentException("values length is 0");
		Context context=getContext();
		int progress=(int)(values[0]*100/total);
		NotificationManager notificationManager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notification=UploaderService.getNotification(context,processing.getName(),progress);
		notificationManager.notify(UploaderService.ONGOING_NOTIFICATION_ID,notification);

	}
	private Context getContext()
	{
		Context context=this.context.get();
		if(context==null)throw new NullPointerException("Context instance already disposed");
		return context;
	}
}
