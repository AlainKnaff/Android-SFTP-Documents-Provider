package com.island.androidsftpdocumentsprovider.provider

import com.island.androidsftpdocumentsprovider.R
import com.island.sftp.SFTP
import java.io.FileInputStream
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.ExecutionException

import android.os.Build
import android.util.Log
import android.net.Uri
import android.content.Intent
import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.OneTimeWorkRequest
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.Worker
import androidx.work.WorkManager
import androidx.work.ForegroundInfo
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

import android.content.pm.ServiceInfo

class UploadWorker(context: Context, parameters: WorkerParameters) :
    Worker(context, parameters)
{
    /* Example at:
       dependencies:
       https://developer.android.com/jetpack/androidx/releases/work

       notification for longer lived worker (except the part about service
       type):
       https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
     */

    private val notificationManager =
	context.getSystemService(Context.NOTIFICATION_SERVICE) as
        NotificationManager

    private val packageName = context.packageName

    private var lastNamePart: String? = null

    private var foregroundFuture: Future<Void>? = null

    override fun doWork(): Result {
	val cacheFileName = inputData.getString(KEY_CACHE_FILE_NAME)
	    ?: return Result.failure()
	val remoteUriString = inputData.getString(KEY_REMOTE_URL)
	    ?: return Result.failure()
	val remoteUri = Uri.parse(remoteUriString)

	lastNamePart = remoteUri.lastPathSegment

	// Mark the Worker as important
	setForeground(0)

	upload(cacheFileName, remoteUri)

	// Inform broadcast receiver that we are done
	val intentBroadcast = Intent(SFTPProvider.SFTP_UPLOAD_POST)
	intentBroadcast.setPackage(packageName)
	intentBroadcast.putExtra("uri", remoteUriString.toString())
	applicationContext.sendBroadcast(intentBroadcast)

	// wait for foreground future to terminate, if any, before
	// returning result
	val f = foregroundFuture
	if(f != null /* && !f.isDone() */ )  {
	    try {
		f.get()
		Log.d(TAG, "foregroundFuture terminated")
	    } catch(e: ExecutionException) {
		Log.e(TAG, "Exception "+e+" while waiting for foregroundFuture to terminate")
	    }
	}

	return Result.success()
    }

    var total = 0L
    var lastPercent = 0

    // id to distinguish several ongoing notifications amongst each other?
    val notification_id = GetNotificationId()

    private fun upload(cacheFileName: String,  uri: Uri) {
	val ctx: Context = applicationContext
	val cacheDir = ctx.cacheDir
	val cache = File(cacheDir, cacheFileName)
	lastPercent = 0
	SFTP(ctx, uri, SFTPProvider.getToken(ctx,uri)).use {
	    val cache= File(cacheDir,SFTP.getFile(uri).getName())
	    this@UploadWorker.total = cache.length()
	    Log.d(SFTPProvider.TAG,
		  "UploadWorker doInBackground Transfer "+
		      cache.getAbsolutePath() +" to " +
		      uri)
	    SFTP.writeAll(FileInputStream(cache),
			  it.write(File(uri.getPath()))) {
		o, w -> update(w as Long)
	    }
	}
    }

    fun update(wrote: Long) {
	var percent:Int = ((wrote * 100L) / total).toInt()
	val f = foregroundFuture
	if(percent != lastPercent && (f == null || f.isDone()) ) {
	    lastPercent = percent
	    if(f != null) {
		try {
		    f.get()
		} catch(e: ExecutionException) {
		    // ocasionally, on first launch after an upgrade, worker
		    // service cannot be put into foreground.
		    // if that happens, so be it, just avoid updating progress
		    // to avoid flooding the log
		    Log.e(TAG, "Exception: "+e.message+" while setting to "+
				   percent)
		    return
		}
	    }
	    setForeground(percent)
	    /*
	    try {
		Thread.sleep(100);
	    } catch(e: InterruptedException) {
	    }
	    */
	}
    }

    // Creates an instance of ForegroundInfo which can be used to update the
    // ongoing notification.
    private fun setForeground(progress: Int) {
	val id = applicationContext.getString(R.string.notification_channel_id)
	val title = applicationContext.getString(R.string.notification_title,
						 lastNamePart)
	val cancel = applicationContext.getString(R.string.cancel_upload)
	// This PendingIntent can be used to cancel the worker
	val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(getId())

	// Create a Notification channel if necessary
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(id)
	}

	val notification = NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
	    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
	    .setOngoing(true)
            .setTicker(title)
            .setContentText(getNotificationDescription(progress))
            .setSmallIcon(R.drawable.ic_stat_name)
	    .setPriority(NotificationCompat.PRIORITY_LOW)
	    .setProgress(100,progress,false)
        // Add the cancel action to the notification which can
        // be used to cancel the worker
            .addAction(android.R.drawable.ic_delete, cancel, intent)
	    .build()

	val fg = if(Build.VERSION.SDK_INT>=29)
	    ForegroundInfo(notification_id, notification,
			   ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
	else
	    ForegroundInfo(notification_id, notification)
	foregroundFuture=setForegroundAsync(fg)
    }

    private fun getNotificationDescription(progress: Int): String {
	return String.format(applicationContext.getString(R.string.notification_description),progress);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(id: String) {
	// Create a Notification channel
	val channel = NotificationChannel(id,
					  applicationContext
					      .getString(R.string.foreground),
					  NotificationManager.IMPORTANCE_LOW)
	channel.setDescription("SFTP upload channel for foreground service notification")

	notificationManager.createNotificationChannel(channel)
    }

    companion object {
	const val KEY_CACHE_FILE_NAME = "KEY_CACHE_FILE_NAME"
	const val KEY_REMOTE_URL = "KEY_REMOTE_URL"
	const val TAG = "UploadWorker"
	var notificationId = 1
	
	@JvmStatic
	public fun Upload(context: Context,
			  cacheFile: File, documentId: Uri ): Unit {
	    val data = Data.Builder()
	    data.putString(UploadWorker.KEY_CACHE_FILE_NAME,
			   cacheFile.getPath());
	    data.putString(UploadWorker.KEY_REMOTE_URL,
			   documentId.toString());

	    val workManager = WorkManager.getInstance(context)
	    workManager.enqueue(OneTimeWorkRequest
				    .Builder(UploadWorker::class.java)
				    .setInputData(data.build())
				    .build())

	    // Examples used:
	    // https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work

	    // "Define input data":
	    // https://stackoverflow.com/questions/52639001/how-to-create-a-worker-with-parameters-for-in-workmanager-for-android
	}

	fun GetNotificationId(): Int {
	    synchronized(this) {
		return notificationId++;
	    }
	}
    }
}
