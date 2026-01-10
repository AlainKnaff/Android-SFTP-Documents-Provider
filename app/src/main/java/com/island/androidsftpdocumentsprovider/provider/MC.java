package com.island.androidsftpdocumentsprovider.provider;

import android.net.Uri
import android.database.ContentObserver;
import android.database.MatrixCursor;
import android.util.Log;

/**
 * "Notifiable" Matrix cursor, with exposed onChange
 */
public class MC extends MatrixCursor {
    public static final String TAG="MC";

    private SFTPProvider provider;
    
    public MC(SFTPProvider provider, Uri documentId,
	      String[] columns, int initialCapacity) {
	super(columns, size);
	this.documentId=documentId;
	this.provider=provider;
    }

    public void onChange(boolean selfChange) {
	Log.i(TAG, "onChange called on "+this+" with "+selfChange);
	super.onChange(selfChange);
    }

    public void registerContentObserver(ContentObserver observer) {
	super.registerContentObserver(observer);
	provider.registerCursor(this, uri);
	Log.i(TAG, "observer "+observer+" registered on "+this);
    }

    public void unregisterContentObserver(ContentObserver observer) {
	super.unregisterContentObserver(observer);
	provider.unregisterCursor(this, uri);
	Log.i(TAG, "observer "+observer+" unregistered on "+this);
    }
}
