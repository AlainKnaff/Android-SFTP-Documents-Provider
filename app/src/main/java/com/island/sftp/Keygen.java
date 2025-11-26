package com.island.sftp;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import java.security.interfaces.RSAPublicKey;
import java.nio.charset.StandardCharsets;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

import android.content.Intent;
import android.content.Context;
import android.util.Log;
import android.util.Base64;

import com.island.androidsftpdocumentsprovider.R;

/**
 * Ssh keygen, as mentioned in https://codingtechroom.com/question/-generate-ssh-key-using-java-
 */
public class Keygen {
    public static final String TAG="Keygen";

    public static final String PRIVATE_KEY_FILE="privateKey.pem";
    public static final String PUBLIC_KEY_FILE="publicKey.txt";

    public static void genKey(Context ctx) {
        try {
	    // confirmation dialog if it already exists:
	    // https://stackoverflow.com/questions/5127407/how-to-implement-a-confirmation-yes-no-dialogpreference

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            saveKeyToFile(ctx, PRIVATE_KEY_FILE, convertToPEM(keyPair.getPrivate(), "PRIVATE"));
            saveKeyToFile(ctx, PUBLIC_KEY_FILE, getEncodedSshPublicKey( (PublicKey) keyPair.getPublic()));

        } catch (NoSuchAlgorithmException e) {
	    Log.e(TAG, "Error generating keys: " + e.getMessage());
        }
    }

    private static void saveKeyToFile(Context ctx, String fileName, String key) {
        try(PrintWriter pw = new PrintWriter(ctx.openFileOutput(fileName, 0))) {
            pw.println(key);
            System.out.println(fileName + " saved successfully.");
        } catch (IOException e) {
	    Log.e(TAG, "Error saving key to file: " + e.getMessage());
        }
    }

    private static String toBase64(byte[] bin) {
	return Base64.encodeToString(bin, Base64.NO_WRAP);
    }

    public static String convertToPEM(Key key, String type) {
        byte[] encodedKey = key.getEncoded();
        String base64Key = toBase64(encodedKey);
        return "-----BEGIN "+type+" KEY-----\n" + base64Key + "\n-----END "+type+" KEY-----";
    }

    // see https://linuxtut.com/en/ee3c7d0ba7d4610a9d21/ for
    // outputting public key
    public static String getEncodedSshPublicKey(final PublicKey pKey) {
        final String sig = "ssh-rsa";

	RSAPublicKey publicKey = (RSAPublicKey) pKey;
        final byte[] sigBytes = sig.getBytes(StandardCharsets.US_ASCII);
        final byte[] eBytes = publicKey.getPublicExponent().toByteArray();
        final byte[] nBytes = publicKey.getModulus().toByteArray();

        final int size = 4 + sigBytes.length
                + 4 + eBytes.length
                + 4 + nBytes.length;

        final byte[] publicKeyBytes = ByteBuffer.allocate(size)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(sigBytes.length).put(sigBytes)
                .putInt(eBytes.length).put(eBytes)
                .putInt(nBytes.length).put(nBytes)
                .array();

        final String publicKeyBase64 = toBase64(publicKeyBytes);

        final String publicKeyEncoded = sig + " " + publicKeyBase64 + " user@sftpprovider";
        return publicKeyEncoded;
    }

    public static String readPrivateKey(Context ctx) {
	return ctx.getFilesDir()+"/"+PRIVATE_KEY_FILE;
    }


    public static String readPublicKey(Context ctx) {
	try(BufferedReader br =
	    new BufferedReader(new InputStreamReader(ctx.openFileInput(PUBLIC_KEY_FILE)))) {
	    return br.readLine();
	} catch(FileNotFoundException e) {
	    Log.d(TAG, "File not found: "+PUBLIC_KEY_FILE);
	    return null;
	} catch(IOException e) {
	    Log.e(TAG, "Exception while reading private key "+e);
	    return null;
	}
    }

    public static void shareKey(Context ctx) {
	// Sharing intent
	Intent shareIntent = new Intent();
	shareIntent.setAction(Intent.ACTION_SEND);
	shareIntent.putExtra(Intent.EXTRA_SUBJECT,
			     ctx
			     .getResources()
			     .getText(R.string.pubkey_subject));
	shareIntent.putExtra(Intent.EXTRA_TEXT, readPublicKey(ctx));
	shareIntent.setType("text/plain");
	ctx.startActivity(Intent.createChooser(shareIntent,null));
    }
}
