package com.myster.search;

import java.io.IOException;

import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.type.MysterType;

/**
 * Used to be used for Myster searches and crawls before UDP but now is an historical legacy.
 */
public interface MysterSearchClientSection {
    public void start(); //called when crawler starts.. useful for init.

    public void search(MysterSocket socket, MysterAddress address, MysterType type)
            throws IOException;

    public void searchedAll(MysterType type); //called when (and only if) all

    // servers have been searched. Is
    // called just before endSearch.

    public void endSearch(MysterType type); //called when craler dies

    public void flagToEnd(); //flag search to end ansynchronously
    //public void end(); //signal the search to end and join();
}