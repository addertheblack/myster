package com.myster.search;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.general.util.Util;
import com.myster.mml.MessagePak;
import com.myster.mml.ReadOnlyMessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.stream.client.msdownload.DownloadDirectoryValidator;
import com.myster.net.stream.client.msdownload.DownloadStartException;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.search.ui.ServerStatsFromCache;
import com.myster.tracker.MysterServer;
import com.myster.ui.MysterFrameContext;

/** Search row whose metadata reads and publication are confined to the EDT. */
public class MysterSearchResult implements SearchResult {
    private static final ReadOnlyMessagePak EMPTY_STATS = MessagePak.newEmpty().asReadOnly();

    private final MysterFileStub stub;
    private final ServerStatsFromCache cache;
    private final MysterProtocol protocol;
    private final HashCrawlerManager hashCrawler;
    private final MysterFrameContext context;
    
    private ReadOnlyMessagePak metadata = EMPTY_STATS;

    public MysterSearchResult(MysterProtocol protocol,
                              HashCrawlerManager hashCrawler,
                              MysterFrameContext context,
                              MysterFileStub stub,
                              ServerStatsFromCache cache) {
        this.protocol = protocol;
        this.hashCrawler = hashCrawler;
        this.context = context;
        this.stub = stub;
        this.cache = cache;
    }

    /**
     * Installs a live protected view on the EDT, scheduling publication if called off the EDT.
     * Null clears the stats. The owner retains mutation authority over the supplied pack;
     * consumers holding an older view keep that pack until they re-read the result accessor.
     */
    public void setFileStats(MessagePak stats) {
        Util.invokeNowOrLater(() -> metadata = stats == null ? EMPTY_STATS : stats.asReadOnly());
    }

    @Override
    public void downloadTo(Path baseDirectory,
                           Consumer<DownloadStartException> startFailureHandler)
            throws DownloadStartException {
        Path validatedBaseDirectory =
                DownloadDirectoryValidator.validateDownloadDirectory(baseDirectory);

        getProtocol().getStream()
                     .downloadFile(new MSDownloadParams(context,
                                                        hashCrawler,
                                                        getProtocol().getDnsLookup(),
                                                        stub,
                                                        validatedBaseDirectory,
                                                        Path.of(""),
                                                        startFailureHandler));
    }

    // returns the network the search result is on.
    @Override
    public String getNetwork() {
        return "Myster Network";
    }

    @Override
    public ReadOnlyMessagePak getMetaData() {
        return metadata;
    }

    // gets the list of known meta data types for this item.
    @Override
    public String[] getKeyList() {
        return metadata.list("/").stream()
                .map(key -> "/" + key)
                .filter(metadata::isAValue)
                .toArray(String[]::new);
    }

    // gets the filename of the search result
    @Override
    public String getName() {
        return stub.getName();
    }

    @Override
    public MysterAddress getHostAddress() {
        return stub.getMysterAddress();
    }

    @Override
    public MysterServer getServer() {
        return cache.get(getHostAddress());
    }

    @Override
    public MysterProtocol getProtocol() {
        return protocol;
    }
}
