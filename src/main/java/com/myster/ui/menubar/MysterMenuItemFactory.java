package com.myster.ui.menubar;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import com.myster.application.MysterGlobals;

public class MysterMenuItemFactory {
    private final Action action;

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

    public MysterMenuItemFactory(String s, ActionListener a, int shortcut, boolean useShift) {
        this(makeAction(s, a, shortcut, useShift));
    }

    private static Action makeAction(String s, ActionListener a, int shortcut, boolean useShift) {
        Action action = new AbstractAction(s) {

            @Override
            public void actionPerformed(ActionEvent e) {
                a.actionPerformed(e);
            }
        };

        if (shortcut != -1) {
            action.putValue(Action.ACCELERATOR_KEY,
                            KeyStroke.getKeyStroke(shortcut,
                                                   useShift ? InputEvent.SHIFT_DOWN_MASK : 0));
        }
        return action;
    }

    public MysterMenuItemFactory(Action action) {
        this.action = action;

        this.action.putValue(Action.NAME,
                             com.myster.util.I18n.tr((String) action.getValue(Action.NAME)));

        int keyMask = MysterGlobals.ON_MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
        KeyStroke k = (KeyStroke) this.action.getValue(Action.ACCELERATOR_KEY);
        if (k != null) {
            this.action
                    .putValue(Action.ACCELERATOR_KEY,
                              KeyStroke.getKeyStroke(k.getKeyCode(), k.getModifiers() | keyMask));
        }
    }

    public void makeMenuItem(JFrame frame, JMenu menu) {
        if ("-".equals(action.getValue(Action.NAME))) {
            menu.addSeparator();

            return;
        }

        JMenuItem menuItem = new JMenuItem(action);

        menu.add(menuItem);
    }

    public void setEnabled(boolean b) {
        action.setEnabled(b);
    }

    public String getName() {
        return (String) action.getValue(Action.NAME);
    }
}