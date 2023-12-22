package com.myster.ui;

import com.myster.ui.menubar.MysterMenuBar;

public record MysterFrameContext(MysterMenuBar menuBar, WindowManager windowManager) {}