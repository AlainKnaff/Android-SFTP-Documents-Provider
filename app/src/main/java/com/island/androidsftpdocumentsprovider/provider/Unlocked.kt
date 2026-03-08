package com.island.androidsftpdocumentsprovider.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import com.island.sftp.SftpService;

/**
 * Get notification when mobile is unlocked
 */
class Unlocked : BroadcastReceiver() {
    companion object {
        var activated = false
        val TAG = "WaitForUnlock"

        var waitForUnlockFuture: CompletableFuture<Boolean>? = null
        val instance = Unlocked()

        private fun activate(context: Context) {
            if(activated)
                return

            try {
                ContextCompat
                    .registerReceiver(context, instance,
                                               IntentFilter(Intent.ACTION_USER_PRESENT),
                                               ContextCompat.RECEIVER_EXPORTED);
                activated = true
            } catch(e: Exception) {
                Log.i(TAG, "Could not register Unlocked receiver: "+e);
            }
        }

        @JvmStatic
        fun waitForUnlock(wfu: CompletableFuture<Boolean>, milliseconds: Long) {
            try {
                wfu.get(milliseconds, TimeUnit.MILLISECONDS);
            } catch(exe: ExecutionException) {
                Log.i(TAG, "Execution exception while waiting for unlock", exe);
            } catch(ie: InterruptedException) {
                Log.i(TAG, "Interrupted while waiting for unlock", ie);
            } catch(te: TimeoutException) {
                Log.i(TAG, "Timeout while waiting for unlock "+ te);
            }
        }

        @JvmStatic
        fun getWfu(context: Context) : CompletableFuture<Boolean> {
            activate(context)
            synchronized(instance) {
                var wfu = waitForUnlockFuture
                if(wfu == null) {
                    wfu = CompletableFuture()
                    waitForUnlockFuture = wfu
                }
                return wfu
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_USER_PRESENT) {
            Log.e(TAG, "Ignoring action ${intent?.action}")
            return
        }
        var waiting = false
        synchronized(this) {
            var wfu = waitForUnlockFuture
            if(wfu != null) {
                wfu.complete(true);
                waiting=true
                Log.i(TAG, "Mobile unlocked");
            }
            waitForUnlockFuture = null;
        }
        if(waiting)
            SftpService.start(context)
    }
}
