package com.myster.menubar;

import java.awt.Frame;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

public class MysterMenuItemFactory {
    private ActionListener action;

    private String name;

    private int shortcut;

    private boolean useShift;

    private boolean isDisabled = false;

    public MysterMenuItemFactory() {
        this("-");
    }

    public MysterMenuItemFactory(String s) {
        this(s, null);
    }

    public MysterMenuItemFactory(String s, ActionListener a) {
        this(s, a, -1);
    }

    public MysterMenuItemFactory(String s, ActionListener a, int shortcut) {
        this(s, a, shortcut, false);
    }

    public MysterMenuItemFactory(String s, ActionListener a, int shortcut,
            boolean useShift) {
        this.action = a;
        this.name = s;
        this.shortcut = shortcut;
        this.useShift = useShift;
    }

    public JMenuItem makeMenuItem(Frame frame) {
        JMenuItem menuItem = new JMenuItem(com.myster.util.I18n.tr(name));
        if (shortcut != -1) {
            int shiftMask = useShift ? InputEvent.SHIFT_DOWN_MASK : 0;
            menuItem.setAccelerator(KeyStroke.getKeyStroke(shortcut, InputEvent.CTRL_DOWN_MASK|shiftMask));
        }

        if (action != null) {
            menuItem.addActionListener(action);
        }

        menuItem.setEnabled(!isDisabled);

        return menuItem;
    }

    public void setEnabled(boolean b) {
        isDisabled = !b;
    }

    public String getName() {
        return name;
    }
}