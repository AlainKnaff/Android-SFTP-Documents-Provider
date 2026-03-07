package com.island.androidsftpdocumentsprovider.provider

import android.database.Cursor

interface RefreshableCursor : Cursor {
    fun onChange(selfChange: Boolean)
}
