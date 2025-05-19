
package com.myster.ui;

import java.awt.Rectangle;

import com.general.util.Util;
import com.myster.pref.ui.PreferencesDialogBox;
import com.myster.pref.ui.PreferencesPanel;

public class PreferencesGui {
    private final PreferencesDialogBox prefsWindow;
    private final MysterFrameContext context;


    public PreferencesGui(MysterFrameContext context) {
        this.context = context;
        prefsWindow = new PreferencesDialogBox(context);
        if ( !Util.isEventDispatchThread() )
            throw new IllegalStateException("Not on event thread!");
        prefsWindow.pack();
    }
    

    static final String WINDOW_KEEPER_KEY = "MysterPrefsGUI";

    private void initWindowLocations(MysterFrameContext c) {
        Rectangle[] lastLocs = c.keeper().getLastLocs(WINDOW_KEEPER_KEY);
        if (lastLocs.length > 0) {
             prefsWindow.setBounds(lastLocs[0]);
             setGUI(true);
        }
    }
    

    public void initGui() {
        context.keeper().addFrame(prefsWindow, WINDOW_KEEPER_KEY);
        
        initWindowLocations(context);
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
