package com.myster.search;

import com.general.events.GenericEvent;

public class HashSearchEvent extends GenericEvent {
    public static final int START_SEARCH = 0;

    public static final int SEARCH_RESULT = 1;

    public static final int END_SEARCH = 2;

    MysterFileStub stub;

    public HashSearchEvent(int id, MysterFileStub stub) {
        super(id);

        this.stub = stub;
    }

    public MysterFileStub getFileStub() {
        return stub;
    }
}