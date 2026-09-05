package com.island.sftp

import java.net.ConnectException
import java.io.File
import java.util.Properties
import android.util.Log
import android.content.Context
import com.jcraft.jsch.UserInfo
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.ProxySOCKS5
import com.island.androidsftpdocumentsprovider.account.TheDatabase
import com.island.androidsftpdocumentsprovider.account.Account

/**
 * Ssh connection
 */
abstract class SSH(val context: Context,
		   val account: Account,
		   val userInfo: UserInfo?) {
    var jsch: JSch
    var session: Session?=null

    val TAG = "SSH"
    val TIMEOUT = 20000

    init {
        Log.d(TAG, String.format("Creating new connection for %s",
				 account.hostName))
        BouncyCastle.trigger()
        val privKey = Keygen.readPrivateKey(context)
        jsch=JSch()
        //jsch.setLogger(new Logger())
        try {
	    var dir = context.filesDir
	    jsch.setKnownHosts(File(dir,"known_hosts").toString())
            if(privKey != null)
                jsch.addIdentity(privKey)
            if(userInfo != null)
                makeSession()
        } catch(e: JSchException) {
            Log.e(TAG, "JschException during init: "+e, e)
            val exception = ConnectException("Can't connect to $account.name")
            exception.initCause(e)
            throw exception
        }
    }

    @Throws(JSchException::class)
    protected open fun makeSession() : Session {
	val session=jsch.getSession(account.userName,
				    account.hostName,
				    account.port)
	val config=Properties()
	if(userInfo != null)
	    config.put("StrictHostKeyChecking","ask")
	else
	    config.put("StrictHostKeyChecking","yes")

	session.setConfig(config)
	if(userInfo != null)
        session.userInfo = userInfo

	val socksProxy = account.socksProxy
	val jumpHostId = account.jumpHostId
	if(!socksProxy.isEmpty()) {
	    var socksPort : Int
	    lateinit var socksHost : String
	    val idx = socksProxy.lastIndexOf(':')
	    if(idx == -1) {
		socksHost=socksProxy
		socksPort = 1080
	    } else {
		socksHost = socksProxy.substring(0,idx)
		socksPort = Integer.parseInt(socksProxy.substring(idx+1))
	    }
	    val proxy = ProxySOCKS5(socksHost, socksPort)
	    session.setProxy(proxy)
	} else if(jumpHostId != null) {
	    val dao = TheDatabase.getDao(context)
	    val jumpAccount = dao.readAccountById(jumpHostId)
	    val proxy = ProxyJumpHost(context, jumpAccount, userInfo)
	    session.setProxy(proxy)
	}

	val password = account.password
	if(password != null && !password.isEmpty())
	    session.setPassword(password.toByteArray())

	session.setTimeout(TIMEOUT)
	session.connect()
	this.session = session
	return session
    }

    @Throws(JSchException::class)
    fun getSshSession() : Session {
	return session ?: makeSession()
    }
}
