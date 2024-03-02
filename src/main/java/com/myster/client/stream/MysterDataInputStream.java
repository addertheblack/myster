
package com.myster.client.stream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;

public class MysterDataInputStream extends InputStream {
    private final byte[] readBuffer = new byte[1024];
    private final InputStream in;
    
    
    public MysterDataInputStream(InputStream in) {
        this.in = in;
    }
    
    @Override
    public int read() throws IOException {
        return in.read();
    }
    
    public int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }
    
    /**
     * See the general contract of the {@code readFully}
     * method of {@code DataInput}.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @param   b   the buffer into which the data is read.
     * @throws  NullPointerException if {@code b} is {@code null}.
     * @throws  EOFException  if this input stream reaches the end before
     *          reading all the bytes.
     * @throws  IOException   the stream has been closed and the contained
     *          input stream does not support reading after close, or
     *          another I/O error occurs.
     * @see     java.io.FilterInputStream#in
     */
    public final void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    /**
     * See the general contract of the {@code readFully}
     * method of {@code DataInput}.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in the data array {@code b}.
     * @param      len   the number of bytes to read.
     * @throws     NullPointerException if {@code b} is {@code null}.
     * @throws     IndexOutOfBoundsException if {@code off} is negative,
     *             {@code len} is negative, or {@code len} is greater than
     *             {@code b.length - off}.
     * @throws     EOFException  if this input stream reaches the end before
     *             reading all the bytes.
     * @throws     IOException   the stream has been closed and the contained
     *             input stream does not support reading after close, or
     *             another I/O error occurs.
     * @see        java.io.FilterInputStream#in
     */
    public final void readFully(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        int n = 0;
        while (n < len) {
            int count = read(b, off + n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
    }

    /**
     * See the general contract of the {@code skipBytes}
     * method of {@code DataInput}.
     * <p>
     * Bytes for this operation are read from the contained
     * input stream.
     *
     * @param      n   the number of bytes to be skipped.
     * @return     the actual number of bytes skipped.
     * @throws     IOException  if the contained input stream does not support
     *             seek, or the stream has been closed and
     *             the contained input stream does not support
     *             reading after close, or another I/O error occurs.
     */
    public final int skipBytes(int n) throws IOException {
        int total = 0;
        int cur = 0;

        while ((total<n) && ((cur = (int) skip(n-total)) > 0)) {
            total += cur;
        }

        return total;
    }

    public boolean readBoolean() throws IOException {
        return readUnsignedByte() != 0;
    }

    public byte readByte() throws IOException {
        return (byte) readUnsignedByte();
    }

    public int readUnsignedByte() throws IOException {
        int ch = read();
        if (ch < 0)
            throw new EOFException();
        return  0xFF & ch;
    }

    public short readShort() throws IOException {
        readFully(readBuffer, 0, 2);
        
        int temp = readBuffer[0] << 8;
        int temp2 = ((0xFF) &  readBuffer[1]);
        return (short)( temp | temp2);
    }

    public int readUnsignedShort() throws IOException {
        return (readShort() & 0xFFFF);
    }

    public char readChar() throws IOException {
        return (char)readShort();
    }

    public int readInt() throws IOException {
        readFully(readBuffer, 0, 4);
        
        return ((readBuffer[0] & 0xFF) << 24) |
                ((readBuffer[1] & 0xFF) << 16) |
                ((readBuffer[2] & 0xFF) << 8) |
                 (readBuffer[3] & 0xFF);
    }

    public long readLong() throws IOException {
        return (((long)readInt()) << 32) | (readInt() & 0xFFFFFFFFL);
    }

    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    public String readUTF() throws IOException {
        int size = readUnsignedShort();
        
        byte[] buffer = new byte[size];
        
        readFully(buffer);
        
        return new String(buffer, Charset.forName("UTF-8"));
    }
}
