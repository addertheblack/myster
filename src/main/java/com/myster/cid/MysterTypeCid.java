package com.myster.cid;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Objects;

import com.general.util.Util;

/**
 * Immutable compact identity of a Myster type.
 *
 * <p>The 16-byte value is the MD5 digest of the encoded type public key. MD5 is
 * retained as part of the established Myster type identity format, not for new
 * cryptographic authentication.
 */
public final class MysterTypeCid {
    public static final int LENGTH = Cid128.LENGTH;

    private final Cid128 value;

    public MysterTypeCid(byte[] bytes) {
        value = new Cid128(bytes);
    }

    /**
     * Derives the established 16-byte type identity from an encoded public key.
     *
     * @param publicKey public key that defines the Myster type
     * @return the MD5-derived type CID
     */
    public static MysterTypeCid fromPublicKey(PublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey");
        try {
            return new MysterTypeCid(
                    MessageDigest.getInstance("MD5").digest(publicKey.getEncoded()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 algorithm must exist", exception);
        }
    }

    /**
     * Parses the fixed-width hexadecimal representation used by preferences and filenames.
     *
     * @param hex 32 hexadecimal characters representing 16 bytes
     * @return the parsed type CID
     * @throws IOException if the value is not valid hexadecimal or is not 16 bytes
     */
    public static MysterTypeCid fromHexString(String hex) throws IOException {
        try {
            return new MysterTypeCid(Util.fromHexString(hex));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid MysterType CID: " + hex, exception);
        }
    }

    /** @return a defensive copy of the 16-byte CID. */
    public byte[] bytes() {
        return value.bytes();
    }

    public String asHex() {
        return value.asHex();
    }

    @Override
    public boolean equals(Object object) {
        return this == object
                || object instanceof MysterTypeCid other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return asHex();
    }
}
