package com.island.sftp

import android.util.Log
/**
 * Bogus userinfo class that always says yes to set up known_hosts file,
 * yes to new host keys, and no to changed host keys
 * Later versions will try to actually prompt the user
 */
class UserInfo : com.jcraft.jsch.UserInfo {
    val TAG = "UserInfo"

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
        Log.i(TAG, message)
        if(message.indexOf("The authenticity of host") == 0)
            // New hosts
            return true
        if(message.indexOf("Are you sure you want to create it?") != -1)
            // Asking for confirmation to create known_hosts if it
            // doesn't exist yet
            return true

        // all other cases: host key has changed => say no
        return false
    }

    override fun showMessage(message: String) {
        Log.i(TAG, message);
    }
}
