package com.myster.menubar;

import java.util.List;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JMenu;

public class MysterMenuFactory {
    private List<MysterMenuItemFactory> mysterMenuItemFactories;

    private String name;

    public MysterMenuFactory(String name, Vector<MysterMenuItemFactory> mysterMenuItemFactories) {
        this.name = name;
        this.mysterMenuItemFactories = mysterMenuItemFactories;
    }

    public JMenu makeMenu(JFrame frame) {
        JMenu menu = new JMenu(com.myster.util.I18n.tr(name));
        
        for (MysterMenuItemFactory mysterMenuItemFactory : mysterMenuItemFactories) {
            mysterMenuItemFactory.makeMenuItem(frame, menu);
        }

        return menu;
    }

    public String getName() {
        return name;
    }
}