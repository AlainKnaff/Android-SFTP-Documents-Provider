package com.island.androidsftpdocumentsprovider.provider;

import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

import android.util.Log
import android.net.Uri
import android.provider.DocumentsContract.Document;
import android.database.ContentObserver;
import android.database.AbstractCursor

import com.island.sftp.SFTP;
import com.island.sftp.SftpFile;

class DirectoryCursor(val provider: SFTPProvider,
                      val documentId: Uri,
                      val sftp: SFTP) : AbstractCursor(), RefreshableCursor {
    val TAG = "DirectoryCursor"

    companion object {
        val executor = Executors.newFixedThreadPool(2)

        val DEFAULT_DOCUMENT_PROJECTION : Array<String> = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_SIZE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS
        );

        val DOCUMENT_ID_IDX   = 0;
        val SIZE_IDX          = 1;
        val DISPLAY_NAME_IDX  = 2;
        val LAST_MODIFIED_IDX = 3;
        val MIME_TYPE_IDX     = 4;
        val FLAGS_IDX         = 5;
    }

    lateinit var files : Array<SftpFile>
    lateinit var f : FutureTask<Array<SftpFile>>

    fun start(c: Callable<Array<SftpFile>>) : DirectoryCursor {
        f = FutureTask(c)
        executor.submit(f)
        return this
    }

    fun fetch() {
        try {
            files = f.get()
        } catch(e: ExecutionException) {
            val t = e.cause
            Log.e(TAG, "Exception "+t+" during fetch", t)
            files = arrayOf()
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
        var flags : Int
        if(sftp.isDirectory(file))
            flags=Document.FLAG_DIR_SUPPORTS_CREATE;
        else
            flags=Document.FLAG_SUPPORTS_WRITE;
        flags = flags or
        Document.FLAG_SUPPORTS_DELETE or
	Document.FLAG_SUPPORTS_COPY or
	Document.FLAG_SUPPORTS_MOVE or
	Document.FLAG_SUPPORTS_RENAME
        return flags
    }

    override fun getInt(i: Int) : Int {
        fetch()
        val file = files[getPosition()];
        when(i) {
            FLAGS_IDX -> return getFlags(file)
            else -> return getLong(i).toInt()
        }
    }

    private fun unsupported(i: Int) : UnsupportedOperationException {
        return UnsupportedOperationException("Index "+i+ " not supported")
    }

    override fun getLong(i: Int) : Long {
        fetch()
        val file = files[getPosition()];
        when(i) {
            SIZE_IDX -> return file.getSize()
            LAST_MODIFIED_IDX -> return file.getSftpLastModified()
            FLAGS_IDX -> return getInt(i).toLong()
            else -> throw unsupported(i)
        }
    }

    override fun getShort(i: Int) : Short {
        return getLong(i).toShort()
    }

    override fun getString(i: Int) : String {
        fetch()
        val file = files[getPosition()];
        when(i) {
            DOCUMENT_ID_IDX -> return sftp.getUri(file).toString()
            DISPLAY_NAME_IDX -> return file.getName()
            MIME_TYPE_IDX -> return sftp.getMimeType(file)
            else -> throw unsupported(i)
        }
    }

    override fun getType(i: Int) : Int {
        when(i) {
            DOCUMENT_ID_IDX    -> return FIELD_TYPE_STRING
            SIZE_IDX           -> return FIELD_TYPE_INTEGER
            DISPLAY_NAME_IDX   -> return FIELD_TYPE_STRING
            LAST_MODIFIED_IDX  -> return FIELD_TYPE_INTEGER
            MIME_TYPE_IDX      -> return FIELD_TYPE_STRING
            FLAGS_IDX          -> return FIELD_TYPE_INTEGER
            else               -> throw unsupported(i)
        }
    }

    override fun isNull(column: Int) : Boolean {
        return false;
    }

    override fun onChange(selfChange: Boolean) {
	super.onChange(selfChange);
    }

    override fun registerContentObserver(observer: ContentObserver) {
	super.registerContentObserver(observer);
	provider.registerCursor(this, documentId);
    }

    override fun unregisterContentObserver(observer: ContentObserver) {
	super.unregisterContentObserver(observer);
	provider.unregisterCursor(this, documentId);
    }
}
