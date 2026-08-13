package com.myster.cid;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable compact identity of a public-key-backed Myster server.
 *
 * <p>The value is the first 16 bytes of SHA-256 over the encoded server public key. Server CIDs
 * are ordered as unsigned big-endian integers for tracker and 3DNS ring operations. Bit index
 * {@code 0} is the least-significant bit and index {@code 127} is the most-significant bit.
 */
public final class ServerCid implements Comparable<ServerCid> {
    public static final int LENGTH = Cid128.LENGTH;

    private final Cid128 value;

    public ServerCid(byte[] bytes) {
        value = new Cid128(bytes);
    }

    /**
     * Derives a server identity from its public key.
     *
     * @param publicKey server public key
     * @return the first 16 bytes of SHA-256 over the encoded key
     */
    public static ServerCid fromPublicKey(PublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return new ServerCid(Arrays.copyOf(hash, LENGTH));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm must exist", exception);
        }
    }

    /** @return a defensive copy of the 16-byte CID. */
    public byte[] bytes() {
        return value.bytes();
    }

    /**
     * Adds {@code 2^bitIndex} in the unsigned 128-bit ring.
     *
     * @param bitIndex bit to add, in the inclusive range 0 through 127
     * @return the wrapped server CID
     */
    public ServerCid plusPowerOfTwo(int bitIndex) {
        return new ServerCid(value.plusPowerOfTwo(bitIndex).bytes());
    }

    /** Compares two server CIDs by predecessor-side distance to this target. */
    public int comparePredecessorDistance(ServerCid a, ServerCid b) {
        return value.comparePredecessorDistance(a.value, b.value);
    }

    /** Compares two server CIDs by successor-side distance to this target. */
    public int compareSuccessorDistance(ServerCid a, ServerCid b) {
        return value.compareSuccessorDistance(a.value, b.value);
    }

    @Override
    public int compareTo(ServerCid other) {
        return value.compareTo(other.value);
    }

    public String asHex() {
        return value.asHex();
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof ServerCid other && value.equals(other.value);
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
