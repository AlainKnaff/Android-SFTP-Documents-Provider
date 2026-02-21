package com.island.sftp;

import java.io.File;
import com.jcraft.jsch.SftpATTRS;

public class SftpFile extends File {
    private long lastModified=-1;

    /**
     * Gets lastModified. LastModified is the last modified time of file
     * @return the lastModified
     */
    public long getSftpLastModified() {
	return lastModified;
    }

    private long size=-1;

    /**
     * Gets size. Size is the size of the file
     * @return the size
     */
    public long getSize() {
	return size;
    }

    /**
     * Set size to 0 (invoked when writing file in truncate mode
     */
    public void truncateSize() {
        size=0;
    }

    /**
     * Extend size (invoked when writing data to file
     */
    public void extendSize(long newSize) {
        if(size==-1)
            return; // current size unknown => do nothing
        if(newSize > size)
            size = newSize;
    }

    private Boolean isDirectory=null;

    /**
     * Mark file as newly created directory
     */
    public void markAsNewDirectory() {
        isDirectory = true;
    }

    /**
     * Mark file as newly created file (of size 0)
     */
    public void markAsNewFile() {
        isDirectory = false;
        size = 0;
    }

    /**
     * Gets isDirectory. IsDirectory is a boolean indicating whether this is a directory
     * @return the isDirectory
     */
    public Boolean getIsDirectory() {
	return isDirectory;
    }

    SftpFile(File parent, String filename, SftpATTRS attributes) {
	super(parent, filename);
        setAttrs(attributes);
    }
    void setAttrs(SftpATTRS attributes) {
	isDirectory=attributes.isDir();
	lastModified=attributes.getMTime()*1000L;
	size=attributes.getSize();
    }
    SftpFile(String filename) {
        super(filename);
    }
}
