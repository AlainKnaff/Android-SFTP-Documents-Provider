package com.island.util

import lu.knaff.alain.saf_sftp.R
import android.app.AlertDialog
import android.app.Activity

/**
 * Dialog used to display errors. To be used when an activity is present
 * when no activity is present, use ErrorNotification instead
 */
class ErrorDialog {
    companion object {
        @JvmStatic
        fun showError(activity: Activity, title: String, tt: Throwable) {
            var t = tt
            while(true) {
                val c = t.cause
                if(c == null || c is android.system.ErrnoException)
	            break
                t=c
            }

            activity.runOnUiThread {
	        AlertDialog
                    .Builder(activity)
                    .setTitle(title)
                    .setMessage(t.toString())
                    .setPositiveButton(R.string.ok) { d, w -> d.dismiss() }
                    .show()
            }
        }
    }
}
