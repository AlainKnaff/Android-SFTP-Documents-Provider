package com.island.sftp

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Reload sufficiently recent BouncyCastle library
 */
object BouncyCastle {
    init {
        // https://stackoverflow.com/questions/2584401/how-to-add-bouncy-castle-algorithm-to-android
        // By default Android _has_ bouncy castle, but an old version. Remove
        // that first
        //
        Security.removeProvider("BC")

        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    /**
     * Trigger method does nothing by itself, but loading the class and its
     * BouncyCastle provider.
     */
    @JvmStatic
    fun trigger() {
    }
}
