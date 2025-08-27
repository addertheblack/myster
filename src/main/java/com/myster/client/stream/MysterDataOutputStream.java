
package com.myster.client.stream;

import java.io.IOException;
import java.io.OutputStream;

import com.myster.mml.MessagePack;
import com.myster.type.MysterType;

public class MysterDataOutputStream extends OutputStream  {
    private static final int MAX_UNSIGNED_SHORT = 65535;
    
    private final OutputStream out;
    
    public MysterDataOutputStream(OutputStream out) {
        this.out = out;
    }
    
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }
    
    public void write(int b) throws IOException {
        out.write(b);
    }

    public void writeBoolean(boolean v) throws IOException {
        write(v ? 1 : 0);        
    }

    public void writeByte(int v) throws IOException {
        write(v); 
    }

    public void writeShort(int v) throws IOException {
        byte[] whatever = new byte[2];
        whatever[1] = (byte) (v);
        whatever[0] = (byte) (v >> 8);
        write(whatever);
    }
    
    public void writeUnsignedShort(int v) throws IOException {
        writeShort(v);
    }

    public void writeChar(int v) throws IOException {
        writeShort(v);
    }

    public void writeInt(int v) throws IOException {
        byte[] whatever = new byte[4];
        
        whatever[3] = (byte) (v);
        whatever[2] = (byte) (v >> 8);
        whatever[1] = (byte) (v >> 16);
        whatever[0] = (byte) (v >> 24);
        
        write(whatever);
    }

    public void writeLong(long v) throws IOException {
        byte[] whatever = new byte[8];
        
        whatever[7] = (byte) (v);
        whatever[6] = (byte) (v >> 8);
        whatever[5] = (byte) (v >> 16);
        whatever[4] = (byte) (v >> 24);
        whatever[3] = (byte) (v >> 32);
        whatever[2] = (byte) (v >> 40);
        whatever[1] = (byte) (v >> 48);
        whatever[0] = (byte) (v >> 56);
        
        write(whatever);
    }

    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToRawIntBits(v));
    }

    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToRawLongBits(v));
    }

    public void writeUTF(String s) throws IOException {
        byte[] bytes = s.getBytes("UTF-8");
        if (bytes.length > 0xFFFF) {
                throw new IllegalArgumentException("too many bytes. Expected < 2^16 but was " + bytes.length);
        }
        writeChar(bytes.length);
        write(bytes);
    }

    public void writeType(MysterType t) throws IOException {
        final var bytes = t.toBytes();
        
        if (bytes.length > MAX_UNSIGNED_SHORT) {
            throw new IllegalArgumentException("Can't serialize myster type because it's key is"
                    + " longer than " + MAX_UNSIGNED_SHORT + ". It is: " + bytes.length);
        }
        writeUnsignedShort(bytes.length);
        write(bytes);
    }
    
    @Override
    public void flush() throws IOException {
        out.flush();
    }

    public void writeMessagePack(MessagePack pack) throws IOException {
        byte[] bytes = pack.toBytes();
        writeInt(bytes.length);
        write(bytes);
    }
}
