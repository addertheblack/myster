package com.myster.identity;

import com.general.util.Util;

/**
 * The {@code Cid128} is the equivalent of the {@link com.myster.type.MysterType} "short bytes"
 * concept but for Myster node identities. It is cryptographically derived from the node's RSA
 * public key (first 128 bits of SHA-256 of the encoded key), so it provides a compact,
 * collision-resistant handle for identity lookups without requiring a full public-key comparison.
 *
 * <p>Used in the MSD (Myster Secure Datagram) protocol as the {@code cid} field in Section 2,
 * and in {@link com.myster.access.AddMemberOp} to identify members of a private type's access
 * list.
 *
 * <p>128-bit truncated SHA-256.
 *
 * @see com.myster.identity.Util#generateCid(java.security.PublicKey)
 */
public record Cid128(byte[] bytes) {
    public static final int LENGTH = 16;

    public Cid128 {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException("Cid128 must be 16 bytes, got " + bytes.length);
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