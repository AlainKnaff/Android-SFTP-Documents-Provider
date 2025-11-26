package com.island.androidsftpdocumentsprovider.provider;

import java.io.File;
import java.util.Objects;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.island.androidsftpdocumentsprovider.R;
import com.island.sftp.SFTP;

public class UploaderService extends Service
{
	public static final String FOREGROUND_CHANNEL_ID="foreground";
	public static final int ONGOING_NOTIFICATION_ID=1;
	@Override
	public int onStartCommand(Intent intent,int flags,int startId)
	{
		Log.i(SFTPProvider.TAG,String.format("UploaderService onStartCommand %s %s %s",intent,flags,startId));
		Objects.requireNonNull(intent);
		Objects.requireNonNull(intent.getData());
		File file=SFTP.getFile(intent.getData());
		startForeground(ONGOING_NOTIFICATION_ID,getNotification(this,file.getName(),0));
		AsyncCopy copy=new AsyncCopy(this,intent.getData());
		copy.execute(file);
		return START_REDELIVER_INTENT;
	}
	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}
	static Notification getNotification(Context context,String title,int progress)
	{
		Objects.requireNonNull(context);
		Objects.requireNonNull(title);
		Notification.Builder builder;
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
		{
			CharSequence name=context.getString(R.string.foreground);
			int importance=NotificationManager.IMPORTANCE_LOW;
			NotificationChannel channel=new NotificationChannel(FOREGROUND_CHANNEL_ID,name,importance);
			NotificationManager notificationManager=context.getSystemService(NotificationManager.class);
			notificationManager.createNotificationChannel(channel);
			builder=new Notification.Builder(context,FOREGROUND_CHANNEL_ID);
		}
		else builder=new Notification.Builder(context);
		builder.setContentTitle(title);
		builder.setVisibility(Notification.VISIBILITY_PUBLIC);
		builder.setOngoing(true);
		builder.setContentText(getNotificationDescription(context,progress));
		builder.setSmallIcon(R.drawable.ic_stat_name);
		builder.setPriority(Notification.PRIORITY_LOW);
		builder.setProgress(100,progress,false);
		return builder.build();
	}
	private static String getNotificationDescription(Context context,long progress)
	{
		assert context!=null;
		return String.format(context.getString(R.string.notification_description),progress);
	}
}
