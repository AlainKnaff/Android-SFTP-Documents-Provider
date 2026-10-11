package com.island.androidsftpdocumentsprovider.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log

class SFTPProviderOld : SFTPProvider() {
    override fun queryRoots(projection: Array<String>): Cursor {
        Log.d(TAG, String.format("queryRoots %s", projection.contentToString()))
        return MatrixCursor(resolveRootProjection(projection))
    }

    companion object {
        const val TAG: String = "SFTPProviderOld"
    }
}
