package com.myster.cid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class TestServerCid {
    @Test
    void derivesFirst128BitsOfSha256() throws Exception {
        byte[] encodedKey = { 1, 3, 3, 7 };
        PublicKey publicKey = mock(PublicKey.class);
        when(publicKey.getEncoded()).thenReturn(encodedKey);

        byte[] expected = Arrays.copyOf(
                MessageDigest.getInstance("SHA-256").digest(encodedKey), ServerCid.LENGTH);

        assertArrayEquals(expected, ServerCid.fromPublicKey(publicKey).bytes());
    }

    @Test
    void constructorAndBytesDefensivelyCopy() {
        byte[] source = bytes(0, 1);
        ServerCid cid = new ServerCid(source);

        source[15] = 2;
        assertArrayEquals(bytes(0, 1), cid.bytes());

        byte[] returned = cid.bytes();
        returned[15] = 3;
        assertArrayEquals(bytes(0, 1), cid.bytes());
    }

    @Test
    void rejectsInvalidLengths() {
        assertThrows(NullPointerException.class, () -> new ServerCid(null));
        assertThrows(IllegalArgumentException.class, () -> new ServerCid(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> new ServerCid(new byte[17]));
    }

    @Test
    void remainsDistinctFromTypeCid() {
        byte[] bytes = new byte[ServerCid.LENGTH];
        assertNotEquals(new ServerCid(bytes), new MysterTypeCid(bytes));
        assertNotEquals(new MysterTypeCid(bytes), new ServerCid(bytes));
    }

    @Test
    void unsignedCompareOrdersHighBitValues() {
        assertTrue(cid(0, 0).compareTo(cid(0, 1)) < 0);
        assertTrue(cid(0x7FFF_FFFF_FFFF_FFFFL, -1)
                .compareTo(cid(0x8000_0000_0000_0000L, 0)) < 0);
        assertTrue(cid(-1, -1).compareTo(cid(0, 0)) > 0);
    }

    @Test
    void plusPowerOfTwoWrapsIn128BitSpace() {
        assertEquals(cid(0, 1), cid(0, 0).plusPowerOfTwo(0));
        assertEquals(cid(1, 0), cid(0, 0).plusPowerOfTwo(64));
        assertEquals(cid(0, 0), cid(-1, -1).plusPowerOfTwo(0));
        assertEquals(cid(0x8000_0000_0000_0000L, 0), cid(0, 0).plusPowerOfTwo(127));
        assertThrows(IllegalArgumentException.class, () -> cid(0, 0).plusPowerOfTwo(128));
    }

    @Test
    void predecessorDistanceHandlesOrdinaryAndWraparoundTargets() {
        ServerCid target = cid(0, 10);
        assertTrue(target.comparePredecessorDistance(cid(0, 9), cid(0, 8)) < 0);
        assertTrue(target.comparePredecessorDistance(target, cid(0, 9)) < 0);

        ServerCid zero = cid(0, 0);
        assertTrue(zero.comparePredecessorDistance(cid(-1, -1), cid(-1, -2)) < 0);
    }

    @Test
    void successorDistanceHandlesOrdinaryAndWraparoundTargets() {
        ServerCid target = cid(0, 10);
        assertTrue(target.compareSuccessorDistance(cid(0, 11), cid(0, 12)) < 0);
        assertTrue(target.compareSuccessorDistance(target, cid(0, 11)) < 0);

        ServerCid max = cid(-1, -1);
        assertTrue(max.compareSuccessorDistance(cid(0, 0), cid(0, 1)) < 0);
    }

    private static ServerCid cid(long hi, long lo) {
        return new ServerCid(bytes(hi, lo));
    }

    private static byte[] bytes(long hi, long lo) {
        byte[] bytes = new byte[ServerCid.LENGTH];
        writeLong(bytes, 0, hi);
        writeLong(bytes, Long.BYTES, lo);
        return bytes;
    }

    private static void writeLong(byte[] bytes, int offset, long value) {
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            bytes[offset + i] = (byte) value;
            value >>>= Byte.SIZE;
        }
    }
}
