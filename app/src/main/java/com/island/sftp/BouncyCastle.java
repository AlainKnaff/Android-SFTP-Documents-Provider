package com.island.sftp;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Reload sufficiently recent BouncyCastle library
 */
public abstract class BouncyCastle {

    static {
        // https://stackoverflow.com/questions/2584401/how-to-add-bouncy-castle-algorithm-to-android
        // By default Android _has_ bouncy castle, but an old version. Remove
        // that first
        //
        Security.removeProvider("BC");

        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    /**
     * Trigger method does nothing by itself, but loading the class and its
     * BouncyCastle provider.
     */
    public static void trigger() {
    }
}
