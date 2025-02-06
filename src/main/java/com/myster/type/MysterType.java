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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;

import com.general.util.Util; 

/**
 * This class represents a MysterType.
 * <p>
 * Is immutable!
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

