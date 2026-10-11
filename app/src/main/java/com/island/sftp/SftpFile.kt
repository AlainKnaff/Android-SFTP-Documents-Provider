package com.island.sftp

import com.jcraft.jsch.SftpATTRS
import java.io.File

class SftpFile : File {
    /**
     * Gets lastModified. LastModified is the last modified time of file
     * @return the lastModified
     */
    var sftpLastModified: Long = -1
        private set

    /**
     * Gets size. Size is the size of the file
     * @return the size
     */
    var size: Long = -1
        private set

    /**
     * Set size to 0 (invoked when writing file in truncate mode
     */
    fun truncateSize() {
        size = 0
    }

    /**
     * Extend size (invoked when writing data to file
     */
    fun extendSize(newSize: Long) {
        if (size == -1L) return  // current size unknown => do nothing

        if (newSize > size) size = newSize
    }

    /**
     * Gets isDirectory. IsDirectory is a boolean indicating whether this is a directory
     * @return the isDirectory
     */
    var isDirectory: Boolean? = null
        private set

    /**
     * Mark file as newly created directory
     */
    fun markAsNewDirectory() {
        isDirectory = true
    }

    /**
     * Mark file as newly created file (of size 0)
     */
    fun markAsNewFile() {
        isDirectory = false
        size = 0
    }

    internal constructor(parent: File?, filename: String, attributes: SftpATTRS) : super(
        parent,
        filename
    ) {
        setAttrs(attributes)
    }

    fun setAttrs(attributes: SftpATTRS) {
        isDirectory = attributes.isDir
        this.sftpLastModified = attributes.mTime * 1000L
        size = attributes.getSize()
    }

    internal constructor(filename: String) : super(filename)

    companion object {
        /**
         * Make SftpFile that is a directory
         */
        @JvmStatic
        fun makeDirectory(filename: String): SftpFile {
            val dir = SftpFile(filename)
            dir.isDirectory = true
            dir.sftpLastModified = 0L
            return dir
        }
    }
}
