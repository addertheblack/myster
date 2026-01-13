package com.myster.ui;

import com.myster.client.ui.ClientWindowProvider;
import com.myster.filemanager.FileTypeListManager;
import com.myster.progress.ui.DownloadManager;
import com.myster.type.TypeDescriptionList;
import com.myster.ui.menubar.MysterMenuBar;

/** Holds shared UI context for Myster application frames. */
public record MysterFrameContext(MysterMenuBar menuBar,
                                 WindowManager windowManager,
                                 TypeDescriptionList tdList,
                                 WindowPrefDataKeeper keeper,
                                 FileTypeListManager fileManager,
                                 DownloadManager downloadManager,
                                 ClientWindowProvider clientWindowProvider) {}
