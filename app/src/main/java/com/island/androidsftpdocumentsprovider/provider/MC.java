package com.island.androidsftpdocumentsprovider.provider;

import android.net.Uri;
import android.database.ContentObserver;
import android.database.MatrixCursor;

/**
 * "Notifiable" Matrix cursor, with exposed onChange
 */
public class MC extends MatrixCursor {
    public static final String TAG="MC";

    private SFTPProvider provider;

    private Uri documentId;

    public MC(SFTPProvider provider, Uri documentId,
	      String[] columns, int initialCapacity) {
	super(columns, initialCapacity);
	this.documentId=documentId;
	this.provider=provider;
    }

    public void onChange(boolean selfChange) {
	super.onChange(selfChange);
    }

    public void registerContentObserver(ContentObserver observer) {
	super.registerContentObserver(observer);
	provider.registerCursor(this, documentId);
    }

    public void unregisterContentObserver(ContentObserver observer) {
	super.unregisterContentObserver(observer);
	provider.unregisterCursor(this, documentId);
    }
}
