package com.myster.hash;

public abstract class FileHash {
    public abstract byte[] getBytes();

    public abstract short getHashLength();

    public abstract String getHashName();
    //toString <- should be hex value
}