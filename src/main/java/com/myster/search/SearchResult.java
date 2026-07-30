package com.myster.search;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.stream.client.msdownload.DownloadStartException;
import com.myster.tracker.MysterServer;

public interface SearchResult {
    /**
     * Starts this search result downloading to the supplied base directory.
     *
     * @param baseDirectory validated base directory for the download
     * @param startFailureHandler called if asynchronous startup fails before
     *        the download starts
     * @throws DownloadStartException if local download setup fails before the
     *         asynchronous download is scheduled
     */
    void downloadTo(Path baseDirectory, Consumer<DownloadStartException> startFailureHandler)
            throws DownloadStartException;

    // returns the network the search result is on.
    String getNetwork();

    // gets a value for a meta data thingy
    String getMetaData(String key);

    // gets the list of known meta data types for this item.
    String[] getKeyList();

    // gets the Name of the search result (usualy a file name!)
    String getName();

    // gets the host address
    MysterAddress getHostAddress();

    MysterServer getServer();

    // this might be an abstraction violation.. I'm not sure.
    MysterProtocol getProtocol();
}
