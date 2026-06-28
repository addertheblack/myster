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
 * <p>128-bit truncated SHA-256. CIDs are ordered as unsigned big-endian
 * 128-bit integers. Bit index {@code 0} is the least-significant bit and bit
 * index {@code 127} is the most-significant bit; positive offset arithmetic
 * wraps modulo {@code 2^128}.
 *
 * @see com.myster.identity.Util#generateCid(java.security.PublicKey)
 */
public final class Cid128 implements Comparable<Cid128> {
    public static final int LENGTH = 16;

    private final long hi;
    private final long lo;

    public Cid128(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException("Cid128 must be 16 bytes, got " + bytes.length);
        }

        this.hi = longFromBytes(bytes, 0);
        this.lo = longFromBytes(bytes, Long.BYTES);
    }

    private Cid128(long hi, long lo) {
        this.hi = hi;
        this.lo = lo;
    }

    /**
     * @return the fixed-width 16-byte big-endian serialization of this CID.
     */
    public byte[] bytes() {
        byte[] bytes = new byte[LENGTH];
        writeLong(bytes, 0, hi);
        writeLong(bytes, Long.BYTES, lo);
        return bytes;
    }

    /**
     * Adds {@code 2^bitIndex} in the unsigned 128-bit ring.
     *
     * @param bitIndex bit to add, where {@code 0} is the least-significant bit
     *        and {@code 127} is the most-significant bit
     * @return the wrapped CID value
     */
    public Cid128 plusPowerOfTwo(int bitIndex) {
        if (bitIndex < 0 || bitIndex >= LENGTH * Byte.SIZE) {
            throw new IllegalArgumentException("bitIndex must be in [0, 127]: " + bitIndex);
        }

        long newHi = hi;
        long newLo = lo;
        if (bitIndex < Long.SIZE) {
            long add = 1L << bitIndex;
            long oldLo = newLo;
            newLo += add;
            if (Long.compareUnsigned(newLo, oldLo) < 0) {
                newHi++;
            }
        } else {
            newHi += 1L << (bitIndex - Long.SIZE);
        }

        return new Cid128(newHi, newLo);
    }

    /**
     * Compares two candidates by predecessor-side distance to this target.
     * Smaller means closer when walking left/negative from the target, with
     * unsigned wraparound.
     *
     * @return negative when {@code a} is closer than {@code b}, positive when
     *         {@code b} is closer, or zero when the distances match
     */
    public int comparePredecessorDistance(Cid128 a, Cid128 b) {
        Distance aDistance = subtract(this, a);
        Distance bDistance = subtract(this, b);
        return aDistance.compareTo(bDistance);
    }

    /**
     * Compares two candidates by successor-side distance to this target.
     * Smaller means closer when walking right/positive from the target, with
     * unsigned wraparound.
     *
     * @return negative when {@code a} is closer than {@code b}, positive when
     *         {@code b} is closer, or zero when the distances match
     */
    public int compareSuccessorDistance(Cid128 a, Cid128 b) {
        Distance aDistance = subtract(a, this);
        Distance bDistance = subtract(b, this);
        return aDistance.compareTo(bDistance);
    }

    @Override
    public int compareTo(Cid128 other) {
        int hiCompare = Long.compareUnsigned(hi, other.hi);
        return hiCompare != 0 ? hiCompare : Long.compareUnsigned(lo, other.lo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return (o instanceof Cid128 other) && hi == other.hi && lo == other.lo;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hi) * 31 + Long.hashCode(lo);
    }
    
    public String asHex() {
        return Util.asHex(bytes());
    }
    
    @Override
    public String toString() {
        return Util.asHex(bytes());
    }

    private static Distance subtract(Cid128 a, Cid128 b) {
        long lo = a.lo - b.lo;
        long borrow = Long.compareUnsigned(a.lo, b.lo) < 0 ? 1 : 0;
        long hi = a.hi - b.hi - borrow;
        return new Distance(hi, lo);
    }

    private static long longFromBytes(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            value = (value << Byte.SIZE) | (bytes[offset + i] & 0xFFL);
        }
        return value;
    }

    private static void writeLong(byte[] bytes, int offset, long value) {
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            bytes[offset + i] = (byte) value;
            value >>>= Byte.SIZE;
        }
    }

    private record Distance(long hi, long lo) implements Comparable<Distance> {
        @Override
        public int compareTo(Distance other) {
            int hiCompare = Long.compareUnsigned(hi, other.hi);
            return hiCompare != 0 ? hiCompare : Long.compareUnsigned(lo, other.lo);
        }
    }
}
