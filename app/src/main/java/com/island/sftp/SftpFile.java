package com.island.sftp;

import com.jcraft.jsch.SftpATTRS;
import java.io.File;

public class SftpFile extends File {
    private long lastModified;

    /**
     * Gets lastModified. LastModified is 
     * @return the lastModified
     */
    public long getSftpLastModified() {
	return lastModified;
    }

    private long size;

    /**
     * Gets size. Size is 
     * @return the size
     */
    public long getSize() {
	return size;
    }

    private boolean isDirectory;

    /**
     * Gets isDirectory. IsDirectory is 
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
