package com.general.util;

public class UnexpectedException extends RuntimeException {
    private Exception ex;

    public UnexpectedException(Exception ex) {
        this.ex = ex;
    }

    public void printStackTrace() {
        ex.printStackTrace();
    }

    //others missing.....
}