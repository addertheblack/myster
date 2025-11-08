package com.myster.pref;

import java.util.Arrays;
import java.util.HashSet;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.myster.mml.MML;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;

/**
 * System globals are stored in; "Globals/" : Connection type: "Globals/speed/"
 * 
 * FileManager Paths are stored in : "File manager/paths/"
 * 
 * Myster IPs are stored in : "Myster IPs/" Specific IP: "/Myster IPs/ <IP or domain name>/"
 * Specific Myster IP info is stored somewhere in this path.
 * 
 * IPList information is stored in : "IPLists/ <Type>/" Specific IP NAME OF IP as a DIR as in:
 * "/IPLists/ <Type>/ <Name of IP>/" This name or IP should be identical to the one stored above..
 *  
 */
public class MysterPreferences {
    public static final int DEBUG = 1;
    public static final int NORMAL = 0;
    public static final String HEADER_STRING = "New myster prefs format";

    private static MysterPreferences pref;
    
    private final Preferences preferences;

    private MysterPreferences() {
        preferences = Preferences.userRoot().node("Myster General Preferences");
    }


    /**
     * Needed in order to get an instance of the preference object. This is included Since their
     * should only be one instance of the preferences object but The routines should not have to be
     * static...
     * 
     * @deprecated stop using this statically
     */
    @Deprecated
    public static synchronized MysterPreferences getInstance() {
        if (pref == null) {
            pref = new MysterPreferences();
        }

        return pref;
    }
    
    public Preferences getPreferences() {
        return preferences;
    }

    /**
     * Gets the value for a path. returns null if path not initilized or invalid.
     * 
     * @param key
     */
    public String get(String key) {
        return  preferences.get(key, null);
    }

    /**
     * Gets the value for a path. returns defaultValue if path not initilized or invalid.
     * 
     * @param key
     * @param defaultValue
     */
    public String get(String key, String defaultValue) {
        return preferences.get(key, defaultValue);
    }
    
    public int getInt(String key, int defaultValue) {
        return preferences.getInt(key, defaultValue);
    }

    public void putInt(String key, int value) {
        preferences.putInt(key, value);
    }

    /**
     * Gets the preferences node for storing window-specific metadata such as window location,
     * connected server, search positions, and column sizes.
     * 
     * @param windowClassPrefKey the unique key for the window (typically the conceptual class name of the window type)
     * @return the Preferences node for this window's metadata
     */
    public Preferences windowMetaDataNode(String windowClassPrefKey) {
        return preferences.node("Window Metadata").node(windowClassPrefKey);
    }

    /**
     * Gets the value for a path. Returns "" if path not inited or invalid.
     * 
     * @param key
     */
    public String query(String key) {
        return get(key, "");
    }

    public synchronized MML getAsMML(String key) {
        return getAsMML(key, null);
    }

    public synchronized RobustMML getAsMML(String key, RobustMML defaultMML) {
        try {
            String v = get(key);
            if (v == null) {
                return defaultMML;
            }
            return new RobustMML(v);
        } catch (MMLException ex) {
            return defaultMML;
        }
    }

    /**
     * Deletes that path and all sub paths.
     * 
     * @param key
     */
    public synchronized String remove(String key) {
        String v = get(key);
        preferences.remove(key);
        return v;
    }

    /**
     * Checks if key exists.
     * 
     * @param key
     *            to check
     * @return true if key exists false otherwise.
     */
    public synchronized boolean containsKey(String key) {
        try {
            return (new HashSet<String>(Arrays.asList(preferences.childrenNames()))).contains(key);
        } catch (BackingStoreException exception) {
            return false;
        }
    }

    public synchronized Object put(String key, String value) {
        preferences.put(key, value);
        return value;
    }

    public synchronized Object put(String key, MML value) {
        preferences.put(key, value.toString());
        return value;
    }
}