package com.myster.identity;

import com.general.util.Util;

/**
 * 128 bit sha-256 truncated
 */
public record Cid128(byte[] bytes) {
    public static final int LENGTH = 16;

    public Cid128 {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException("Sha256 must be 32 bytes, got " + bytes.length);
        }
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return (o instanceof Cid128 other) && java.util.Arrays.equals(this.bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(this.bytes);
    }
    
    public String asHex() {
        return Util.asHex(bytes);
    }
    
    @Override
    public String toString() {
        return Util.asHex(bytes);
    }
}