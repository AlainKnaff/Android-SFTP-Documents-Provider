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

    private Boolean isDirectory=null;

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
