package com.island.sftp

import java.net.ConnectException
import java.io.File
import java.util.Properties
import java.util.concurrent.CompletableFuture
import android.util.Log
import android.content.Context
import com.jcraft.jsch.UserInfo
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.ProxySOCKS5
import com.island.androidsftpdocumentsprovider.provider.Unlocked
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
    final val TIMEOUT = 20000

    init {
        Log.d(TAG, String.format("Creating new connection for %s",
				 account.hostName))
        BouncyCastle.trigger()
        val privKey = Keygen.readPrivateKey(context)
        jsch=JSch()
        //jsch.setLogger(new Logger())
        try {
	    var dir = context.getFilesDir()
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
    open protected fun makeSession() : Session {
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
	    session.setUserInfo(userInfo)

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
	    session.setPassword(password)

	session.setTimeout(TIMEOUT)
	if(userInfo != null)
	    session.connect()
	else
	    retrySessionConnect(session)
	this.session = session
	return session
    }

    @Throws(JSchException::class)
    open protected fun retrySessionConnect(session: Session) {
	lateinit var wfu : CompletableFuture<Boolean>
	for(i in 0..15) {
	    if(i==1)
		wfu = Unlocked.getWfu(context)
	    else if(i == 2)
		Unlocked.waitForUnlock(wfu, 10)
	    else if(i > 2) {
		Unlocked.waitForUnlock(wfu, 10000)
		try {
		    Thread.sleep(10)
		} catch(e: Exception) {
		}
	    }
	    try {
		session.connect()
		Log.d(TAG, toString() + " session connected")
		break
	    } catch(e: JSchException) {
		if(i == 14) {
		    Log.e(TAG, "Giving up "+e)
		    throw e
		}
	    }
	}
    }

    @Throws(JSchException::class)
    fun getSshSession() : Session {
	return session ?: makeSession()
    }
}
