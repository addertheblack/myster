package com.myster.hash.ui;

import java.awt.Checkbox;

import com.general.util.MessagePanel;
import com.myster.hash.HashManager;
import com.myster.pref.ui.PreferencesPanel;
import com.myster.ui.PreferencesGui;

/**
 * Unlike most other modules in Myster, the hash package hash a clear frontend /
 * backend separation even with it's preferences. This should allow preferences
 * to be set from any interface and not just privately. it does, however, mean
 * more coding for me, which is why I don't usually bother. In this case it
 * proves usefull, but not very.
 */

public class HashPreferences extends PreferencesPanel {
    public static final int CHECKBOX_Y_SIZE = 25;
    
    private final MessagePanel explanation;
    private final Checkbox enableHashing;
    private final HashManager manager;

    public static void init(PreferencesGui preferencesGui, HashManager manager) {
        preferencesGui.addPanel(new HashPreferences(manager)); //People
    }

    public HashPreferences(HashManager manager) {
        this.manager = manager;
        setLayout(null);

        explanation = new MessagePanel(
                "Enables files to be hashed in the background so they can be downloaded with multi source download.\n\n"
                        + "NOTE: Files stop hashing after the current one.");
        explanation.setLocation(0, 0);
        explanation.setSize(STD_XSIZE, STD_YSIZE / 2 - CHECKBOX_Y_SIZE);
        add(explanation);

        enableHashing = new Checkbox("Enabling File Hashing");
        enableHashing.setLocation(50, STD_YSIZE / 2 - CHECKBOX_Y_SIZE);
        enableHashing.setSize(STD_XSIZE - 50, CHECKBOX_Y_SIZE);
        add(enableHashing);

        setSize(STD_XSIZE, STD_YSIZE);
    }

    public void save() {
        manager.setHashingEnabled(enableHashing.getState());
    }

    public void reset() {
        enableHashing.setState(manager.getHashingEnabled());
    }

    public String getKey() {
        return "Hashing";
    }
}