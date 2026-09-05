package com.island.sftp

import java.io.OutputStream
import java.io.InputStream

import java.io.PipedInputStream
import java.io.PipedOutputStream

import java.net.Socket
import android.util.Log
import android.content.Context
import com.island.androidsftpdocumentsprovider.account.Account

import com.jcraft.jsch.UserInfo
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.ChannelDirectTCPIP

class ProxyJumpHost(context: Context,
		    account: Account,
		    userInfo: UserInfo?) :
    SSH(context, account, userInfo), Proxy {

    lateinit var channel : ChannelDirectTCPIP

    lateinit var outputStream : PipedOutputStream  // to server
    lateinit var inputStream : PipedInputStream // from server

    override fun connect(socketFactory: SocketFactory?,
			 host: String,
			 port: Int,
			 timeout: Int) {
	Log.i(TAG, "Connect to "+host+":"+port+" via Jump host")
	val sess = getSshSession()
	channel = sess.openChannel("direct-tcpip") as ChannelDirectTCPIP
	channel.setHost(host)
	channel.setPort(port)
	channel.setOrgIPAddress("0.0.0.0") // bind to default appropriate for target

	outputStream = PipedOutputStream()
	val toServer = PipedInputStream(outputStream)

	val fromServer = PipedOutputStream()
	inputStream = PipedInputStream(fromServer)

	channel.inputStream = toServer
	channel.outputStream = fromServer

	channel.connect(timeout)
    }

    override fun getInputStream(): InputStream {
	return inputStream
    }

    override fun getOutputStream(): OutputStream {
	return outputStream
    }

    override fun getSocket(): Socket? {
	// will only be used for timeout configuration
	return null
    }

    override fun close() {
	inputStream.close()
	outputStream.close()
    }
}
