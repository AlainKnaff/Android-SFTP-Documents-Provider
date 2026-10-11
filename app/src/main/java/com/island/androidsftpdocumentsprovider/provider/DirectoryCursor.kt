package com.island.androidsftpdocumentsprovider.provider

import android.database.AbstractCursor
import android.database.ContentObserver
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import com.island.sftp.SFTP
import com.island.sftp.SftpFile
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

class DirectoryCursor(val provider: SFTPProvider,
                      val documentId: String,
                      val sftp: SFTP) : AbstractCursor(), RefreshableCursor {
    companion object {
	const val TAG = "DirectoryCursor"

        val executor : ExecutorService = Executors.newFixedThreadPool(2)

        val DEFAULT_DOCUMENT_PROJECTION : Array<String> = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_SIZE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS
        )

        const val DOCUMENT_ID_IDX   = 0
        const val SIZE_IDX          = 1
        const val DISPLAY_NAME_IDX  = 2
        const val LAST_MODIFIED_IDX = 3
        const val MIME_TYPE_IDX     = 4
        const val FLAGS_IDX         = 5

	var cachedProvider: SFTPProvider? = null
	var cachedDocumentId: String? = null
	var cachedFiles: Array<SftpFile>? = null

	val Lock = Any()

	fun storeIntoCache(provider: SFTPProvider,
			   documentId: String,
			   files : Array<SftpFile>) {
	    synchronized(Lock) {
		cachedProvider=provider
		cachedDocumentId=documentId
		cachedFiles=files
	    }
	}

	fun retrieveFromCache(provider: SFTPProvider,
			      documentId: String): Array<SftpFile>? {
	    synchronized(Lock) {
		if(provider==cachedProvider &&
            documentId == cachedDocumentId
        ) {
		    Log.i(TAG, "Found in cache: $documentId")
		    return cachedFiles
		} else {
		    Log.i(TAG, "Not cached: $documentId")
		    return null
		}
	    }
	}
    }

    lateinit var files : Array<SftpFile>
    lateinit var f : FutureTask<Array<SftpFile>>

    var error : String? = null

    fun start(c: Callable<Array<SftpFile>>) : DirectoryCursor {
        f = FutureTask(c)
        executor.submit(f)
        return this
    }

    fun fetch() {
        if(this::files.isInitialized)
            return
        try {
            files = f.get()
            storeIntoCache(provider, documentId, files)
        } catch(e: ExecutionException) {
            // ErrorNotification.sendNotification(provider.context,
            //                                   documentId, e.cause)
            // throw e
	    Log.i(TAG, "Exception caught in fetch", e)
	    var t : Throwable = e
	    while(true) {
            val c = t.cause ?: break
            t = c
	    }
	    error = t.toString()
	    var localFiles: Array<SftpFile>? = null
	    if(t is UnknownHostException) {
		localFiles = retrieveFromCache(provider, documentId)
	    }

	    if(localFiles == null)
		localFiles = arrayOf()
	    else
		error = null
	    files=localFiles
        }
    }

    override fun getColumnNames() : Array<String> {
        return DEFAULT_DOCUMENT_PROJECTION
    }

    override fun getCount() : Int {
        fetch()
        return files.size
    }

    override fun getDouble(i: Int) : Double {
        throw UnsupportedOperationException()
    }

    override fun getFloat(i: Int) : Float {
        throw UnsupportedOperationException()
    }

    private fun getFlags(file: SftpFile) : Int {
        var flags : Int = if(sftp.isDirectory(file))
            Document.FLAG_DIR_SUPPORTS_CREATE
        else
            Document.FLAG_SUPPORTS_WRITE
        flags = flags or
        Document.FLAG_SUPPORTS_DELETE or
	Document.FLAG_SUPPORTS_COPY or
	Document.FLAG_SUPPORTS_MOVE or
	Document.FLAG_SUPPORTS_RENAME
        return flags
    }

    override fun getInt(i: Int) : Int {
        fetch()
        val file = files[position]
        return when(i) {
            FLAGS_IDX -> getFlags(file)
            else -> getLong(i).toInt()
        }
    }

    private fun unsupported(i: Int) : UnsupportedOperationException {
        return UnsupportedOperationException("Index $i not supported")
    }

    override fun getLong(i: Int) : Long {
        fetch()
        val file = files[position]
        return when(i) {
            SIZE_IDX -> file.size
            LAST_MODIFIED_IDX -> file.sftpLastModified
            FLAGS_IDX -> getInt(i).toLong()
            else -> throw unsupported(i)
        }
    }

    override fun getShort(i: Int) : Short {
        return getLong(i).toShort()
    }

    override fun getString(i: Int) : String {
        fetch()
        val file = files[position]
        return when(i) {
            DOCUMENT_ID_IDX -> sftp.getDocumentId(file)
            DISPLAY_NAME_IDX -> file.name
            MIME_TYPE_IDX -> sftp.getMimeType(file)
            else -> throw unsupported(i)
        }
    }

    override fun getType(i: Int) : Int {
        return when(i) {
            DOCUMENT_ID_IDX    -> FIELD_TYPE_STRING
            SIZE_IDX           -> FIELD_TYPE_INTEGER
            DISPLAY_NAME_IDX   -> FIELD_TYPE_STRING
            LAST_MODIFIED_IDX  -> FIELD_TYPE_INTEGER
            MIME_TYPE_IDX      -> FIELD_TYPE_STRING
            FLAGS_IDX          -> FIELD_TYPE_INTEGER
            else               -> throw unsupported(i)
        }
    }

    override fun isNull(column: Int) : Boolean {
        return false
    }

    override fun getExtras() : Bundle {
	fetch()
	var b = super.getExtras()
	if(error != null) {
	    if(b == Bundle.EMPTY)
		b = Bundle()
	    b.putCharSequence(DocumentsContract.EXTRA_ERROR, error)
	}
	return b
    }

    override fun onChange(selfChange: Boolean) {
	super.onChange(selfChange)
    }

    override fun registerContentObserver(observer: ContentObserver) {
	super.registerContentObserver(observer)
	provider.registerCursor(documentId, this)
    }

    override fun unregisterContentObserver(observer: ContentObserver) {
	super.unregisterContentObserver(observer)
	provider.unregisterCursor(documentId, this)
    }
}
