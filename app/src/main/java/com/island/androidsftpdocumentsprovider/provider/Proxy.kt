package com.island.androidsftpdocumentsprovider.provider;

import java.io.File
import java.util.function.Consumer

import android.util.Log;
import android.content.Context
import android.os.ParcelFileDescriptor;
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
                                val recycler: Consumer<SFTP>
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

    override fun onGetSize(): Long {
        Log.i(TAG, "Getting length of ${file.path}")
        return sftp.length(file)
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        val inpStr = sftp.read(file, offset)
        var sizeLeft = size
        // Log.i(TAG, "${this}: Reading ${offset}+${size} from ${file.path}")
        inpStr.use {
            var bufOffset = 0
            while(sizeLeft > 0) {
                val n = inpStr.read(data, bufOffset, sizeLeft)
                // Log.i(TAG, "Got ${n} bytes")
                if(n <= 0) {
                    if(bufOffset > 0) {
                        break
                    } else {
                        Log.i(TAG, "Error ${n} bytes")
                        return n
                    }
                }
                bufOffset += n
                sizeLeft -= n
            }
            // Log.i(TAG, "Got total ${bufOffset} bytes")
            return bufOffset
        }
    }

    // first write ever is 0 (OVERWRITE) to make sure file is
    // truncated initially, and from then on 3 (Neither OVERWRITE nor
    // RESUME nor APPEND) to make sure new data is added rather than
    // re-truncating file over and over again
    var mode = 0

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        // Log.i(TAG, "Write ${offset}+${size} to ${file.path}")
        val os = sftp.write(file, mode, offset)
        mode = 3
        os.use {
            os.write(data, 0,size)
            return size
        }
    }

    override fun onFsync() {
        Log.i(TAG, "Fsync not yet implemented")
    }

    override fun onRelease() {
        Log.i(TAG, "On release")
        ioThread.quitSafely()
        if(sftp.isConnected())
            recycler.accept(sftp)
    }
}
