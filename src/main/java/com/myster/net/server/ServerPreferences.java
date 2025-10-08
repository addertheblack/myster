
package com.myster.net.server;

import java.util.prefs.Preferences;

import com.myster.application.MysterGlobals;

public class ServerPreferences {
    private static final String PATH_IN_PREFS = "Server Download Slots";
    private static final String IDENTITY_NAME_KEY = "Server Identity";
    private static final String SERVER_PORT = "Server Port";
    private static final String FREELOAD_KEY = "ServerFreeloaderKey";

    private final Preferences preferences;

    public ServerPreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    public void setIdentityName(String s) {
        preferences.put(IDENTITY_NAME_KEY, s);
    }

    public String getIdentityName() {
        return preferences.get(IDENTITY_NAME_KEY, "");
    }

    public Integer getServerPort() {
        return preferences.getInt(SERVER_PORT, MysterGlobals.DEFAULT_SERVER_PORT);
    }

    public void setPort(int value) {
        preferences.putInt(SERVER_PORT, value);
    }

    public final void setDownloadSlots(int i) {
        preferences.putInt(PATH_IN_PREFS, i);
    }

    public final int getDownloadSlots() {
        return Math.max(preferences.getInt(PATH_IN_PREFS, 4), 1);
    }

    public void setKickFreeloaders(boolean set) {
        preferences.putBoolean(FREELOAD_KEY, set);
    }
    
    public boolean isKickFreeloaders() {
        return preferences.getBoolean(FREELOAD_KEY, false);
    }
}
