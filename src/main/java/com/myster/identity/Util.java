
package com.myster.identity;

import static com.myster.net.datagram.MSDConstants.CID_SIZE;

import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

import com.myster.net.datagram.DatagramEncryptUtil;

public class Util {
    public static String keyToString(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static Optional<PublicKey> publicKeyFromString(String publicKeyString) {
        byte[] encodedPublicKey = Base64.getDecoder().decode(publicKeyString);
    
        return publicKeyFromBytes(encodedPublicKey);
    }

    public static Optional<PublicKey> publicKeyFromBytes(byte[] encodedPublicKey) {
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedPublicKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return Optional.of(keyFactory.generatePublic(keySpec));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException exception) {
            exception.printStackTrace();
    
            return Optional.empty();
        }
    }

    public static byte[] generateNakedCid(PublicKey publicKey) {
        byte[] hash = DatagramEncryptUtil.hashBytes(publicKey.getEncoded());
        byte[] cid = new byte[CID_SIZE];
        System.arraycopy(hash, 0, cid, 0, CID_SIZE);
        return cid;
    }
    
    public static Cid128 generateCid(PublicKey publicKey) {
        return new Cid128(generateNakedCid(publicKey));
    }
}
