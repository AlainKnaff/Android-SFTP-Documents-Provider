package com.island.androidsftpdocumentsprovider.provider

import com.island.sftp.SFTP
import java.io.FileInputStream
import java.io.File
import java.util.concurrent.Executors

import android.util.Log
import android.net.Uri
import android.content.Intent
import android.content.Context
import android.app.Service;

class UploadWorker(val id: Int,
		   val cacheFile: File,
		   val documentUri: Uri) : Object()
{
    private var inForeground: Boolean = false
    private var startId: Int = 0

    fun start() {
	val service: UploaderService? = UploadWorker.service
	if(service == null)
	    throw NullPointerException("Service is null!")
	executorService.execute() {
	    try {
		upload(service, cacheFile, documentUri)

		// signal SFTPProvider that upload is done
		val intentBroadcast = Intent(SFTPProvider.SFTP_UPLOAD_POST)
		intentBroadcast.setPackage(service.packageName)
		intentBroadcast.putExtra("uri", documentUri.toString())
		service.sendOrderedBroadcast(intentBroadcast,null)

	    } catch(e: Exception) {
		Log.e(TAG, "Exception during async upload", e)
	    } finally {
		stopping=true
		StopIfLast(service, startId)
	    }
	}
    }

    var total = 0L

    private fun upload(service: UploaderService,
		       cache: File, uri: Uri) {
	val lastNamePart = documentUri.lastPathSegment
	lastPercent = 0
	SFTP(service, uri, SFTPProvider.getToken(service,uri), 0).use {
	    total = cache.length()
	    Log.d(SFTPProvider.TAG,
		  "UploadWorker doInBackground Transfer "+
		      cache.getAbsolutePath() +" to " +
		      uri)
	    SFTP.writeAll(FileInputStream(cache),
			  it.write(SFTP.getFile(uri))) {
		w -> update(service, lastNamePart, w as Long)
	    }
	}
    }

    /**
     * Percentage actually uploaded
     */
    private var percent = 0

    /**
     * Percentage currently displayed
     */
    private var lastPercent = 0

    /**
     * is this worker done? If so, no further update will be done in
     * order to prevent it accidentally slipping after the
     * stopForeground call
     */
    private var stopping = false

    /**
     * Has a progress update already been scheduled, but not yet
     * performed? If so, no point in scheduling another
     */
    private var messageWaiting = false

    /**
     * Schedule a progress update if needed. I.e. if:
     * 1. percentage is now different than displayed
     * 2. there is not yet an update scheduled
     */
    fun update(service: UploaderService, lastNamePart: String?, wrote: Long) {
	percent = ((wrote * 100L) / total).toInt()
	if(percent != lastPercent && !messageWaiting) {
	    messageWaiting = true
	    lastPercent = percent
	    service.runOnUiThread() {
		if(!stopping) {
		    // Log.d(TAG, "Updating "+percent)
		    service?.updateNotification(lastNamePart, lastPercent)
		}
		messageWaiting = false
		lastPercent = percent
	    }

	    // Test code for debugging progress display
	    /*
	    try {
		Thread.sleep(200);
	    } catch(e: InterruptedException) {
	    }
	    */
	}
    }

    /**
     * Is object set up, i.e. has "foreground" call been attempted?
     */
    private var setupDone = false

    /**
     * Wait for this upload object to be setup
     */
    fun waitForSetup() {
	Log.d(TAG, "Waiting for setup done");
	synchronized(this) {
	    while(!setupDone)
		this.wait()
	}
	Log.d(TAG, "Setup done: " + id + " => " + startId);
    }

    companion object {
	const val KEY_CACHE_FILE_NAME = "KEY_CACHE_FILE_NAME"
	const val KEY_REMOTE_URL = "KEY_REMOTE_URL"
	const val TAG = "UploadWorker"
	var Id = 1
	var lastUpload:Long? =null

	val map = HashMap<Int,UploadWorker>()

	var executorService = Executors.newFixedThreadPool(1);

	private var service: UploaderService? = null

	const val WORKER_ID = "workerId"

	@JvmStatic
	public fun Prepare(context: Context,
			   cacheFile: File, documentId: Uri ): UploadWorker {
	    val now: Long = System.currentTimeMillis()
	    lastUpload = now

	    val id : Int = GetId()
	    // make a worker
	    val ret = UploadWorker(id, cacheFile, documentId)
	    synchronized(map) {
		map.put(id,ret)
	    }

	    // start service
	    val intent = Intent(context,UploaderService::class.java)
	    intent.putExtra(WORKER_ID,id)
	    intent.setData(documentId)
	    context.startService(intent)

	    return ret
	}

	/**
	 * Uploads still running
	 * Count is incremented when an upload is created, and
	 * decremented when done or failed. This count is needed in
	 * addition to startId, because startId does not protect
	 * against uploads becoming scheduled out of order.
	 */
	var cnt=0

	/**
	 * Largest start id stopped. Useful for race-condition free
	 * stopping of service. (This is used to protect against
	 * situation where service is started again for a new worker
	 * before current stop request could be service, in which case
	 * new service instance would be stopped immediately.
	 */
	var maxStartId=0

	/**
	 * Notify that service for this upload worker has been set up
	 * (i.e. foregrounding attempted, even if not successful)
	 */
	@JvmStatic
	public fun NotifyForegrounded(startId: Int, id: Int,
				      service: UploaderService,
				      inForegeround: Boolean) {
	    var ret : UploadWorker? = null;
	    synchronized(map) {
		ret = map.get(id)
		if(ret == null)
		    throw NoSuchElementException("UploadWorker "+id+" not found")
		map.remove(id)
	    }
	    if(ret != null) {
		synchronized(this) {
		    if(cnt == 0) {
			// service should be null
			if(this.service != null)
			    Log.e(TAG, "Service should be null before first worker")
		    } else {
			if(this.service != service)
			    Log.e(TAG, "Service should not change until stopped")
		    }

		    this.service = service
		    cnt++
		}
		synchronized(ret) {
		    ret.startId = startId
		    ret.setupDone = true
		    ret.inForeground = inForegeround
		    ret.notify()
		}
	    }
	}

	/**
	 * Stop service if this was the last worker
	 */
	private fun StopIfLast(service: UploaderService, startId: Int) {
	    //
	    synchronized(this) {
		cnt--;
		if(startId > maxStartId)
		    maxStartId = startId
		if(cnt == 0) {
		    val ms = maxStartId
		    UploadWorker.service = null
		    Log.i(TAG, "All uploads completed, stopping service "+
				   service+" "+startId+" "+ms)
		    // this was the last worker. Stopping the service
		    service.runOnUiThread() {
			if(cnt == 0) {
			    service.stopSelfResult(ms)
			}
		    }
		}
	    }
	}

	fun GetId(): Int {
	    synchronized(this) {
		Log.d(TAG, "Assigning id="+Id)
		return Id++;
	    }
	}
    }
}
