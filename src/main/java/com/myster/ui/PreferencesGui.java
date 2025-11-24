
package com.myster.ui;

import java.util.List;

import com.general.util.Util;
import com.myster.pref.ui.PreferencesDialogBox;
import com.myster.pref.ui.PreferencesPanel;
import com.myster.ui.WindowPrefDataKeeper.PrefData;

public class PreferencesGui {
    private final PreferencesDialogBox prefsWindow;
    private final MysterFrameContext context;


    public PreferencesGui(MysterFrameContext context) {
        this.context = context;
        prefsWindow = new PreferencesDialogBox(context);
        if ( !Util.isEventDispatchThread() )
            throw new IllegalStateException("Not on event thread!");
    }
    

    static final String WINDOW_KEEPER_KEY = "MysterPrefsGUI";


    public int initGui() {
        int windowCount = 0;
        List<PrefData<Object>> lastLocs = context.keeper().getLastLocs(WINDOW_KEEPER_KEY, (_) -> null);
        if (lastLocs.size() > 0) {
             prefsWindow.setBounds(lastLocs.get(0).location().bounds());
             setGUI(lastLocs.get(0).location().visible());
        
             windowCount++;
        }
        
        context.keeper().addFrame(prefsWindow, (_) -> {}, WINDOW_KEEPER_KEY, WindowPrefDataKeeper.SINGLETON_WINDOW);
        
        return windowCount;
    }

    public void setGUI(boolean b) {
        if (b) {
            prefsWindow.toFrontAndUnminimize();
        }
        prefsWindow.setVisible(b);
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
