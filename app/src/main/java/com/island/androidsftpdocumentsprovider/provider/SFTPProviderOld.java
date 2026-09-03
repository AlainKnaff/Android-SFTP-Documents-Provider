package com.island.androidsftpdocumentsprovider.provider;

import java.util.Arrays;
import android.util.Log;
import android.database.Cursor;
import android.database.MatrixCursor;

public class SFTPProviderOld extends SFTPProvider {
    public static final String TAG="SFTPProviderOld";

    @Override
    public Cursor queryRoots(String[]projection)
    {
	Log.d(TAG,String.format("queryRoots %s", Arrays.toString(projection)));
	return new MatrixCursor(resolveRootProjection(projection));
    }
}
