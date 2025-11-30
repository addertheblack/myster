
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
    private static final String SELECTED_KEY = "Selected Key";

    private record PrefGuiData(String selectedKey) {}

    public int initGui() {
        int windowCount = 0;
        List<PrefData<PrefGuiData>> lastLocs = context.keeper().getLastLocs(WINDOW_KEEPER_KEY, (p) -> new PrefGuiData(p.get(SELECTED_KEY, "")));
        if (lastLocs.size() > 0) {
             PrefData<PrefGuiData> prefData = lastLocs.get(0);
             prefsWindow.setBounds(prefData.location().bounds());
             setGUI(prefData.location().visible());
        
             windowCount++;
             prefsWindow.setSelectedKey(prefData.data().selectedKey());
        }
        
        
        prefsWindow.setSaver(context.keeper().addFrame(prefsWindow, (p) -> {
            String key = prefsWindow.getSelectedKey();
            
            p.put(SELECTED_KEY, key);
        }, WINDOW_KEEPER_KEY, WindowPrefDataKeeper.SINGLETON_WINDOW));
        
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
