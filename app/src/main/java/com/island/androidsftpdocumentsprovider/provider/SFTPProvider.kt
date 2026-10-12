package com.island.androidsftpdocumentsprovider.provider

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.database.MatrixCursor.RowBuilder
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.util.Log
import com.island.androidsftpdocumentsprovider.account.Account
import com.island.androidsftpdocumentsprovider.account.Dao
import com.island.androidsftpdocumentsprovider.account.TheDatabase
import com.island.sftp.SFTP
import com.island.sftp.SftpFile
import com.island.sftp.SftpService
import com.island.util.ErrorNotification
import lu.knaff.alain.saf_sftp.R
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
Copyright (C) 2025,2026 Alain Knaff
Copyright (C) 2020      Riccardo Isola

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

open class SFTPProvider : DocumentsProvider() {
    private val connections: MutableSet<SFTP> = HashSet()


    private var dao: Dao? = null

    private val cursors: MutableMap<String?, RefreshableCursor?> =
        HashMap()

    fun registerCursor(
        documentId: String,
        cursor: RefreshableCursor
    ) {
        cursors[documentId] = cursor
    }

    fun unregisterCursor(
        documentId: String,
        cursor: RefreshableCursor
    ) {
        cursors.remove(documentId, cursor)
    }

    @Throws(IOException::class)
    private fun refreshCursorFor(documentId: String?) {
        val mc = cursors[documentId]
        mc?.onChange(false)
    }

    @Throws(IOException::class)
    private fun refreshCursorForParent(documentId: String) {
        var documentId = documentId
        val idx = documentId.lastIndexOf('/')
        if (idx >= 0) documentId = documentId.substring(0, idx)
        refreshCursorFor(documentId)
    }

    override fun onCreate(): Boolean {
        dao = TheDatabase.getDao(context!!)
        return true
    }

    @Throws(FileNotFoundException::class)
    override fun queryRoots(projection: Array<String>?): Cursor {
        cancelStrictMode()
        Log.d(TAG, String.format("queryRoots %s", projection.contentToString()))
        try {
            val result = MatrixCursor(resolveRootProjection(projection))
            val accounts = dao!!.getAllVisibleAccounts()
            for (account in accounts) {
                val documentId = SFTP.accountToDocumentId(account)
                var directory = account.directory
                if (directory.isEmpty()) directory = "/"
                if (directory.length > 1) directory = directory.replace("/$".toRegex(), "")
                val row = result.newRow()
                row.add(DocumentsContract.Root.COLUMN_ROOT_ID, documentId)
                row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, documentId + directory)
                row.add(DocumentsContract.Root.COLUMN_ICON, R.drawable.ic_launcher)
                row.add(
                    DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                            DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
                )
                row.add(DocumentsContract.Root.COLUMN_TITLE, account.getUsedDisplayName())
                if (directory.length > 1) row.add(DocumentsContract.Root.COLUMN_SUMMARY, directory)
            }
            return result
        } catch (e: Exception) {
            throw exception(e, "QueryRoots")
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        var parentDocumentId = parentDocumentId
        var documentId = documentId
        cancelStrictMode()
        parentDocumentId = SFTP.normalize(parentDocumentId)
        documentId = SFTP.normalize(documentId)
        Log.d(
            TAG,
            String.format(
                "isChildDocument: parentDocumentId=%s, documentId=%s",
                parentDocumentId,
                documentId
            )
        )
        if (!documentId.startsWith(parentDocumentId)) return false
        val l = parentDocumentId.length
        if (documentId.length == l) return true // the 2 document ids have the same length => equal

        return documentId[l] == '/'
        // child document has indeed a slash immediately after
        // parent part.
    }

