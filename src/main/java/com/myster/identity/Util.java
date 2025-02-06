
package com.myster.identity;

import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

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
    
    
    //////////////////
}
