package com.myster.ui;

import java.util.Objects;

import com.myster.client.ui.ClientWindowProvider;
import com.myster.link.MysterLinkService;
import com.myster.filemanager.FileTypeListManager;
import com.myster.progress.ui.DownloadManager;
import com.myster.type.TypeDescriptionList;
import com.myster.ui.menubar.MysterMenuBar;
import com.myster.tracker.ui.KnownServerSource;

/** Holds shared UI services and state for Myster application frames. All services are required. */
public record MysterFrameContext(MysterMenuBar menuBar,
                                 WindowManager windowManager,
                                 TypeDescriptionList tdList,
                                 WindowPrefDataKeeper keeper,
                                 FileTypeListManager fileManager,
                                 DownloadManager downloadManager,
                                 ClientWindowProvider clientWindowProvider,
                                 KnownServerSource knownServerSource,
                                 MysterLinkService linkService) {
    public MysterFrameContext {
        Objects.requireNonNull(menuBar, "menuBar");
        Objects.requireNonNull(windowManager, "windowManager");
        Objects.requireNonNull(tdList, "tdList");
        Objects.requireNonNull(keeper, "keeper");
        Objects.requireNonNull(fileManager, "fileManager");
        Objects.requireNonNull(downloadManager, "downloadManager");
        Objects.requireNonNull(clientWindowProvider, "clientWindowProvider");
        Objects.requireNonNull(knownServerSource, "knownServerSource");
        Objects.requireNonNull(linkService, "linkService");
    }
}
