package com.myster.search;

import java.io.IOException;
import java.util.Vector;

import com.myster.mml.RobustMML;
import com.myster.net.DisconnectException;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.type.MysterType;

public class StandardMysterSearch implements MysterSearchClientSection {
    final String searchString;

    final SearchResultListener listener;

    volatile boolean endFlag = false;

    public StandardMysterSearch(String searchString, SearchResultListener listener) {
        this.searchString = searchString;
        this.listener = listener;
    }

    public void start() {
        //listener.startSearch(); (This is already taken care of by the root.
        // All we have to do is add search results.)
    }

    public void search(MysterSocket socket, MysterAddress address, MysterType type)
            throws IOException {
        if (endFlag)
            throw new DisconnectException();

        Vector searchResults = com.myster.client.stream.StandardSuite.getSearch(socket, type,
                searchString);

        if (endFlag)
            throw new DisconnectException();

        if (searchResults.size() != 0) {
            MysterSearchResult[] searchArray = new MysterSearchResult[searchResults.size()];
            
            for (int i = 0; i < searchArray.length; i++) {
                searchArray[i] = new MysterSearchResult(new MysterFileStub(address,
                        type, (String) (searchResults.elementAt(i))));
            }
            
            listener.addSearchResults(searchArray);

            dealWithFileStats(socket, type, searchArray, listener);

            if (endFlag)
                throw new DisconnectException(); //FileInfoGetter
        }
    }

    public void endSearch(MysterType type) {
    }

    public void searchedAll(MysterType type) {
    }

    public synchronized void flagToEnd() {
        if (endFlag)
            return;
        endFlag = true;
    }

    /**
     * When passed a socket, type and Vector of search results (String) as well
     * as a listener, this routine will update the search result sin the
     * listener with the meta data found from the remote server.
     * 
     * This is not part of the crawlable interface, but who cares. I need it and
     * it's a nice routine.
     * 
     * 
     * @param socket
     *            socket to ask for File information on.
     * @param type
     *            type of of files the search results are for.
     * @param mysterSearchResults
     *            vector of strings, search results...
     * @param listener
     *            the mysterSearchResults listener. This routine only UPDATE the
     *            information, does not put the vector of search results into
     *            the listener.
     * @throws IOException
     *             (also a Disconnect exception) throws this exception on IO
     *             errors an if the search object is told to die (die die die!).
     */
    public void dealWithFileStats(MysterSocket socket, MysterType type, MysterSearchResult[] mysterSearchResults,
            SearchResultListener listener) throws IOException {
        //This is a speed hack.
        int pointer = 0;
        int current = 0;
        final int MAX_OUTSTANDING = 25;
        while (current < mysterSearchResults.length) { //usefull.
            if (endFlag)
                throw new DisconnectException();

            if (pointer < mysterSearchResults.length) {
                SearchResult result = mysterSearchResults[pointer];
                socket.out.writeInt(77);

                socket.out.writeInt(type.getAsInt());
                socket.out.writeUTF(result.getName());
                pointer++;
            }

            if (endFlag)
                throw new DisconnectException();

            while (socket.in.available() > 0 || (pointer - current > MAX_OUTSTANDING)
                    || pointer >= mysterSearchResults.length) {
                if (socket.in.readByte() != 1)
                    return;

                if (endFlag)
                    throw new DisconnectException();

                RobustMML mml;
                try {
                    mml = new RobustMML(socket.in.readUTF());
                } catch (Exception ex) {
                    return;
                }

                mysterSearchResults[current].setMML(mml);

                listener.searchStats(mysterSearchResults[current]);

                current++;

                if (current >= mysterSearchResults.length) {
                    break;
                }
            }
        }
    }
}