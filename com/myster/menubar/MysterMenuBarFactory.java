package com.myster.menubar;

import javax.swing.JFrame;
import javax.swing.JMenuBar;

public interface MysterMenuBarFactory {
    public default JMenuBar makeMenuBar(JFrame frame) {
        return new JMenuBar();
    }

    public default int getMenuCount() {
        return 0;
    }
}