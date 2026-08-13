package com.myster.cid;

import com.general.util.Util;

/** Internal unsigned 128-bit value shared by the two domain-specific CID types. */
final class Cid128 implements Comparable<Cid128> {
    static final int LENGTH = 16;

    private final long hi;
    private final long lo;

    Cid128(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException("CID must be 16 bytes, got " + bytes.length);
        }

        this.hi = longFromBytes(bytes, 0);
        this.lo = longFromBytes(bytes, Long.BYTES);
    }

    private Cid128(long hi, long lo) {
        this.hi = hi;
        this.lo = lo;
    }

    byte[] bytes() {
        byte[] bytes = new byte[LENGTH];
        writeLong(bytes, 0, hi);
        writeLong(bytes, Long.BYTES, lo);
        return bytes;
    }

    Cid128 plusPowerOfTwo(int bitIndex) {
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

    int comparePredecessorDistance(Cid128 a, Cid128 b) {
        return subtract(this, a).compareTo(subtract(this, b));
    }

    int compareSuccessorDistance(Cid128 a, Cid128 b) {
        return subtract(a, this).compareTo(subtract(b, this));
    }

    @Override
    public int compareTo(Cid128 other) {
        int hiCompare = Long.compareUnsigned(hi, other.hi);
        return hiCompare != 0 ? hiCompare : Long.compareUnsigned(lo, other.lo);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        return object instanceof Cid128 other && hi == other.hi && lo == other.lo;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hi) * 31 + Long.hashCode(lo);
    }

    String asHex() {
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
