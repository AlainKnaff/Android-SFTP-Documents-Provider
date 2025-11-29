package com.island.util;

import com.island.androidsftpdocumentsprovider.R;
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
	    int importance=NotificationManager.IMPORTANCE_DEFAULT;
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
