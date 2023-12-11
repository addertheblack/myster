package com.myster.mml;

public class NodeAlreadyExistsException extends MMLException {
    public NodeAlreadyExistsException(String tag) {
        super("Node already exists: " + tag);
    }
}