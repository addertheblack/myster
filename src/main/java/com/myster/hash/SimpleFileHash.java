package com.myster.hash;

import java.io.Serializable;

public class SimpleFileHash extends FileHash implements Serializable {
    private byte[] hash;

    private String hashName;

    private SimpleFileHash() {
    }

    protected SimpleFileHash(String hashName, byte[] hash) {
        this.hashName = hashName.toLowerCase();
        this.hash = (byte[]) hash.clone();
    }

    public byte[] getBytes() {
        return (byte[]) hash.clone();
    }

    public short getHashLength() {
        return (short) hash.length;
    }

    public String getHashName() {
        return hashName;
    }

    public String toString() {
        return asHex(hash);
    }

    public boolean equals(Object o) {
        SimpleFileHash otherHash = (SimpleFileHash) o;

        if (otherHash.hash.length != hash.length)
            return false;

        if (!hashName.equalsIgnoreCase(otherHash.hashName))
            return false;

        for (int i = 0; i < hash.length; i++) {
            if (otherHash.hash[i] != hash[i]) {
                return false;
            }
        }

        return true;
    }

    public static String asHex(byte hash[]) {
        StringBuffer buf = new StringBuffer(hash.length * 2);
        int i;

        for (i = 0; i < hash.length; i++) {
            if ((hash[i] & 0xff) < 0x10)
                buf.append("0");

            buf.append(Long.toString(hash[i] & 0xff, 16));

            //if (i != hash.length -1) buf.append(":");
        }

        return buf.toString();
    }

    public static FileHash buildFileHash(String hashName, byte[] hash) {
        return new SimpleFileHash(hashName, hash);
    }

    public static FileHash buildFromHexString(String hashName, String hexString) {
        return new SimpleFileHash(hashName, com.general.util.Util
                .fromHexString(hexString));
    }
}

