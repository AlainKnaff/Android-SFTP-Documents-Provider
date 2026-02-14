package com.island.androidsftpdocumentsprovider.provider

import java.io.OutputStream
import java.io.IOException;
import java.io.InputStream
import java.io.File
import java.util.function.Consumer

import android.util.Log
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.Handler
import android.os.HandlerThread
import android.os.storage.StorageManager

import com.island.sftp.SFTP

/**
 * This version of the class is single threaded, and depends on running on
 * one handler
 */
class Proxy private constructor(val sftp: SFTP,
                                val file: File,
                                var recycler: Consumer<SFTP>?
) : ProxyFileDescriptorCallback() {
    val TAG = "Proxy"

    private val ioThread = HandlerThread(javaClass.simpleName).apply { start() }
    private val ioHandler = Handler(ioThread.looper)

    companion object {
        @JvmStatic
        fun open(context: Context,
                 sftp: SFTP, file: File,
                 accessMode: Int,
                 recycler: Consumer<SFTP>) : ParcelFileDescriptor {
            val proxy = Proxy(sftp, file, recycler)
            var storageManager = context
                .getSystemService(StorageManager::class.java)
            return storageManager
                .openProxyFileDescriptor(accessMode, proxy, proxy.ioHandler)
        }
    }

    var inStr : InputStream? = null
    var inPos: Long = 0L

    var outStr : OutputStream? = null
    var outPos: Long = 0L

    private fun closeCurrentStreams() {
        val _inStr = inStr
        if(_inStr != null)
            try {
                _inStr.close()
            } catch(e: IOException) {
                Log.e(TAG, "Error closing input stream", e)
            }
        inStr = null
        inPos = 0L

        val _outStr = outStr
        if(_outStr != null)
            try {
                _outStr.close()
            } catch(e: IOException) {
                Log.e(TAG, "Error closing output stream", e)
            }
        outStr = null
        outPos = 0L
    }

    override fun onGetSize(): Long {
        Log.d(TAG, "Getting length of ${file.path}")
        return sftp.length(file)
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        if(inStr == null || offset != inPos) {
            closeCurrentStreams()
            Log.d(TAG, "Setting file pos of ${file} to ${offset}")
            inStr = sftp.read(file, offset)
            inPos = offset
        }
        var sizeLeft = size
        // Log.d(TAG, "${this}: Reading ${offset}+${size} from ${file.path}")
        var bufOffset = 0
        while(sizeLeft > 0) {
            val n = inStr!!.read(data, bufOffset, sizeLeft)
            // Log.d(TAG, "Got ${n} bytes")
            if(n <= 0) {
                if(bufOffset > 0) {
                    break
                } else {
                    if(n < 0)
                        Log.e(TAG, "Error ${n} on ${file}")
                    else
                        Log.d(TAG, "EOF reached on ${file}")
                    return n
                }
            }
            bufOffset += n
            inPos += n
            sizeLeft -= n
        }
        // Log.i(TAG, "Got total ${bufOffset} bytes")
        return bufOffset
    }

    // first write ever is 0 (OVERWRITE) to make sure file is
    // truncated initially, and from then on 3 (Neither OVERWRITE nor
    // RESUME nor APPEND) to make sure new data is added rather than
    // re-truncating file over and over again
    var mode = 0

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        if(outStr == null || offset != outPos) {
            closeCurrentStreams()
            outStr = sftp.write(file, mode, offset)
            mode = 3
            outPos = offset
        }

        // Log.i(TAG, "Write ${offset}+${size} to ${file.path}")
        try {
            outStr!!.write(data, 0,size)
        } catch(e: IOException) {
            Log.e(TAG, "Error while writing", e)
            closeCurrentStreams()
        }
        outPos += size
        return size
    }

    override fun onFsync() {
        val _outStr = outStr
        if(_outStr != null)
            try {
                _outStr.flush()
            } catch(e: IOException) {
                Log.e(TAG, "Error while flushing", e)
                outStr = null
            }
    }

    override fun onRelease() {
        Log.d(TAG, "On release")
        closeCurrentStreams()
        ioThread.quitSafely()
        val _recycler = recycler
        if(_recycler != null && sftp.isConnected())
            _recycler.accept(sftp)
        recycler=null
    }
}
