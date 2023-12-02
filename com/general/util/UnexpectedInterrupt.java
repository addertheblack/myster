package com.general.util;

public class UnexpectedInterrupt extends UnexpectedException {
    public static boolean silent;

    public UnexpectedInterrupt(InterruptedException ex) {
        super(ex);
    }

    public void printStackTrace() {
        if (!silent)
            super.printStackTrace();
    }

    public String getMessage() {
        return (silent ? "" : super.getMessage());
    }

    public String toString() {
        return (silent ? "" : super.toString());
    }

}

