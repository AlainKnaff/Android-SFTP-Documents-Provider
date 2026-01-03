package com.island.androidsftpdocumentsprovider.provider;

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
 Copyright (C) 2025,2026 Alain Knaff
 Copyright (C) 2020      Riccardo Isola

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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

import androidx.core.app.NotificationCompat;

import lu.knaff.alain.saf_sftp.R;
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

	Notification getNotification(String title,int progress)	{
		Objects.requireNonNull(title);
		NotificationCompat.Builder builder;
		builder=new NotificationCompat.Builder(this,FOREGROUND_CHANNEL_ID);
		builder.setContentTitle(title);
		builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
		builder.setOngoing(progress < 100);
		builder.setContentText(getNotificationDescription(progress));
		builder.setSmallIcon(R.drawable.ic_stat_name);
		builder.setPriority(NotificationCompat.PRIORITY_LOW);
		builder.setProgress(100,progress,false);
		return builder.build();
	}

	private void setNotificationPrioriy(NotificationCompat.Builder builder) {
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
