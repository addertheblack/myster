package com.myster.mml;

public class DoubleSlashException extends MMLPathException {
    public DoubleSlashException(String path) {
        super("// Exception: " + path);
    }
}