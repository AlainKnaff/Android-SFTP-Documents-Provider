package com.island.androidsftpdocumentsprovider.account;

import java.util.List;
import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHandler extends SQLiteOpenHelper {

    // creating a constant variables for our database.
    // below variable is for our database name.
    private static final String DB_NAME = "roots";

    // below int is our database version
    private static final int DB_VERSION = 2;

    // below variable is for our table name.
    private static final String TABLE_NAME = "roots";

    // below variable is for our id column.
    public static final String ID_COL = "id";

    // below variable is for name column
    public static final String NAME_COL = "name";

    // below variable is for the host name column.
    public static final String HOST_NAME_COL = "host_name";

    // below variable is for the port column.
    public static final String PORT_COL = "port";

    // below variable is for the user name column.
    public static final String USER_NAME_COL = "user_name";

    // below variable is for the password column.
    public static final String PASSWORD_COL = "password";

    // below variable is for the directory column.
    public static final String DIRECTORY_COL = "directory";

    // creating a constructor for our database handler.
    public DBHandler(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // below method is for creating a database by running a sqlite query
    @Override
    public void onCreate(SQLiteDatabase db) {
        // on below line we are creating
        // an sqlite query and we are
        // setting our column names
        // along with their data types.
        String query = "CREATE TABLE " + TABLE_NAME + " ("
                +        ID_COL + " INTEGER PRIMARY KEY AUTOINCREMENT "
                + ", " + NAME_COL + " TEXT"
                + ", " + HOST_NAME_COL + " TEXT NOT NULL"
                + ", " + PORT_COL + " INTEGER NOT NULL"
                + ", " + USER_NAME_COL + " TEXT"
		+ ", " + PASSWORD_COL + " TEXT"
		+ ", " + DIRECTORY_COL + " TEXT NOT NULL DEFAULT ''"
	    	+ " )";

        // at last we are calling a exec sql
        // method to execute above sql query
        db.execSQL(query);
    }

    /**
     * this method is called to perform any schema changes due to upgrade
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	if(oldVersion >= newVersion)
	    return;

	if(oldVersion == 1) {
	    String query = "ALTER TABLE " + TABLE_NAME +
		" ADD COLUMN "+DIRECTORY_COL + " TEXT NOT NULL DEFAULT ''";
	    db.execSQL(query);
	}
    }

    private void storeValues(ContentValues values,
			     String name, String hostName, int port,
			     String userName, String password,
			     String directory) {
        values.put(NAME_COL, name);
        values.put(HOST_NAME_COL, hostName);
        values.put(PORT_COL, port);
        values.put(USER_NAME_COL, userName);
        values.put(PASSWORD_COL, password);
        values.put(DIRECTORY_COL, directory);
    }

    // this method is use to add new course to our sqlite database.
    public void addNewAccount(String name, String hostName, int port,
			      String userName, String password,
			      String directory) {

        // on below line we are creating a variable for
        // our sqlite database and calling writable method
        // as we are writing data in our database.
        try(SQLiteDatabase db = this.getWritableDatabase()) {

	    // on below line we are creating a
	    // variable for content values.
	    ContentValues values = new ContentValues();

	    storeValues(values,
			name, hostName, port,
			userName, password, directory);

	    // after adding all values we are passing
	    // content values to our table.
	    db.insert(TABLE_NAME, null, values);
	}
    }

    // below is the method for updating our account
    public void updateAccount(int id,
			      String name, String hostName, int port,
			      String userName, String password,
			      String directory) {

        // calling a method to get writable database.
	try(SQLiteDatabase db = this.getWritableDatabase()) {
	    ContentValues values = new ContentValues();

	    storeValues(values,
			name, hostName, port,
			userName, password, directory);

	    // on below line we are calling a update method to update our
	    // database and passing our values. and we are comparing it
	    // with name of our account which is stored in id variable.
	    db.update(TABLE_NAME, values, ID_COL+"=?",
		      new String[]{String.valueOf(id)});
	}
    }

    // below is the method for deleting our account.
    public void removeAccount(int id) {
        // on below line we are creating
        // a variable to write our database.
        try(SQLiteDatabase db = this.getWritableDatabase()) {
	    // on below line we are calling a method to delete our
	    // account and we are comparing it with our account id.
	    db.delete(TABLE_NAME, ID_COL+"=?", new String[]{String.valueOf(id)});
	}
    }

    // we have created a new method for reading all the accounts.
    // Needs to be synchronized due to possible reference counting bug
    // around cursor:
    // https://stackoverflow.com/questions/23293572/android-cannot-perform-this-operation-because-the-connection-pool-has-been-clos
    private synchronized List<Account> readAccounts(String whereClause,
						    String[] params)
    {
	// on below line we are creating a
	// database for reading our database.
	try(SQLiteDatabase db = this.getReadableDatabase();

	    // on below line we are creating a cursor with query to
	    // read data from database.
	    Cursor cursor = db.rawQuery("SELECT "+ID_COL+
					","+NAME_COL+
					","+HOST_NAME_COL+
					","+PORT_COL+
					","+USER_NAME_COL+
					","+PASSWORD_COL+
					","+DIRECTORY_COL+
					" FROM " + TABLE_NAME + " "  +
					whereClause,
					params)) {

	    // on below line we are creating a new array list.
	    ArrayList<Account> accountArrayList  = new ArrayList<>();

	    // moving our cursor to first position.
	    if (cursor.moveToFirst()) {
		do {
		    // on below line we are adding the data from
		    // cursor to our array list.
		    accountArrayList.add(new Account(cursor.getInt(0),
						     cursor.getString(1),
						     cursor.getString(2),
						     cursor.getInt(3),
						     cursor.getString(4),
						     cursor.getString(5),
						     cursor.getString(6)
						     ));
		} while (cursor.moveToNext());
		// moving our cursor to next.
	    }
	    // at last closing our cursor
	    // and returning our array list.
	    return accountArrayList;
	}
    }

    public Account readAccountById(int id) {
	List<Account> accounts = readAccounts("WHERE "+ID_COL+" = ?",
					      new String[] { String.valueOf(id) });
	if(accounts.size() > 0)
	    return accounts.get(0);
	else
	    return null;
    }

    public Account readAccountByName(String name) {
	List<Account> accounts = readAccounts("WHERE "+NAME_COL+" = ?",
					      new String[] { name });
	if(accounts.size() > 0)
	    return accounts.get(0);
	else
	    return null;
    }

    public List<Account> readAccounts() {
	return readAccounts("", null);
    }
}
