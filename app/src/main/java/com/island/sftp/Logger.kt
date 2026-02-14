package com.island.sftp;

import android.util.Log;

import com.jcraft.jsch.Logger.DEBUG
import com.jcraft.jsch.Logger.INFO
import com.jcraft.jsch.Logger.WARN
import com.jcraft.jsch.Logger.ERROR
import com.jcraft.jsch.Logger.FATAL

class Logger : com.jcraft.jsch.Logger {
    val TAG = "Jssh"

    override fun isEnabled(level: Int): Boolean {
        return true;
    }

    override fun log(level: Int, message: String) {
        when(level) {
            DEBUG -> Log.d(TAG, message)
            INFO -> Log.i(TAG, message)
            WARN -> Log.w(TAG, message)
            ERROR -> Log.e(TAG, message)
            FATAL -> Log.wtf(TAG, message)
            else -> Log.i(TAG, message)
        }
    }
}
