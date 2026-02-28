/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2003
 */

package com.myster.type;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;

import com.general.util.Util; 

/**
 * This class represents a MysterType.
 * <p>
 * Is immutable! MysterType is based on a public key, but only stores a short hash of the key for compactness.
 * The short hash varient of the MysterType is computed using MD5 of the public key's encoded bytes.
 */
public final class MysterType {
    private final byte[] shortBytes;

    public MysterType(PublicKey key) {
        this(toShortBytes(key));
    }
    
    public MysterType(byte[] shortBytes) {
        this.shortBytes = shortBytes.clone();
    }

    public byte[] toBytes() {
        return this.shortBytes.clone();
    }
    
    public String toHexString() {
        return Util.asHex(shortBytes);
    }

    /**
     * Parses a {@link MysterType} from its hex string representation (as produced by
     * {@link #toHexString()}). Used when reconstructing types from stored prefs node names.
     *
     * @param hex the hex string to parse
     * @return the corresponding MysterType
     * @throws IllegalArgumentException if the string is not valid hex or has the wrong length
     */
    public static MysterType fromHexString(String hex) throws IOException {
        try {
            byte[] bytes = Util.fromHexString(hex);
            if (bytes.length != 16) {
                throw new IOException(
                    "MysterType hex string must be 32 characters (16 bytes), got: " + hex);
            }
            return new MysterType(bytes);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid hex string for MysterType: " + hex, e);
        }
    }

    public String toString() {
        return toHexString();
    }
    
    public static byte[] toShortBytes(PublicKey key) {
        try {
            // Compute the MD5 digest of the bytes
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(key.getEncoded());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algorythm should exist but does not", ex);
        }
    }

    public boolean equals(Object o) {
        if (o instanceof MysterType other) {
           return Arrays.equals(shortBytes, other.shortBytes);
        }
        
        return false;
    }

    public int hashCode() {
        return Arrays.hashCode(shortBytes);
    }
}

