package com.island.androidsftpdocumentsprovider.provider;

import androidx.annotation.NonNull;
import android.database.ContentObserver;
import android.database.MatrixCursor;

/**
 * "Notifiable" Matrix cursor, with exposed onChange
 */
public class MC extends MatrixCursor implements RefreshableCursor {
    public static final String TAG="MC";

    private SFTPProvider provider;

    private String documentId;

    public MC(@NonNull SFTPProvider provider, @NonNull String documentId,
	      @NonNull String[] columns, int initialCapacity) {
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
