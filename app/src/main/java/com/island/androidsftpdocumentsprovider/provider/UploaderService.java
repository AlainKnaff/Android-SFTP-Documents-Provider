package com.island.androidsftpdocumentsprovider.provider;

import java.io.File;
import java.util.Objects;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.content.Intent;
import android.os.Looper;
import android.os.Handler;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.island.androidsftpdocumentsprovider.R;
import com.island.sftp.SFTP;

public class UploaderService extends Service
{
	public static final String FOREGROUND_CHANNEL_ID="foreground";

	public static String TAG = "UploaderService";

	public static final int ONGOING_NOTIFICATION_ID=1;

	private NotificationManager notificationManager;

	private Handler uiHandler;

	@Override
	public void onCreate() {
		Log.d(SFTPProvider.TAG,"onCreate "+this);

		// Hander for runOnUiThread
		Looper looper = getMainLooper();
		uiHandler = new Handler(looper);

		// Notification manager
		notificationManager=getSystemService(NotificationManager.class);
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
			CharSequence name=getString(R.string.foreground);
			int importance=NotificationManager.IMPORTANCE_LOW;
			NotificationChannel channel=new NotificationChannel(FOREGROUND_CHANNEL_ID,name,importance);
			notificationManager.createNotificationChannel(channel);
		}
	}
	@Override
	public int onStartCommand(Intent intent,int flags,int startId)
	{
		Log.d(SFTPProvider.TAG,
		      "onStartCommand "+intent+" "+flags+" "+this+" "+startId);
		Objects.requireNonNull(intent);
		Objects.requireNonNull(intent.getData());
		File file=SFTP.getFile(intent.getData());
		boolean inForeground;
		int workerId = intent.getIntExtra(UploadWorker.WORKER_ID,0);
		try {
			var notif = getNotification(file.getName(), 0);
			if(Build.VERSION.SDK_INT>=29) {
				startForeground(ONGOING_NOTIFICATION_ID,
						notif,
						ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
			} else {
				startForeground(ONGOING_NOTIFICATION_ID,
						notif);
			}
			inForeground=true;
		} catch(Exception e) {
		    Log.i(TAG, "Exception "+e+" while trying to foreground");
		    inForeground=false; // could not be
					// foregrounded. Might get
					// interrupted after a few
					// seconds, but hopefully most files
		    			// are small enough
		}
		UploadWorker.NotifyForegrounded(startId,
						workerId,
						this, inForeground);
		return START_REDELIVER_INTENT;
	}
	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}
	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy: "+this);
	}

	/**
	 * Convenience method for running stuff on Ui thread.
	 * Why is this not part of Android?
	 */
	public void runOnUiThread(Runnable r) {
		if(getMainLooper().isCurrentThread()) {
			r.run();
		} else {
			uiHandler.post(r);
		}
	}

	Notification getNotification(String title,int progress)
	{
		Objects.requireNonNull(title);
		Notification.Builder builder;
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
			builder=new Notification.Builder(this,FOREGROUND_CHANNEL_ID);
		else
			builder=new Notification.Builder(this);
		builder.setContentTitle(title);
		builder.setVisibility(Notification.VISIBILITY_PUBLIC);
		builder.setOngoing(progress < 100);
		builder.setContentText(getNotificationDescription(progress));
		builder.setSmallIcon(R.drawable.ic_stat_name);
		builder.setPriority(Notification.PRIORITY_LOW);
		builder.setProgress(100,progress,false);
		return builder.build();
	}
	private String getNotificationDescription(long progress)
	{
		return String.format(getString(R.string.notification_description),progress);
	}

	void updateNotification(String title, int progress) {
		Notification notification=getNotification(title, progress);
		notificationManager.notify(ONGOING_NOTIFICATION_ID,
					   notification);
	}
}
