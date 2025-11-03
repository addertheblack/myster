package com.myster.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import com.general.thread.Invoker;
import com.general.util.UnexpectedException;
import com.general.util.Util;
import com.myster.net.DisconnectException;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterProtocol;
import com.myster.net.stream.client.StandardSuiteStream;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

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
    private final Tracker manager;
    private final MysterProtocol protocol;
    private final HashCrawlerManager hashCrawler;
    private final MysterFrameContext context;

    private volatile boolean endFlag = false;

    public StandardMysterSearch(MysterProtocol protocol,
                                HashCrawlerManager hashCrawler,
                                MysterFrameContext context,
                                Tracker manager,
                                String searchString,
                                MysterType type,
                                SearchResultListener listener) {
        this.protocol = protocol;
        this.hashCrawler = hashCrawler;
        this.context = context;
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
                                                        context,
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
     * When passed a socket, type and Vector of search results (String) as well
     * as a listener, this routine will update the search results in the
     * listener with the meta data found from the remote server.
     * 
     * @param socket
     *            socket to ask for File information on.
     * @param mysterSearchResults
     *            vector of strings, search results...
     * @throws IOException
     *             (also a Disconnect exception) throws this exception on IO
     *             errors an if the search object is told to die (die die die!).
     */
    public void dealWithFileStats(MysterSocket socket, MysterSearchResult[] mysterSearchResults)
            throws IOException {

        List<MysterFileStub> fileStubs =
                Util.map(Arrays.asList(mysterSearchResults),
                         r -> new MysterFileStub(r.getHostAddress(), type, r.getName()));
        final int[] counter = new int[] { 0 };
        var promise = StandardSuiteStream
                .getFileStatsBatch(socket, fileStubs.toArray(new MysterFileStub[] {}))
                .setInvoker(Invoker.EDT)
                .addPartialResultListener(messagePack -> {
                    mysterSearchResults[counter[0]].setFileStats(messagePack);

                    listener.searchStats(mysterSearchResults[counter[0]]);

                    counter[0]++;
                });
        
        try {
            // wait for async task to complete
            promise.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            
            throw new CancellationException();
        } catch (ExecutionException e) {
            var cause = e.getCause();
            
            if ( cause instanceof RuntimeException r) {
                throw r;
            } else if (cause instanceof IOException r ) {
                throw r;
            } else {
                throw new UnexpectedException(e);
            }
        }
    }
}