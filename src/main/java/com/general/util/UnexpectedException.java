package com.general.util;

public class UnexpectedException extends IllegalStateException {
    private Throwable ex;

    public UnexpectedException(String s) {
        super(s);
    }
    
    public UnexpectedException(Throwable ex) {
        this.ex = ex;
    }

    public void printStackTrace() {
        ex.printStackTrace();
    }

    //others missing.....
}