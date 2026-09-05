package com.island.androidsftpdocumentsprovider.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Purge old pre-0.2.12 cache directory
 */
class CleanOldCache : BroadcastReceiver() {
    val TAG = "CleanOldCache"

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.e(TAG, "Ignoring action ${intent?.action}")
            return
        }

        Log.d(TAG, "Cleaning old cache")
        val cacheDir = context.cacheDir
        if(cacheDir == null) {
            Log.e(TAG, "Cachedir is null")
            return
        }
        val subdirs = cacheDir.listFiles()
        if(subdirs == null) {
            Log.e(TAG, "No cache subdirs")
            return
        }
        for(subdir in subdirs) {
            if(subdir.name.length < 44) {
                Log.d(TAG, "Skipping "+subdir.name)
                continue
            }
            val files = subdir.listFiles()
            if(files != null)
                for(file in files) {
                    Log.i(TAG, "Removing file "+file)
                    file.delete()
                }
            subdir.delete()
            Log.i(TAG, "Removing subdirectory "+subdir)
        }
    }
}
