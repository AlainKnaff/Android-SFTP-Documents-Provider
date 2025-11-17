package com.island.androidsftpdocumentsprovider.account;

public class Account {
    // variables for our account,
    // hostname, port, username and password
    private int id;

    private String name;
    private String hostName;
    private int port;

    private String userName;
    private String password;

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


    public int getId() {
	return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPort() {
	return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    // constructor
    public Account() {
    }

    public Account(int id,
		   String name,
		   String hostName,
		   int port,
		   String userName,
		   String password) {
	this.id=id;
	this.name=name;
	this.hostName=hostName;
	this.port=port;
	this.userName=userName;
	this.password=password;
    }
}
