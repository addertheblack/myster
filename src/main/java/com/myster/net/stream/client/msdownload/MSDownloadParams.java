package com.myster.net.stream.client.msdownload;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

import com.myster.net.client.DnsLookupProtocol;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.ui.MysterFrameContext;

/**
 * Parameters for MS download operations.
 *
 * @param dnsLookup required CID resolver, passed at invocation to avoid a stream/DNS construction cycle
 * @param targetDir absolute base directory for the target file; empty means
 *        the caller failed to choose a destination and download startup will
 *        fail without opening a folder chooser
 * @param subDirectory relative subdirectory within {@code targetDir} where the
 *        file will be saved
 * @param startFailureHandler called if asynchronous download startup fails
 *        before the multi-source download starts
 */
public record MSDownloadParams(MysterFrameContext context,
                               HashCrawlerManager crawlerManager,
                               DnsLookupProtocol dnsLookup,
                               MysterFileStub stub,
                               Path targetDir,
                               Path subDirectory,
                               Consumer<DownloadStartException> startFailureHandler) {
    public MSDownloadParams(MysterFrameContext context,
                            HashCrawlerManager crawlerManager,
                            DnsLookupProtocol dnsLookup,
                            MysterFileStub stub,
                            Path targetDir,
                            Path subDirectory) {
        this(context, crawlerManager, dnsLookup, stub, targetDir, subDirectory, exception -> {});
    }

    public MSDownloadParams {
        Objects.requireNonNull(dnsLookup, "dnsLookup");
        startFailureHandler = Objects.requireNonNullElseGet(startFailureHandler,
                                                            () -> exception -> {});
    }

    public void reportStartFailure(DownloadStartException exception) {
        startFailureHandler.accept(exception);
    }
}
