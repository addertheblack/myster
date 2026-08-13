package com.myster.cid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.PublicKey;

import org.junit.jupiter.api.Test;

import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.type.MysterType;

class TestMysterTypeCid {
    @Test
    void derivesMd5OfEncodedPublicKey() throws Exception {
        byte[] encodedKey = { 2, 4, 6, 8 };
        PublicKey publicKey = mock(PublicKey.class);
        when(publicKey.getEncoded()).thenReturn(encodedKey);

        byte[] expected = MessageDigest.getInstance("MD5").digest(encodedKey);

        assertArrayEquals(expected, MysterTypeCid.fromPublicKey(publicKey).bytes());
    }

    @Test
    void bytesAndHexRoundTripDefensively() throws IOException {
        byte[] source = new byte[MysterTypeCid.LENGTH];
        source[0] = (byte) 0xAB;
        source[15] = (byte) 0xCD;
        MysterTypeCid cid = new MysterTypeCid(source);

        source[0] = 0;
        assertEquals(cid, MysterTypeCid.fromHexString(cid.asHex()));

        byte[] returned = cid.bytes();
        returned[15] = 0;
        assertEquals((byte) 0xCD, cid.bytes()[15]);
    }

    @Test
    void rejectsMalformedValues() {
        assertThrows(NullPointerException.class, () -> new MysterTypeCid(null));
        assertThrows(IllegalArgumentException.class, () -> new MysterTypeCid(new byte[15]));
        assertThrows(IOException.class, () -> MysterTypeCid.fromHexString("not-hex"));
        assertThrows(IOException.class, () -> MysterTypeCid.fromHexString("00"));
    }

    @Test
    void mysterTypePreservesCidBytesAndHex() throws IOException {
        byte[] bytes = new byte[MysterTypeCid.LENGTH];
        bytes[4] = 42;
        MysterTypeCid cid = new MysterTypeCid(bytes);
        MysterType type = new MysterType(bytes);

        assertArrayEquals(cid.bytes(), type.toBytes());
        assertEquals(cid.asHex(), type.toHexString());
        assertEquals(type, MysterType.fromHexString(type.toHexString()));
    }

    @Test
    void incomingWrongLengthTypeIsAnIoFailure() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeShort(4);
            out.write(new byte[4]);
        }

        MysterDataInputStream in = new MysterDataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        assertThrows(IOException.class, in::readType);
    }
}
