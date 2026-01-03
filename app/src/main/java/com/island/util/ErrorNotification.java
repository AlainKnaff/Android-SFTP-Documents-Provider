package com.island.util;

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
 Copyright (C) 2025,2026 Alain Knaff

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

import lu.knaff.alain.saf_sftp.R;
import android.os.Build;
import android.app.NotificationChannel;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

public class ErrorNotification {
    private static boolean isInitialized=false;
    private static String channelName="ErrorChannel";

    private static synchronized void createChannel(Context ctx) {
	if(isInitialized)
	    return;
	if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
	    int importance=NotificationManager.IMPORTANCE_HIGH;
	    NotificationChannel channel=new NotificationChannel(channelName,
								channelName,
								importance);
	    NotificationManager notificationManager=
		ctx.getSystemService(NotificationManager.class);
	    notificationManager.createNotificationChannel(channel);
	    isInitialized=true;
	}
    }

    public static void sendNotification(Context ctx,
					String title, Throwable t) {
	if(Build.VERSION.SDK_INT < 26)
	    return;
	while(true) {
	    Throwable c = t.getCause();
	    if(c == null)
		break;
	    t=c;
	}

	String message = t.getMessage();
	if(message==null)
	    message=t.toString();

	createChannel(ctx);
	Notification.Builder notificationBuilder =
	    new Notification.Builder(ctx, channelName)
	    .setContentTitle(title)
	    .setContentText(message)
	    .setSmallIcon(R.drawable.ic_stat_name)
	    .setStyle(new Notification.BigTextStyle()
		      .bigText(message))
	    .setAutoCancel(true)
	    ;

	NotificationManager notificationManager = (NotificationManager)
	    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
	notificationManager.notify(0, notificationBuilder.build());
    }
}
