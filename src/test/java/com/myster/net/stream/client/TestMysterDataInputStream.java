package com.myster.net.stream.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;

public class TestMysterDataInputStream {
    private ByteArrayOutputStream baos;
    private MysterDataOutputStream mdos;

    @BeforeEach
    void setUp() {
        baos = new ByteArrayOutputStream();
        mdos = new MysterDataOutputStream(baos);
    }

    @Test
    void testWriteBoolean() throws IOException {
        boolean testData = true;
        mdos.writeBoolean(testData);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        MysterDataInputStream mdin = new MysterDataInputStream(bais);

        assertEquals(testData, mdin.readBoolean());
        mdin.close();
    }

    @Test
    void testWriteByte() throws IOException {
        byte testData = 123;
        mdos.writeByte(testData);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        MysterDataInputStream mdin = new MysterDataInputStream(bais);

        assertEquals(testData, mdin.readByte());
        mdin.close();
    }

    @Test
    void testWriteShort() throws IOException {
        short testData = 12345;
        mdos.writeShort(testData);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        MysterDataInputStream mdin = new MysterDataInputStream(bais);

        assertEquals(testData, mdin.readShort());
        mdin.close();
    }

    @Test
    void testWriteChar() throws IOException {
        char testData = 'A';
        mdos.writeChar(testData);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        MysterDataInputStream mdin = new MysterDataInputStream(bais);

        assertEquals(testData, mdin.readChar());
        mdin.close();
    }

    @Test
    void testWriteInt() throws IOException {
        int testData = 123456789;
        mdos.writeInt(testData);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        MysterDataInputStream mdin = new MysterDataInputStream(bais);

        assertEquals(testData, mdin.readInt());
        mdin.close();
    }

    @Test
    void testWriteLong() throws IOException {
        long testData = 1234567890123456789L;
        mdos.writeLong(testData);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        MysterDataInputStream mdin = new MysterDataInputStream(bais);

        assertEquals(testData, mdin.readLong());
        mdin.close();
    }

    @Test
    void testWriteFloat() throws IOException {
        float testData = 123.45f;
        mdos.writeFloat(testData);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        MysterDataInputStream mdin = new MysterDataInputStream(bais);

        assertEquals(testData, mdin.readFloat());
        mdin.close();
    }

    @Test
    void testWriteDouble() throws IOException {
        double testData = 12345678.12345678;
        mdos.writeDouble(testData);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        MysterDataInputStream mdin = new MysterDataInputStream(bais);

        assertEquals(testData, mdin.readDouble());
        mdin.close();
    }

    @Test
    void testWriteUTF() throws IOException {
        String testData = "Hello, World!";
        mdos.writeUTF(testData);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        MysterDataInputStream mdin = new MysterDataInputStream(bais);

        assertEquals(testData, mdin.readUTF());
        mdin.close();
    }
    @Test
    void testWriteShortNetworkOrder() throws IOException {
        short testData = 0x1234;
        mdos.writeShort(testData);

        byte[] expectedBytes = ByteBuffer.allocate(Short.BYTES).putShort(testData).array();
        byte[] actualBytes = baos.toByteArray();

        assertArrayEquals(expectedBytes, actualBytes, "The bytes were not written in network byte order.");
    }

    @Test
    void testWriteIntNetworkOrder() throws IOException {
        int testData = 0x12345678;
        mdos.writeInt(testData);

        byte[] expectedBytes = ByteBuffer.allocate(Integer.BYTES).putInt(testData).array();
        byte[] actualBytes = baos.toByteArray();

        assertArrayEquals(expectedBytes, actualBytes, "The bytes were not written in network byte order.");
    }

    @Test
    void testWriteLongNetworkOrder() throws IOException {
        long testData = 0x123456789ABCDEF0L;
        mdos.writeLong(testData);

        byte[] expectedBytes = ByteBuffer.allocate(Long.BYTES).putLong(testData).array();
        byte[] actualBytes = baos.toByteArray();

        assertArrayEquals(expectedBytes, actualBytes, "The bytes were not written in network byte order.");
    }

    @Test
    void testWriteCharNetworkOrder() throws IOException {
        char testData = 'A'; // Unicode value of 'A' is 0x0041
        mdos.writeChar(testData);

        byte[] expectedBytes = ByteBuffer.allocate(Character.BYTES).putChar(testData).array();
        byte[] actualBytes = baos.toByteArray();

        assertArrayEquals(expectedBytes, actualBytes, "The bytes were not written in network byte order.");
    }
}
