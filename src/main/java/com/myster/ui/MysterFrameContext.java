package com.myster.ui;

import com.myster.filemanager.FileTypeListManager;
import com.myster.type.TypeDescriptionList;
import com.myster.ui.menubar.MysterMenuBar;

public record MysterFrameContext(MysterMenuBar menuBar, WindowManager windowManager, TypeDescriptionList tdList, WindowLocationKeeper keeper, FileTypeListManager fileManager) {
}