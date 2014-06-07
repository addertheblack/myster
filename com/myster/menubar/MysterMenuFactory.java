package com.myster.menubar;

import java.awt.Frame;
import java.util.Vector;

import javax.swing.JMenu;

public class MysterMenuFactory {
    Vector mysterMenuItemFactories;

    String name;

    public MysterMenuFactory(String name, Vector mysterMenuItemFactories) {
        this.name = name;
        this.mysterMenuItemFactories = mysterMenuItemFactories;
    }

    public JMenu makeMenu(Frame frame) {
        JMenu menu = new JMenu(com.myster.util.I18n.tr(name));

        for (int i = 0; i < mysterMenuItemFactories.size(); i++) {
            menu.add(((MysterMenuItemFactory) mysterMenuItemFactories
                    .elementAt(i)).makeMenuItem(frame));
        }

        return menu;
    }

    public String getName() {
        return name;
    }
}