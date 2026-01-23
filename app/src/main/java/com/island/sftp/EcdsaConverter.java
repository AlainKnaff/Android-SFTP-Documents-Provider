package com.island.sftp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPrivateKey;
import java.util.Random;

import  java.security.InvalidKeyException;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;

// https://stackoverflow.com/questions/79262968/how-to-convert-a-ecprivatekey-to-a-pem-encoded-openssh-format

public abstract class EcdsaConverter {
    // supports ECDSA for nistp256, nistp384 and nistp521
    public static byte[] convertToOpenSshPrivateKey(ECPrivateKey ecPrivateKey)
        throws InvalidKeyException
    {

        // get keytpe, pub0
        byte[] keyType = getKeyType(ecPrivateKey).getBytes(StandardCharsets.UTF_8);
        byte[] pub0 = Arrays.copyOfRange(keyType, keyType.length - 8, keyType.length);

        // get raw private key and calculate raw public key
        byte[] rawPrivateKey =  ecPrivateKey.getS().toByteArray(); // signed byte array
        ECPoint g = ((ECPrivateKeyParameters)ECUtil.generatePrivateKeyParameter(ecPrivateKey)).getParameters().getG();
        byte[] uncompressedPublicKey = g.multiply(ecPrivateKey.getS()).getEncoded(false);

        // public data
        int publicDataSize =
                4 + keyType.length +                // 32-bit length, keytype
                4 + pub0.length +                   // 32-bit length, pub0
                4 + uncompressedPublicKey.length;   // 32-bit length, pub1
        ByteBuffer publicData = ByteBuffer.allocate(publicDataSize);
        publicData.putInt(keyType.length); publicData.put(keyType);
        publicData.putInt(pub0.length); publicData.put(pub0);
        publicData.putInt(uncompressedPublicKey.length); publicData.put(uncompressedPublicKey);

        // private data
        int privateDataSizeUnpadded =
                8 +                                 // 64-bit dummy checksum
                publicData.position() +             // public key parts
                4 + rawPrivateKey.length +          // 64-bit dummy checksum
                4;                                  // 32-bit length, comment
        int paddingSize = (8 - privateDataSizeUnpadded % 8) % 8;
        int privateDataSize =
                privateDataSizeUnpadded +           // private data, unpadded
                paddingSize;                        // padding
        ByteBuffer privateData = ByteBuffer.allocate(privateDataSize);
        int chcksm = new Random().nextInt();
        privateData.putInt(chcksm); privateData.putInt(chcksm);
        privateData.put(publicData.array(), 0, publicData.position());
        privateData.putInt(rawPrivateKey.length); privateData.put(rawPrivateKey);
        privateData.putInt(0);
        byte[] paddingBlock = new byte[] {1,2,3,4,5,6,7}; // no cipher: blocksize: 8
        byte[] padding = Arrays.copyOfRange(paddingBlock, 0, paddingSize);
        privateData.put(padding);

        // all data
        byte[] prefix = "openssh-key-v1\0".getBytes(StandardCharsets.UTF_8);
        byte[] none = "none".getBytes(StandardCharsets.UTF_8);
        int allDataSize =
                prefix.length +                     // "openssh-key-v1"0x00
                4 + none.length +                   // 32-bit length, "none"
                4 + none.length +                   // 32-bit length, "none"
                4 +                                 // 32-bit length, nil
                4 +                                 // 32-bit 0x01
                4 + publicData.position() +         // 32-bit length, public key parts
                4 + privateData.position();         // 32-bit length, private key parts
        ByteBuffer allData = ByteBuffer.allocate(allDataSize);
        allData.put(prefix);
        allData.putInt(none.length); allData.put(none);
        allData.putInt(none.length); allData.put(none);
        allData.putInt(0);
        allData.putInt(1);
        allData.putInt(publicData.position()); allData.put(publicData.array(), 0, publicData.position());
        allData.putInt(privateData.position()); allData.put(privateData.array(), 0, privateData.position());

        return allData.array();
    }

    private static String getKeyType(ECPrivateKey ecPrivateKey) {
        PrivateKeyInfo pki = PrivateKeyInfo.getInstance(ASN1Sequence.getInstance(ecPrivateKey.getEncoded()));
        ASN1ObjectIdentifier oid = ASN1ObjectIdentifier.getInstance(pki.getPrivateKeyAlgorithm().getParameters());
        if (oid.equals(new ASN1ObjectIdentifier("1.2.840.10045.3.1.7"))) {
            return "ecdsa-sha2-nistp256";
        } else if (oid.equals(new ASN1ObjectIdentifier("1.3.132.0.34"))) {
            return "ecdsa-sha2-nistp384";
        } else if (oid.equals(new ASN1ObjectIdentifier("1.3.132.0.35"))) {
            return "ecdsa-sha2-nistp521";
        } else {
            throw new RuntimeException("key type "+oid+" not defined");
        }
    }
}
