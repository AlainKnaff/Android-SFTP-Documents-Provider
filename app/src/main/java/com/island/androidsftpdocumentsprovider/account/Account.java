package com.island.androidsftpdocumentsprovider.account;

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
 Copyright (C) 2025      Alain Knaff

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

public class Account {
    // variables for our account,
    // hostname, port, username and password
    private int id;

    private String name;
    private String hostName;
    private int port;

    private String userName;
    private String password;

    private String directory;

    public int getId() {
	return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // creating getter and setter methods
    public String getName() {
	return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHostName() {
	return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getPort() {
	return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUserName() {
	return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
	return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Gets directory. Directory is home directory of user
     * @return the directory
     */
    public String getDirectory() {
	return directory;
    }

    /**
     * Sets directory. Directory is home directory of user
     * @param directory the directory
     */
    public void setDirectory(String directory) {
	this.directory = directory;
    }

    // constructor
    public Account() {
    }

    public Account(int id,
		   String name,
		   String hostName,
		   int port,
		   String userName,
		   String password,
		   String directory) {
	this.id=id;
	this.name=name;
	this.hostName=hostName;
	this.port=port;
	this.userName=userName;
	this.password=password;
	this.directory=directory;
    }
}
