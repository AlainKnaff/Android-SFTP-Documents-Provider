package com.island.androidsftpdocumentsprovider.provider;

import java.util.Arrays;
import android.util.Log;
import android.database.Cursor;
import android.database.MatrixCursor;

public class SFTPProviderOld extends SFTPProvider {

    @Override
    public Cursor queryRoots(String[]projection)
    {
	Log.d(SFTPProvider.TAG,String.format("SFTPProviderOld queryRoots %s",Arrays.toString(projection)));
	return new MatrixCursor(resolveRootProjection(projection));
    }
}
