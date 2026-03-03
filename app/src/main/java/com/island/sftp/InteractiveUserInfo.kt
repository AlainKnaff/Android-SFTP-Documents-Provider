package com.island.sftp

import java.util.concurrent.CompletableFuture
import android.app.AlertDialog
import android.app.Activity
import lu.knaff.alain.saf_sftp.R

/**
 * Bogus userinfo class that always says yes to set up known_hosts file,
 * yes to new host keys, and no to changed host keys
 * Later versions will try to actually prompt the user
 */
class InteractiveUserInfo(val activity: Activity) : com.jcraft.jsch.UserInfo {
    val TAG = "InteractiveUserInfo"

    override fun getPassphrase() : String {
        throw UnsupportedOperationException()
    }

    override fun getPassword() : String {
        throw UnsupportedOperationException()
    }

    override fun promptPassphrase(message: String) : Boolean {
        return false
    }

    override fun promptPassword(message: String) : Boolean {
        return false
    }

    override fun promptYesNo(message: String) : Boolean {
        val f = CompletableFuture<Boolean>()
        activity.runOnUiThread {
	    AlertDialog
	        .Builder(activity)
                .setMessage(message)
                .setPositiveButton(R.string.yes) { d, w -> d.dismiss()
                                                           f.complete(true)
                }
                .setNegativeButton(R.string.no) { d, w -> d.dismiss()
                                                          f.complete(false)
                }
                .setCancelable(false)
	        .show()
        }
        return f.get()
    }

    override fun showMessage(message: String) {
        val f = CompletableFuture<Boolean>()
        activity.runOnUiThread {
	    AlertDialog
	        .Builder(activity)
                .setMessage(message)
                .setPositiveButton(R.string.ok) { d, w -> d.dismiss()
                                                           f.complete(true)
                }
                .setCancelable(false)
	        .show()
        }
        f.get()
    }
}
