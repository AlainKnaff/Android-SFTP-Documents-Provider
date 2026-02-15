package com.island.androidsftpdocumentsprovider.provider;

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
        Log.d(TAG, "Cleaning old cache")
        val cacheDir = context.getCacheDir();
        for(subdir in cacheDir.listFiles()) {
            if(subdir.name.length < 44) {
                Log.d(TAG, "Skipping "+subdir.name)
                continue;
            }
            for(file in subdir.listFiles()) {
                Log.i(TAG, "Removing file "+file)
                file.delete()
            }
            subdir.delete()
            Log.i(TAG, "Removing subdirectory "+subdir)
        }
    }
}
