package com.island.androidsftpdocumentsprovider.provider

import lu.knaff.alain.saf_sftp.R;
import android.app.Activity;
import android.net.Uri;
import android.provider.DocumentsContract;

/**
 * Common activity superclass with helpful methods for documents providers
 */
abstract class ProviderActivity : Activity() {
    fun getAuthority(): String {
	return getString(R.string.authority)
    }

    fun notifyChange(mode: Int) {
	val uri:Uri = DocumentsContract.buildRootsUri(getAuthority());
	contentResolver.notifyChange(uri, null, mode);
    }
}
