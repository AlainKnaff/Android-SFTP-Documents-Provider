package com.island.sftp;

import java.io.File;
import com.jcraft.jsch.SftpATTRS;

public class SftpFile extends File {
    private long lastModified;

    /**
     * Gets lastModified. LastModified is the last modified time of file
     * @return the lastModified
     */
    public long getSftpLastModified() {
	return lastModified;
    }

    private long size;

    /**
     * Gets size. Size is the size of the file
     * @return the size
     */
    public long getSize() {
	return size;
    }

    private boolean isDirectory;

    /**
     * Gets isDirectory. IsDirectory is a boolean indicating whether this is a directory
     * @return the isDirectory
     */
    public boolean getIsDirectory() {
	return isDirectory;
    }

    public SftpFile(File parent, String filename, SftpATTRS attributes) {
	super(parent, filename);
	isDirectory=attributes.isDir();
	lastModified=attributes.getMTime()*1000L;
	size=attributes.getSize();
    }
}
