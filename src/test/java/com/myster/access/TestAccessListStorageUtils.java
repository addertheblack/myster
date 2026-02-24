package com.myster.access;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AccessListStorageUtils} binary serialization.
 * Includes wire format stability checks to catch accidental format breakage.
 */
class TestAccessListStorageUtils {
    private static KeyPair ed25519KeyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        ed25519KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void headerMagicAndVersionAreStable() throws IOException {
        byte[] typeBytes = new byte[16];
        Arrays.fill(typeBytes, (byte) 0xAB);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AccessListStorageUtils.writeHeader(baos, typeBytes);
        byte[] header = baos.toByteArray();

        assertEquals(0x4D, header[0] & 0xFF);
        assertEquals(0x59, header[1] & 0xFF);
        assertEquals(0x53, header[2] & 0xFF);
        assertEquals(0x54, header[3] & 0xFF);

        assertEquals(0, header[4]);
        assertEquals(0, header[5]);
        assertEquals(0, header[6]);
        assertEquals(1, header[7]);

        for (int i = 0; i < 16; i++) {
            assertEquals((byte) 0xAB, header[8 + i]);
        }

        assertEquals(0, header[24]);
        assertEquals(0, header[25]);
        assertEquals(0, header[26]);
        assertEquals(1, header[27]);

        assertEquals(0, header[28]);
        assertEquals(0, header[29]);
        assertEquals(0, header[30]);
        assertEquals(1, header[31]);

        assertEquals(32, header.length);
    }

    @Test
    void headerRoundTrip() throws IOException {
        byte[] originalTypeBytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            originalTypeBytes[i] = (byte) (i * 17);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AccessListStorageUtils.writeHeader(baos, originalTypeBytes);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        byte[] restoredTypeBytes = AccessListStorageUtils.readHeader(bais);

        assertArrayEquals(originalTypeBytes, restoredTypeBytes);
    }

    @Test
    void headerRejectsWrongMagic() {
        byte[] badHeader = new byte[32];
        badHeader[0] = 0x00;
        ByteArrayInputStream bais = new ByteArrayInputStream(badHeader);
        assertThrows(IOException.class, () -> AccessListStorageUtils.readHeader(bais));
    }

    @Test
    void headerRejectsWrongVersion() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(0x4D595354);
        dos.writeInt(999);
        dos.write(new byte[16]);
        dos.writeInt(1);
        dos.writeInt(1);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        assertThrows(IOException.class, () -> AccessListStorageUtils.readHeader(bais));
    }

    @Test
    void headerRejects32ByteMysterType() {
        assertThrows(IllegalArgumentException.class,
                () -> AccessListStorageUtils.writeHeader(new ByteArrayOutputStream(), new byte[32]));
    }

    @Test
    void blockSerializationRoundTrip() throws IOException {
        byte[] prevHash = new byte[32];
        Arrays.fill(prevHash, (byte) 0x42);
        byte[] payload = {1, 2, 3, 4, 5};
        byte[] signature = new byte[64];
        Arrays.fill(signature, (byte) 0xFF);

        AccessBlock original = new AccessBlock(prevHash, 7, 1234567890L,
                ed25519KeyPair.getPublic(), payload, signature);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AccessListStorageUtils.writeBlock(original, baos);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        AccessBlock restored = AccessListStorageUtils.readBlock(bais);

        assertArrayEquals(original.getPrevHash(), restored.getPrevHash());
        assertEquals(original.getHeight(), restored.getHeight());
        assertEquals(original.getTimestamp(), restored.getTimestamp());
        assertArrayEquals(original.getWriterPubkey().getEncoded(),
                          restored.getWriterPubkey().getEncoded());
        assertArrayEquals(original.getPayload(), restored.getPayload());
        assertArrayEquals(original.getSignature(), restored.getSignature());
        assertArrayEquals(original.getPayloadHash(), restored.getPayloadHash());
    }
}

