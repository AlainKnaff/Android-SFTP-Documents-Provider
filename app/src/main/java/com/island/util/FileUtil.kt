package com.island.util

import java.io.OutputStream
import java.io.InputStream
import java.io.FileNotFoundException
import android.content.Context

class FileUtil {
    companion object {
        @JvmStatic
        fun readFile(context: Context, fileName: String): ByteArray? {
            try {
                context.openFileInput(fileName).use {
                    return it.readBytes()
                }
            } catch(e: FileNotFoundException) {
                return null
            }
        }

        @JvmStatic
        fun copyTo(istr: InputStream, os: OutputStream) {
            istr.copyTo(os)
        }
    }
}