    @Throws(FileNotFoundException::class)
    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        cancelStrictMode()
        Log.d(TAG, String.format("queryDocument %s %s", documentId, projection.contentToString()))
        try {
            val result = MatrixCursor(resolveDocumentProjection(projection))
            var needSftp = false
            if (projection != null) {
                for (column in projection) if (DocumentsContract.Document.COLUMN_DOCUMENT_ID == column ||
                    DocumentsContract.Document.COLUMN_SIZE == column ||
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED == column ||
                    DocumentsContract.Document.COLUMN_MIME_TYPE == column ||
                    DocumentsContract.Document.COLUMN_FLAGS == column
                ) {
                    needSftp = true
                    break
                }
            } else needSftp = true
            val mc = result.newRow()
            if (needSftp) {
                val sftp = getSFTP(documentId)
                putFileInfo(mc, sftp, sftp.getFile(documentId))
            } else putFileInfo(mc, documentId)
            return result
        } catch (e: Exception) {
            throw exception(e, "QueryDocument", documentId)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?
    ): Cursor {
        cancelStrictMode()
        Log.d(
            TAG,
            String.format(
                "queryChildDocuments %s %s %s",
                parentDocumentId,
                projection.contentToString(),
                projection.contentToString()
            )
        )
        try {
            val sftp = getSFTP(parentDocumentId, true)
            val parent = sftp.getFile(parentDocumentId)
            return DirectoryCursor(
                this,
                parentDocumentId,
                sftp
            )
                .start {
                    val files = sftp.listFiles(parent)
                    connections.add(sftp)
                    files
                }
        } catch (e: Exception) {
            throw exception(e, "QueryChildDocuments", parentDocumentId)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        cancelStrictMode()
        Log.d(TAG, String.format("openDocument %s %s %s", documentId, mode, signal))
        try {
            val sftp = getSFTP(documentId, true)
            val serverFile = sftp.getFile(documentId)

            try {
                return Proxy.open(
                    context!!, sftp, serverFile, mode
                ) { s: SFTP? -> connections.add(s!!) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open proxy file descriptor", e)
                // openProxyFileDescriptor can throw an exception without
                // invoking onRelease
                remove(sftp)
                throw FileNotFoundException(serverFile.path)
            }
        } catch (e: Exception) {
            throw exception(e, "openDocument", documentId)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String? {
        cancelStrictMode()
        Log.d(
            TAG,
            String.format("createDocument %s %s %s", parentDocumentId, mimeType, displayName)
        )
        try {
            val sftp = getSFTP(parentDocumentId)
            try {
                val parent = sftp.getFile(parentDocumentId)
                val documentId = arrayOfNulls<String>(1)
                val file = uniqueFile(
                    sftp, parent, displayName,
                    documentId
                )
                if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                    sftp.mkdirs(file)
                    refreshCursorFor(parentDocumentId)
                } else sftp.newFile(file)
                return documentId[0]
            } catch (e: SocketException) {
                remove(sftp)
                throw e
            }
        } catch (e: Exception) {
            throw exception(e, "CreateDocument", parentDocumentId)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun deleteDocument(documentId: String) {
        cancelStrictMode()
        Log.d(TAG, String.format("deleteDocument %s", documentId))
        try {
            val sftp = getSFTP(documentId)
            try {
                sftp.delete(sftp.getFile(documentId))
                refreshCursorForParent(documentId)
            } catch (e: SocketException) {
                remove(sftp)
                throw e
            }
        } catch (e: Exception) {
            throw exception(e, "DeleteDocument", documentId)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun getDocumentType(documentId: String): String {
        cancelStrictMode()
        Log.d(TAG, String.format("getDocumentType %s", documentId))
        try {
            val sftp = getSFTP(documentId)
            try {
                val mimeType = sftp.getMimeType(sftp.getFile(documentId))
                return mimeType
            } catch (e: SocketException) {
                remove(sftp)
                throw e
            }
        } catch (e: Exception) {
            throw exception(e, "GetDocumentType", documentId)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun renameDocument(
        documentId: String,
        displayName: String
    ): String {
        cancelStrictMode()
        Log.d(TAG, String.format("renameDocument %s %s", documentId, displayName))
        try {
            val sftp = getSFTP(documentId)
            try {
                val source: File = sftp.getFile(documentId)
                val parent = source.parentFile
                val destination: File = uniqueFile(sftp, parent!!, displayName)
                sftp.renameTo(source, destination)
                refreshCursorForParent(documentId)
                return sftp.getDocumentId(destination)
            } catch (e: SocketException) {
                remove(sftp)
                throw e
            }
        } catch (e: Exception) {
            throw exception(e, "RenameDocument", documentId)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String {
        cancelStrictMode()
        Log.d(
            TAG,
            String.format(
                "moveDocument %s %s %s",
                sourceDocumentId,
                sourceParentDocumentId,
                targetParentDocumentId
            )
        )
        try {
            val sftp = getSFTP(sourceDocumentId)
            val source: File = sftp.getFile(sourceDocumentId)
            try {
                val destination: File = uniqueFile(
                    sftp,
                    sftp.getFile(targetParentDocumentId),
                    source.name
                )
                sftp.renameTo(source, destination)
                refreshCursorForParent(sourceDocumentId)
                return sftp.getDocumentId(destination)
            } catch (e: SocketException) {
                remove(sftp)
                throw e
            }
        } catch (e: Exception) {
            throw exception(e, "MoveDocument", sourceDocumentId, targetParentDocumentId)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun copyDocument(
        sourceDocumentId: String,
        targetParentDocumentId: String
    ): String {
        cancelStrictMode()
        Log.d(TAG, String.format("copyDocument %s %s", sourceDocumentId, targetParentDocumentId))
        try {
            val sftp = getSFTP(sourceDocumentId)
            val source: File = sftp.getFile(sourceDocumentId)
            try {
                val destination = uniqueFile(
                    sftp,
                    sftp.getFile(targetParentDocumentId),
                    source.name
                )
                sftp.copy(source, destination)
                refreshCursorForParent(sourceDocumentId)
                refreshCursorForParent(targetParentDocumentId)
                return sftp.getDocumentId(destination)
            } catch (e: SocketException) {
                remove(sftp)
                throw e
            }
        } catch (e: Exception) {
            throw exception(e, "CopyDocument", sourceDocumentId, targetParentDocumentId)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun findDocumentPath(
        parentDocId: String?,
        childDocId: String
    ): DocumentsContract.Path {
        // TODO: handle non-null parentDocId. Right now we have no test case

        if (parentDocId != null) throw UnsupportedOperationException("Non-null parentDocId not yet supported due to lack of test case")

        val rootId = SFTP.documentIdToAccount(childDocId)

        val account = dao!!.readAccountByName(rootId)
        var extendedRoot = rootId
        if (account != null) {
            extendedRoot += account.directory.replace("/$".toRegex(), "")
        }

        // Skip legacy sftp:// prefix
        val start = childDocId.indexOf(extendedRoot)
        if (start < 0) throw FileNotFoundException("$childDocId not below configured start directory")

        // Split path along slashes
        val children: MutableList<String> = ArrayList()
        var i = start + extendedRoot.length // index of next slash
        val length = childDocId.length
        while (i < length) {
            i = childDocId.indexOf('/', i)
            if (i == -1) break
            children.add(childDocId.substring(start, i))
            i += 1
        }
        children.add(childDocId)
        return DocumentsContract.Path(rootId, children)
    }

    @Throws(IOException::class)
    private fun getSFTP(documentId: String): SFTP {
        return getSFTP(documentId, false)
    }

    @Throws(IOException::class)
    private fun getSFTP(documentId: String, needsFresh: Boolean): SFTP {
        SftpService.start(context!!)
        var sftp: SFTP? = null
        val toRemove: MutableSet<SFTP?> = HashSet()
        for (connection in connections) {
            if (SFTP.documentIdToAccount(connection.documentId)
                == SFTP.documentIdToAccount(documentId)
            ) {
                if (!connection.isConnected) {
                    Log.d(TAG, "Connection closed, cleaning")
                    toRemove.add(connection)
                    continue
                }
                sftp = connection
                if (needsFresh)  // remove connection from pool while used elsewhere
                    connections.remove(sftp)
                break
            }
        }
        connections.removeAll(toRemove)
        if (sftp == null) {
            sftp = createSftp(documentId)
            if (!needsFresh) connections.add(sftp)
        }
        return sftp
    }

    @Throws(IOException::class)
    private fun createSftp(documentId: String): SFTP {
        val account: Account = getAccountInfo(context, documentId)
        try {
            return SFTP(context!!, documentId, account)
        } catch (e: ConnectException) {
            ErrorNotification.sendNotification(
                context!!,
                documentId,
                e
            )
            throw e
        }
    }

    private fun putFileInfo(row: RowBuilder, documentId: String) {
        val name = documentId.replace("^.*/".toRegex(), "")
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
    }

    @Throws(IOException::class)
    private fun putFileInfo(
        row: RowBuilder, sftp: SFTP,
        file: SftpFile
    ) {
        try {
            var flags: Int
            if (sftp.isDirectory(file)) flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            else {
                flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                row.add(DocumentsContract.Document.COLUMN_SIZE, sftp.length(file))
            }
            flags = flags or (DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                    DocumentsContract.Document.FLAG_SUPPORTS_COPY or
                    DocumentsContract.Document.FLAG_SUPPORTS_MOVE or
                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME)
            row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
            val mimeType = sftp.getMimeType(file)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
            val name = file.name
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
            val documentId = sftp.getDocumentId(file)
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
            val lastModified = sftp.lastModified(file)
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified)
        } catch (e: SocketException) {
            remove(sftp)
            throw e
        }
    }

    @Throws(IOException::class)
    private fun uniqueFile(
        sftp: SFTP, parent: File, displayName: String,
        documentIdP: Array<String?>? = null
    ): SftpFile {

        val base: String?
        val extension: String?
        val dotIdx = displayName.lastIndexOf('.')
        if (dotIdx >= 0) {
            base = displayName.substring(0, dotIdx)
            extension = displayName.substring(dotIdx)
        } else {
            base = displayName
            extension = ""
        }
        var cnt = 0
        while (true) {
            val seq = if (cnt == 0) ""
            else "_$cnt"
            val documentId = sftp.getDocumentId(
                File(
                    parent,
                    base + seq + extension
                )
            )
            val file = sftp.getFile(documentId)
            try {
                sftp.lastModified(file)
                cnt++
                continue
            } catch (_: FileNotFoundException) {
                if (documentIdP != null) documentIdP[0] = documentId
                return file
            }
        }
    }

    private fun exception(
        e: Exception,
        msg: String,
        vararg args: Any?
    ): FileNotFoundException {
        var msg = msg
        for (arg in args) msg += " $arg"
        Log.e(TAG, msg, e)
        val exception = FileNotFoundException(msg)
        exception.initCause(e)
        return exception
    }

    private fun remove(sftp: SFTP) {
        connections.remove(sftp)
        try {
            sftp.close()
        } catch (e: IOException) {
            Log.e(TAG, "sftp close exception", e)
        }
    }

    private fun cancelStrictMode() {
        // if an application directly opens a file on a root
        // supplied by this document provider from its UI
        // thread, the StrictMode attached to that thread
        // carries over to the document provider via binder,
        // preventing it to invoke network (for SFTP),
        // although it's really the calling app's fault. =>
        // cancel StrictMode. This is legitimate as we can't
        // really do anything about it, as Documents Provider
        // API is synchronous (return from same method, rather
        // than calling a callback when done). Any solution
        // other than canceling StrictMode would involve
        // cheating by handing processing off to another
        // thread, but then waiting for that thread, blocking
        // anyway
        val gfgPolicy =
            ThreadPolicy.Builder()
                .permitAll()
                .build()
        StrictMode.setThreadPolicy(gfgPolicy)
    }

    companion object {
        const val TAG: String = "SFTPProvider"
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_SUMMARY
        )
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS
        )

        private fun resolveDocumentProjection(projection: Array<String>?): Array<String> {
            return projection ?: DEFAULT_DOCUMENT_PROJECTION
        }

        @JvmStatic
        protected fun resolveRootProjection(projection: Array<String>?): Array<String> {
            return projection ?: DEFAULT_ROOT_PROJECTION
        }

        @Throws(IOException::class)
        private fun getAccountInfo(context: Context?, documentId: String): Account {
            val dao = TheDatabase.getDao(context!!)
            val accountName = SFTP.documentIdToAccount(documentId!!)
            val account: Account =
                dao.readAccountByName(accountName) ?: throw FileNotFoundException(documentId)
            return account
        }
    }
}
