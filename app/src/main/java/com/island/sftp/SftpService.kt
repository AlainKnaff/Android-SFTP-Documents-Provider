package com.island.sftp

// from https://www.geeksforgeeks.org/android/services-in-android-with-example/

import android.util.Log
import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Service needed to keep Jssh socket alive for longer.
 * Without this (and without setting battery optimization to
 * unrestricted), sockets are closed after 10 seconds of no use
 *
 * With this service, socket survives as long as service is alive (~1
 * minutes) plus those 10 seconds
 *
 * As the goal is to not have the ssh session close on us while we are
 * navigating, this small improvement is enough: 10 seconds is a bit
 * short to pick a file or subdirectory in a large listing, but 1 minute
 * should be enough.
 *
 * So we don't need to bother with foreground service notifications
 */

class SftpService : Service() {
    val TAG = "Service"

    // execution of service will start
    // on calling this method
    //
    // on Huawei, this may be started with a null intent...
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service (re)started")

        // returns the status
        // of the program
        return START_STICKY
    }

    // execution of the service will
    // stop on calling this method
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.e(TAG, "onBind, should not happen")
        return null
    }
}
