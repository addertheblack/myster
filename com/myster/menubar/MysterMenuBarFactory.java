package com.myster.menubar;

import java.awt.MenuBar;
import java.util.Vector;

public class MysterMenuBarFactory {
    Vector mysterMenuFactories;

    public MysterMenuBarFactory(Vector mysterMenuFactories) {
        this.mysterMenuFactories = mysterMenuFactories;
    }

    public MenuBar makeMenuBar() {
        MenuBar menuBar = new MenuBar();

        for (int i = 0; i < mysterMenuFactories.size(); i++) {
            menuBar
                    .add(((MysterMenuFactory) (mysterMenuFactories.elementAt(i)))
                            .makeMenu());
        }

        return menuBar;
    }

    public int getMenuCount() {
        return mysterMenuFactories.size();
    }
}