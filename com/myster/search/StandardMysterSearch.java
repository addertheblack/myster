package com.myster.search;

import java.io.IOException;
import java.util.List;


import com.general.util.Util;
import com.myster.client.net.MysterProtocol;
import com.myster.mml.RobustMML;
import com.myster.net.DisconnectException;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.tracker.IPListManager;
import com.myster.type.MysterType;

/**
 * This call is responsible for containing the piece of code that does TCP based searches and
 * returns the result to the SearchResultListener. This object is built so that it can be re-used
 * for as many searches as needed; you don't need to create a new object for each search so long as
 * the type and search string are the same. Calling "FlagToEnd()" will request this object "abort".
 * After flagToEnd() is called you are guaranteed that no more results will be reported, however,
 * threads running the code might take some time to return.
 * <p>
 *  Should probably be renamed for "StandardMysterSearch" to something that does not imply it
 * contains the entire Myster searcher code.
 * 
 * @see MysterSearch
 */
public class StandardMysterSearch {
    private final String searchString;
    private final SearchResultListener listener;
    private final MysterType type;
    private final IPListManager manager;
    private final MysterProtocol protocol;
    private final HashCrawlerManager hashCrawler;

    private volatile boolean endFlag = false;

    public StandardMysterSearch(MysterProtocol protocol,
                                HashCrawlerManager hashCrawler,
                                IPListManager manager,
                                String searchString,
                                MysterType type,
                                SearchResultListener listener) {
        this.protocol = protocol;
        this.hashCrawler = hashCrawler;
        this.searchString = searchString;
        this.listener = listener;
        this.type = type;
        this.manager = manager;
    }

    
    public void search(MysterSocket socket, MysterAddress address) throws IOException {
        if (endFlag)
            throw new DisconnectException();

        List<String> searchResults = protocol.getStream().getSearch(socket, type,
                searchString);

        if (endFlag)
            throw new DisconnectException();

        if (searchResults.size() != 0) {
            final MysterSearchResult[] searchArray = new MysterSearchResult[searchResults.size()];

            for (int i = 0; i < searchArray.length; i++) {
                searchArray[i] = new MysterSearchResult(protocol, 
                                                        hashCrawler,
                                                        new MysterFileStub(address,
                                                                           type,
                                                                           searchResults.get(i)),
                                                        manager::getQuickServerStats);
            }

            try {
                Util.invokeAndWait(new Runnable() {
                    public void run() {
                        synchronized (StandardMysterSearch.this) {
                            if (endFlag)
                                return;
                            listener.addSearchResults(searchArray);
                        }
                    }
                });
            } catch (InterruptedException ex) {
                if (endFlag) {
                    throw new DisconnectException();
                } else {
                    ex.printStackTrace();
                    throw new DisconnectException();
                }

            }

            dealWithFileStats(socket, searchArray);

            if (endFlag)
                throw new DisconnectException(); //FileInfoGetter
        }
    }

    /**
     * Request that no more search results or stats be updated and that any threads that try to call
     * the two methods of this object return as quickly as possible.
     *  
     */
    public synchronized void flagToEnd() {
        if (endFlag)
            return;
        endFlag = true;
    }

    /**
     * When passed a socket, type and Vector of search results (String) as well as a listener, this
     * routine will update the search results in the listener with the meta data found from the
     * remote server.
     * 
     * @param socket
     *            socket to ask for File information on.
     * @param mysterSearchResults
     *            vector of strings, search results...
     * @throws IOException
     *             (also a Disconnect exception) throws this exception on IO errors an if the search
     *             object is told to die (die die die!).
     */
    public void dealWithFileStats(MysterSocket socket, MysterSearchResult[] mysterSearchResults)
            throws IOException {
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