package com.general.util;

public class UnexpectedError extends RuntimeException {
    public UnexpectedError() {
    }

    public UnexpectedError(String msg) {
        super(msg);
    }
}