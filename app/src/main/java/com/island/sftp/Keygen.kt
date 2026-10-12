package com.island.sftp

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.island.sftp.BouncyCastle.trigger
import com.island.sftp.EcdsaConverter.convertToOpenSshPrivateKey
import com.island.util.FileUtil.Companion.copyTo
import com.island.util.FileUtil.Companion.readFile
import lu.knaff.alain.saf_sftp.R
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.crypto.util.PublicKeyFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
 Copyright (C) 2025,2026 Alain Knaff

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/ /**
 * Ssh key generator, as mentioned in https://codingtechroom.com/question/-generate-ssh-key-using-java-
 */
object Keygen {
    const val TAG: String = "Keygen"

    const val PRIVATE_KEY_FILE: String = "privateKey.pem"
    const val PUBLIC_KEY_FILE: String = "publicKey.txt"
    const val CERT_FILE: String = "user-cert.pub"

    fun genKey(ctx: Context, algo: String) {
        try {
            trigger()

            val keyGen = KeyPairGenerator.getInstance(algo, "BC")
            if ("ECDSA".equals(
                    algo,
                    ignoreCase = true
                )
            ) keyGen.initialize(ECGenParameterSpec("secp256r1"))
            val keyPair = keyGen.generateKeyPair()

            saveKeyToFile(
                ctx, PRIVATE_KEY_FILE,
                convertToPEM(keyPair.private, algo)
            )

            saveKeyToFile(
                ctx, PUBLIC_KEY_FILE,
                getEncodedSshPublicKey(ctx, keyPair.public)
            )
        } catch (e: IOException) {
            Log.e(TAG, "Error generating keys: " + e.message)
        } catch (e: NoSuchProviderException) {
            Log.e(TAG, "Error generating keys: " + e.message)
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "Error generating keys: " + e.message)
        } catch (e: InvalidAlgorithmParameterException) {
            Log.e(TAG, "Error generating keys: " + e.message)
        } catch (e: InvalidKeyException) {
            Log.e(TAG, "Error generating keys: " + e.message)
        }
    }

    @Throws(IOException::class)
    private fun saveKeyToFile(ctx: Context, fileName: String?, key: String?) {
        try {
            PrintWriter(ctx.openFileOutput(fileName, 0)).use { pw ->
                pw.println(key)
                println("$fileName saved successfully.")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error saving key to file: " + e.message)
            throw e
        }
    }

    private fun toBase64(bin: ByteArray?): String? {
        return Base64.encodeToString(bin, Base64.NO_WRAP)
    }

    @Throws(IOException::class, InvalidKeyException::class)
    fun convertToPEM(
        key: Key,
        algo: String
    ): String {
        val encodedKey: ByteArray?
        val type: String
        if ("RSA".equals(algo, ignoreCase = true)) {
            encodedKey = key.encoded
            type = ""
        } else if ("ECDSA".equals(algo, ignoreCase = true)) {
            encodedKey = convertToOpenSshPrivateKey(key as ECPrivateKey)
            type = "OPENSSH "
        } else {
            val bprv =
                PrivateKeyFactory.createKey(key.encoded)
            encodedKey = OpenSSHPrivateKeyUtil.encodePrivateKey(bprv)
            type = "OPENSSH "
        }
        val base64Key = toBase64(encodedKey)
        return "-----BEGIN " + type + "PRIVATE KEY-----\n" + base64Key + "\n-----END " + type + "PRIVATE KEY-----"
    }

    private fun getHostName(ctx: Context): String {
        var hostName: String?
        hostName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Global.getString(
            ctx.contentResolver,
            Settings.Global.DEVICE_NAME
        )
        else Build.MODEL
        if (hostName == null) hostName = "unknown"
        hostName = hostName.replace("[^a-zA-Z0-9_.-]+".toRegex(), "_")
        return hostName
    }

    // see https://linuxtut.com/en/ee3c7d0ba7d4610a9d21/ for
    // outputting public key
    @Throws(IOException::class)
    fun getEncodedSshPublicKey(
        ctx: Context,
        pKey: PublicKey
    ): String {
        val bpub =
            PublicKeyFactory.createKey(pKey.encoded)
        val publicKeyBytes = OpenSSHPublicKeyUtil.encodePublicKey(bpub)
        val sigLen = ByteBuffer
            .wrap(publicKeyBytes)
            .order(ByteOrder.BIG_ENDIAN)
            .getInt()
        val sig = String(publicKeyBytes, 4, sigLen)
        val publicKeyBase64 = toBase64(publicKeyBytes)

        val publicKeyEncoded = sig + " " + publicKeyBase64 +
                " sftp@" + getHostName(ctx)
        return publicKeyEncoded
    }

    fun haveKey(ctx: Context): Boolean {
        val privateKeyFilename = ctx.filesDir.toString() + "/" + PRIVATE_KEY_FILE
        return File(privateKeyFilename).exists()
    }

    fun readPrivateKey(ctx: Context): ByteArray? {
        return readFile(ctx, PRIVATE_KEY_FILE)
    }

    fun readPublicKey(ctx: Context): String? {
        try {
            BufferedReader(InputStreamReader(ctx.openFileInput(PUBLIC_KEY_FILE))).use { br ->
                return br.readLine()
            }
        } catch (_: FileNotFoundException) {
            Log.d(TAG, "File not found: $PUBLIC_KEY_FILE")
            return null
        } catch (e: IOException) {
            Log.e(TAG, "Exception while reading private key $e")
            return null
        }
    }

    fun shareKey(ctx: Context) {
        // Sharing intent
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.putExtra(
            Intent.EXTRA_SUBJECT,
            ctx
                .resources
                .getText(R.string.pubkey_subject)
        )
        shareIntent.putExtra(Intent.EXTRA_TEXT, readPublicKey(ctx))
        shareIntent.type = "text/plain"
        ctx.startActivity(Intent.createChooser(shareIntent, null))
    }

    fun readCertificate(context: Context): ByteArray? {
        return readFile(context, CERT_FILE)
    }

    @Throws(FileNotFoundException::class)
    fun storeCertificate(
        context: Context,
        `is`: InputStream
    ) {
        copyTo(`is`, context.openFileOutput(CERT_FILE, 0))

        // we really should attempt to validate the certificate here,
        // but we can't, see https://github.com/mwiede/jsch/issues/1161
    }
}
