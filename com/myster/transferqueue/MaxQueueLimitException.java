package com.myster.transferqueue;

public class MaxQueueLimitException extends Exception {
    public MaxQueueLimitException(String s) {
        super(s);
    }

    public MaxQueueLimitException() {
        super();
    }
}