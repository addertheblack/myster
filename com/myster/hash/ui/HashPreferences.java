package com.myster.hash.ui;

import java.awt.Checkbox;

import com.general.util.MessagePanel;
import com.myster.hash.HashManager;
import com.myster.pref.ui.PreferencesPanel;

/**
 * Unlike most other modules in Myster, the hash package hash a clear frontend /
 * backend seperation even with it's preferences. This should allow preferences
 * to be set from any interface and not just privately. it does, however, mean
 * more coding for me, which is why I don't usually bother. In this case it
 * proves usefull, but not very.
 */

public class HashPreferences extends PreferencesPanel {
    MessagePanel explanation;

    Checkbox enableHashing;

    public static final int CHECKBOX_Y_SIZE = 25;

    public static void init() {
        //Preferences.getInstance().addPanel(new HashPreferences()); //People
        // should never disable hashing.
    }

    public HashPreferences() {
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
        HashManager.setHashingEnabled(enableHashing.getState());
    }

    public void reset() {
        enableHashing.setState(HashManager.getHashingEnabled());
    }

    public String getKey() {
        return "Hashing";
    }
}