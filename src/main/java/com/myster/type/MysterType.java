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
import java.security.PublicKey;

import com.myster.cid.MysterTypeCid;

/**
 * This class represents a MysterType.
 * <p>
 * Immutable Myster type backed by its 16-byte {@link MysterTypeCid}. The compact identity is
 * computed using MD5 of the encoded type public key for compatibility with the Myster type format.
 */
public final class MysterType {
    private final MysterTypeCid cid;

    public MysterType(PublicKey key) {
        this(MysterTypeCid.fromPublicKey(key));
    }
    
    public MysterType(byte[] shortBytes) {
        this(new MysterTypeCid(shortBytes));
    }

    private MysterType(MysterTypeCid cid) {
        this.cid = cid;
    }

    public byte[] toBytes() {
        return cid.bytes();
    }
    
    public String toHexString() {
        return cid.asHex();
    }

    /**
     * Parses a {@link MysterType} from its hex string representation (as produced by
     * {@link #toHexString()}). Used when reconstructing types from stored prefs node names.
     *
     * @param hex the hex string to parse
     * @return the corresponding MysterType
     * @throws IOException if the string is not valid hexadecimal or does not contain 16 bytes
     */
    public static MysterType fromHexString(String hex) throws IOException {
        return new MysterType(MysterTypeCid.fromHexString(hex));
    }

    public String toString() {
        return toHexString();
    }
    
    public boolean equals(Object o) {
        if (o instanceof MysterType other) {
           return cid.equals(other.cid);
        }
        
        return false;
    }

    public int hashCode() {
        return cid.hashCode();
    }
}
