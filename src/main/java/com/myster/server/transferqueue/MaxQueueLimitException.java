package com.myster.server.transferqueue;

public class MaxQueueLimitException extends Exception {
    public MaxQueueLimitException(String s) {
        super(s);
    }

    public MaxQueueLimitException() {
        super();
    }
}