package com.island.sftp

import android.content.Context
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import com.island.androidsftpdocumentsprovider.account.Account
import com.island.sftp.SftpFile.Companion.makeDirectory
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelSftp.LsEntry
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.UserInfo
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ProtocolException
import java.net.SocketException
import java.util.Objects
import java.util.Vector
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
Copyright (C) 2025,2026 Alain Knaff
Copyright (C) 2019,2020 Riccardo Isola

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

class SFTP @JvmOverloads constructor(
    ctx: Context,
    var documentId: String,
    account: Account,
    userInfo: UserInfo? = null
) : SSH(ctx, account, userInfo), Closeable {
    private var channel: ChannelSftp? = null
    private var disconnected = false

    private val files: MutableMap<String?, SftpFile?> = ConcurrentHashMap()

    init {
        var startDirectory = account.directory
        if (startDirectory.isEmpty()) startDirectory = "/"
        files[startDirectory] = makeDirectory(startDirectory)
    }

    @Throws(JSchException::class)
    override fun makeSession(): Session {
        val session = super.makeSession()
        channel = session.openChannel("sftp") as ChannelSftp?
        channel!!.connect()
        return session
    }

    @Synchronized
    @Throws(JSchException::class)
    private fun reconnectIfNeeded() {
        if (session == null) makeSession()
        if (!session!!.isConnected) {
            try {
                Log.d(Companion.TAG, "Reconnecting session")
                session!!.connect()
            } catch (_: JSchException) {
                // if it fails, just re-create the session from scratch
                // https://stackoverflow.com/questions/16127200/jsch-how-to-keep-the-session-alive-and-up
                Log.d(
                    Companion.TAG,
                    "Session unusable, create a new one"
                )
                makeSession()
            }
        }
        if (!channel!!.isConnected()) {
            Log.d(Companion.TAG, "Reconnecting channel")
            channel!!.connect()
        }
    }

    @Throws(IOException::class)
    fun lastModified(file: SftpFile): Long {
        checkArguments(file)
        if (file.sftpLastModified == -1L) listFile(file)
        return file.sftpLastModified
    }

    @Throws(IOException::class)
    fun length(file: SftpFile): Long {
        checkArguments(file)
        if (file.size == -1L) listFile(file)
        return file.size
    }

    @Throws(IOException::class)
    fun isDirectory(file: SftpFile): Boolean {
        checkArguments(file)
        if (file.isDirectory == null) {
            listFile(file)
        }
        return file.isDirectory!!
    }

    @Synchronized
    @Throws(IOException::class)
    override fun close() {
        if (session != null) session!!.disconnect()
        if (channel != null) channel!!.quit()
        disconnected = true
    }

    @Synchronized
    @Throws(IOException::class)
    fun listFile(file: SftpFile) {
        Log.d(Companion.TAG, "List file $file")
        checkArguments(file)
        try {
            reconnectIfNeeded()
            try {
                val attrs = channel!!.stat(file.path)
                Log.d(Companion.TAG, "Found $file")
                file.setAttrs(attrs)
            } catch (e: SftpException) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    // all is ok, file simply doesn't exist
                    Log.d(Companion.TAG, "Not found $file")
                    throw FileNotFoundException(file.path)
                }
                throw e
            }
        } catch (e: JSchException) {
            throw getException(e)
        } catch (e: SftpException) {
            throw getException(e)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun listFiles(directory: File): Array<SftpFile> {
        checkArguments(directory)
        try {
            reconnectIfNeeded()
            val vector: Vector<*> = channel!!.ls(directory.path)
            val files: MutableList<SftpFile> = ArrayList(vector.size)
            for (obj in vector) {
                val entry =
                    obj as LsEntry
                if (entry.filename == "." ||
                    entry.filename == ".."
                ) continue
                val file: SftpFile?
                val attrs = entry.attrs
                if (attrs.isLink) {
                    continue
                    /* Resolving links is excessively slow
		       which poses problems on Sdk 37
		    var lnk = File(directory,entry.getFilename())
		    try {
			// stat takes 30 milliseconds on SDK 37
			attrs=channel!!.stat(lnk.path)
		    } catch(_: Exception) {
			Log.e(TAG,
			      "Could not read "+lnk.path)
			continue
		    }
                    */
                }
                val fileName = entry.filename
                file = SftpFile(directory, fileName, attrs)
                this.files[fileName] = file
                files.add(file)
            }
            return files.toTypedArray<SftpFile>()
        } catch (e: JSchException) {
            throw getException(e)
        } catch (e: SftpException) {
            throw getException(e)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun newFile(file: SftpFile) {
        checkArguments(file)
        try {
            reconnectIfNeeded()
            channel!!.put(file.path).close()
            file.markAsNewFile()
        } catch (e: JSchException) {
            throw getException(e)
        } catch (e: SftpException) {
            throw getException(e)
        }
    }

    /**
     * Forget cached attributes of a path
     * (after delete or rename), so later lookups ask the server again.
     * No need to care about files deeper down, as to reach those, another
     * directory listing would be required anyways
     */
    private fun evict(path: String)
    {
	files.remove(path);
    }

    /**
     * Ask the server (bypassing cached attributes) whether a file exists.
     * Refreshes the cached attributes if it does, clears them if it does not.
     */
    @Synchronized
    fun existsOnServer(file: SftpFile) : Boolean {
	try {
	    listFile(file);
	    return true;
	} catch(e: FileNotFoundException) {
	    file.invalidate();
	    return false;
	}
    }

    @Synchronized
    @Throws(IOException::class)
    fun delete(file: SftpFile) {
        checkArguments(file)
        try {
            reconnectIfNeeded()
            if (isDirectory(file)) {
                for (child in listFiles(file)) delete(child)
                channel!!.rmdir(file.path)
            } else channel!!.rm(file.path)
            evict(file.path)
        } catch (e: JSchException) {
            throw getException(e)
        } catch (e: SftpException) {
            throw getException(e)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun read(
        file: File,
        offset: Long
    ): InputStream {
        checkArguments(file)
        try {
            reconnectIfNeeded()
            return channel!!.get(file.path, null, offset)
        } catch (e: JSchException) {
            throw getException(e)
        } catch (e: SftpException) {
            throw getException(e)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun mkdirs(file: SftpFile) {
        checkArguments(file)
        try {
            reconnectIfNeeded()
            channel!!.mkdir(file.path)
            file.markAsNewDirectory()
        } catch (e: JSchException) {
            throw getException(e)
        } catch (e: SftpException) {
            throw getException(e)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun renameTo(
        oldPath: File,
        newPath: File
    ) {
        checkArguments(oldPath, newPath)
        try {
            reconnectIfNeeded()
            channel!!.rename(oldPath.path, newPath.path)
            evict(oldPath.path)
            evict(newPath.path)
        } catch (e: JSchException) {
            throw getException(e)
        } catch (e: SftpException) {
            throw getException(e)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun write(
        file: File,
        mode: Int,
        offset: Long
    ): OutputStream {
        checkArguments(file)
        try {
            reconnectIfNeeded()
            return channel!!.put(file.path, null, mode, offset)
        } catch (e: JSchException) {
            throw getException(e)
        } catch (e: SftpException) {
            throw getException(e)
        }
    }

    fun getFile(documentId: String): SftpFile {
        Objects.requireNonNull(documentId)
        val m: Matcher = accountPattern.matcher(documentId)
        var path: String?
        path = if (m.matches()) m.group(2)
        else ""
        if (path.isEmpty() || path[0] != '/') path = account.directory + "/" + path
        return getFileForPath(path)
    }

    private fun getFileForPath(path: String): SftpFile {
        var cachedFile = files[path]
        if (cachedFile == null) {
            Log.d(Companion.TAG, "File $path not found in cache")
            cachedFile = SftpFile(path)
            files[path] = cachedFile
        } else {
            Log.d(Companion.TAG, "File $path found in cache")
        }
        return cachedFile
    }

    fun getDocumentId(file: File): String {
        Objects.requireNonNull(file)
        return documentIdToAccount(documentId) + file.path
    }

    @Synchronized
    @Throws(IOException::class)
    fun copy(
        from: File,
        to: File
    ) {
        checkArguments(from, to)
        try {
            reconnectIfNeeded()
            val input: InputStream = BufferedInputStream(channel!!.get(from.path))
            val output: OutputStream = BufferedOutputStream(channel!!.put(to.path))
            val buffer = ByteArray(BUFFER)
            while (true) if (write(input, output, buffer) == -1) break
            input.close()
            output.close()
        } catch (e: JSchException) {
            throw getException(e)
        } catch (e: SftpException) {
            throw getException(e)
        }
    }

    @Throws(IOException::class)
    fun getMimeType(file: SftpFile): String {
        Objects.requireNonNull(file)
        if (isDirectory(file)) {
            return DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            val name = file.name
            val lastDot = name.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = name.substring(lastDot + 1)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                if (mime != null) return mime
            }
            return "application/octet-stream"
        }
    }

    private fun getException(cause: Exception): IOException {

        if (cause.cause != null) {
            val exception = SocketException("Connection closed")
            exception.initCause(cause)
            return exception
        } else {
            val exception = ProtocolException("sftp")
            exception.initCause(cause)
            return exception
        }
    }

    private fun checkArguments(vararg arguments: Any?) {
        for (argument in arguments) Objects.requireNonNull<Any?>(
            argument,
            arguments.contentToString()
        )
        check(!disconnected) { "Connection already closed" }
    }

    val isConnected: Boolean
        get() = channel != null && channel!!.isConnected()

    companion object {
        private const val TAG = "SFTP"
        private const val BUFFER = 1024
        fun accountToDocumentId(account: Account): String {
            return account.name!!
        }

        var accountPattern: Pattern = Pattern.compile("(?:sftp://?)?([^/]*)(.*)")

        fun documentIdToAccount(documentId: String): String {
            val m: Matcher = accountPattern.matcher(documentId)
            return if (m.matches()) m.group(1)!!
            else documentId
        }

        // remove prefix of older scheme
        fun normalize(documentId: String): String {
            return documentId.replace("^sftp://".toRegex(), "")
        }

        @Throws(IOException::class)
        private fun write(input: InputStream, output: OutputStream, buffer: ByteArray): Int {
            val bytesRead = input.read(buffer)
            if (bytesRead != -1) {
                output.write(buffer, 0, bytesRead)
            }
            return bytesRead
        }
    }
}
