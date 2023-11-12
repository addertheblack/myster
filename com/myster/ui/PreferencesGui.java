
package com.myster.ui;

import java.awt.Rectangle;

import com.general.util.Util;
import com.myster.pref.ui.PreferencesDialogBox;
import com.myster.pref.ui.PreferencesPanel;

public class PreferencesGui {
    private PreferencesDialogBox prefsWindow;

    public PreferencesGui() {
        prefsWindow = new PreferencesDialogBox();
        if ( !Util.isEventDispatchThread() )
            throw new IllegalStateException("Not on event thread!");
        prefsWindow.pack();
    }
    

    static final String WINDOW_KEEPER_KEY = "MysterPrefsGUI";

    static com.myster.ui.WindowLocationKeeper windowKeeper = new com.myster.ui.WindowLocationKeeper(
            WINDOW_KEEPER_KEY);

    private void initWindowLocations() {
        Rectangle[] rect = com.myster.ui.WindowLocationKeeper.getLastLocs(WINDOW_KEEPER_KEY);
        if (rect.length > 0) {
             prefsWindow.setBounds(rect[0]);
             setGUI(true);
        }
    }
    

    public void initGui() {
        windowKeeper.addFrame(prefsWindow);
        
        initWindowLocations();
    }

    public void setGUI(boolean b) {
        if (b) {
            prefsWindow.show();
            prefsWindow.toFrontAndUnminimize();
        } else {
            prefsWindow.hide();
        }
    }

    /**
     * Adds a preferences panel to the preferences GUI under the type and subType. Hierarchical
     * prefs are not supported at this time.
     * 
     * Pluggin writers are asked to avoid messing with other module's pref panels.
     * 
     * @param panel
     */
    public void addPanel(PreferencesPanel panel) {
        prefsWindow.addPanel(panel);
    }

}
