package com.island.sftp

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil
import org.bouncycastle.util.Arrays
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.interfaces.ECPrivateKey
import java.util.Random

// https://stackoverflow.com/questions/79262968/how-to-convert-a-ecprivatekey-to-a-pem-encoded-openssh-format
object EcdsaConverter {
    // supports ECDSA for nistp256, nistp384 and nistp521
    @JvmStatic
    @Throws(InvalidKeyException::class)
    fun convertToOpenSshPrivateKey(ecPrivateKey: ECPrivateKey): ByteArray {
        // get keytype, pub0

        val keyType = getKeyType(ecPrivateKey).toByteArray(StandardCharsets.UTF_8)
        val pub0 = Arrays.copyOfRange(keyType, keyType.size - 8, keyType.size)

        // get raw private key and calculate raw public key
        val rawPrivateKey = ecPrivateKey.s.toByteArray() // signed byte array
        val g =
            (ECUtil.generatePrivateKeyParameter(ecPrivateKey) as ECPrivateKeyParameters).parameters
                .g
        val uncompressedPublicKey = g.multiply(ecPrivateKey.s).getEncoded(false)

        // public data
        val publicDataSize =
            4 + keyType.size +  // 32-bit length, keytype
                    4 + pub0.size +  // 32-bit length, pub0
                    4 + uncompressedPublicKey.size // 32-bit length, pub1
        val publicData = ByteBuffer.allocate(publicDataSize)
        publicData.putInt(keyType.size)
        publicData.put(keyType)
        publicData.putInt(pub0.size)
        publicData.put(pub0)
        publicData.putInt(uncompressedPublicKey.size)
        publicData.put(uncompressedPublicKey)

        // private data
        val privateDataSizeUnpadded =
            8 +  // 64-bit dummy checksum
                    publicData.position() +  // public key parts
                    4 + rawPrivateKey.size +  // 64-bit dummy checksum
                    4 // 32-bit length, comment
        val paddingSize = (8 - privateDataSizeUnpadded % 8) % 8
        val privateDataSize =
            privateDataSizeUnpadded +  // private data, unpadded
                    paddingSize // padding
        val privateData = ByteBuffer.allocate(privateDataSize)
        val checksum = Random().nextInt()
        privateData.putInt(checksum)
        privateData.putInt(checksum)
        privateData.put(publicData.array(), 0, publicData.position())
        privateData.putInt(rawPrivateKey.size)
        privateData.put(rawPrivateKey)
        privateData.putInt(0)
        val paddingBlock = byteArrayOf(1, 2, 3, 4, 5, 6, 7) // no cipher: blocksize: 8
        val padding = Arrays.copyOfRange(paddingBlock, 0, paddingSize)
        privateData.put(padding)

        // all data
        val prefix = "openssh-key-v1\u0000".toByteArray(StandardCharsets.UTF_8)
        val none = "none".toByteArray(StandardCharsets.UTF_8)
        val allDataSize =
            prefix.size +  // "openssh-key-v1"0x00
                    4 + none.size +  // 32-bit length, "none"
                    4 + none.size +  // 32-bit length, "none"
                    4 +  // 32-bit length, nil
                    4 +  // 32-bit 0x01
                    4 + publicData.position() +  // 32-bit length, public key parts
                    4 + privateData.position() // 32-bit length, private key parts
        val allData = ByteBuffer.allocate(allDataSize)
        allData.put(prefix)
        allData.putInt(none.size)
        allData.put(none)
        allData.putInt(none.size)
        allData.put(none)
        allData.putInt(0)
        allData.putInt(1)
        allData.putInt(publicData.position())
        allData.put(publicData.array(), 0, publicData.position())
        allData.putInt(privateData.position())
        allData.put(privateData.array(), 0, privateData.position())

        return allData.array()
    }

    private fun getKeyType(ecPrivateKey: ECPrivateKey): String {
        val pki = PrivateKeyInfo.getInstance(ASN1Sequence.getInstance(ecPrivateKey.encoded))
        val oid = ASN1ObjectIdentifier.getInstance(pki!!.   privateKeyAlgorithm.parameters)
        return if (oid.equals(ASN1ObjectIdentifier("1.2.840.10045.3.1.7"))) {
            "ecdsa-sha2-nistp256"
        } else if (oid.equals(ASN1ObjectIdentifier("1.3.132.0.34"))) {
            "ecdsa-sha2-nistp384"
        } else if (oid.equals(ASN1ObjectIdentifier("1.3.132.0.35"))) {
            "ecdsa-sha2-nistp521"
        } else {
            throw RuntimeException("key type $oid not defined")
        }
    }
}
