package com.myster.net.stream.client.msdownload;

import java.nio.file.Path;

import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.ui.MysterFrameContext;

/**
 * Parameters for MS download operations.
 *
 * @param subDirectory The subdirectory within targetDir where the file will be saved. MUST BE RELATIVE PATH!
 */
public record MSDownloadParams(MysterFrameContext context,
                               HashCrawlerManager crawlerManager,
                               MysterFileStub stub,
                               Path targetDir,
                               Path subDirectory) {
}
