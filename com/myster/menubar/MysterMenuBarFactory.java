package com.myster.menubar;

import java.util.List;

import javax.swing.JFrame;
import javax.swing.JMenuBar;

public class MysterMenuBarFactory {
    private List<MysterMenuFactory> mysterMenuFactories;

    public MysterMenuBarFactory(List<MysterMenuFactory> mysterMenuFactories) {
        this.mysterMenuFactories = mysterMenuFactories;
    }

    public JMenuBar makeMenuBar(JFrame frame) {
        JMenuBar menuBar = new JMenuBar();

        for (int i = 0; i < mysterMenuFactories.size(); i++) {
            menuBar.add(mysterMenuFactories.get(i).makeMenu(frame));
        }

        return menuBar;
    }

    public int getMenuCount() {
        return mysterMenuFactories.size();
    }
}