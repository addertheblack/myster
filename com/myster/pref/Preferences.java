/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.pref;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Hashtable;

import com.general.util.Semaphore;
import com.myster.Myster;
import com.myster.mml.MML;
import com.myster.pref.ui.PreferencesDialogBox;
import com.myster.pref.ui.PreferencesPanel;
import com.myster.ui.WindowManager;
import com.myster.util.MysterThread;

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

//USES THE SINGLETON DESIGN PATTERN!!!! READ UP ON IT!
public class Preferences {
    private Hashtable data;

    private PreferencesDialogBox prefsWindow;

    private static Preferences pref;

    private final File preferenceFile = new File(Myster.getCurrentDirectory(), "mysterprefs.mml");

    private final File preferenceBackupFile = new File(Myster.getCurrentDirectory(),
            "mysterprefs.mml.backup");

    public static final int DEBUG = 1;

    public static final int NORMAL = 0;

    public static final String HEADER_STRING = "New myster prefs format";

    private Preferences() {
        loadFile();
        savethread = new SaveThread();
        savethread.start();
        prefsWindow = new PreferencesDialogBox();
    }

    static final String WINDOW_KEEPER_KEY = "MysterPrefsGUI";

    static com.myster.ui.WindowLocationKeeper windowKeeper = new com.myster.ui.WindowLocationKeeper(
            WINDOW_KEEPER_KEY);

    public static void initWindowLocations() {
        Rectangle[] rect = com.myster.ui.WindowLocationKeeper.getLastLocs(WINDOW_KEEPER_KEY);
        if (rect.length > 0) {
            getInstance().prefsWindow.setBounds(rect[0]);
            getInstance().setGUI(true);
        }
    }

    /**
     * Needed in order to get an instance of the preference object. This is included Since their
     * should only be one instance of the preferences object but The routines should not have to be
     * static...
     */
    public static synchronized Preferences getInstance() {

        if (pref == null) {
            pref = new Preferences();
        }

        return pref;
    }

    public static synchronized void initGui() {
        windowKeeper.addFrame(pref.prefsWindow);
    }

    public synchronized void setGUI(boolean b) {
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

    /**
     * Gets the value for a path. returns null if path not initilized or invalid.
     * 
     * @param key
     * @return
     */
    public synchronized String get(String key) {
        return (String) (data.get(key));
    }

    /**
     * Gets the value for a path. returns defaultValue if path not initilized or invalid.
     * 
     * @param key
     * @param defaultValue
     * @return
     */
    public synchronized String get(String key, String defaultValue) {
        String temp = (String) (data.get(key));

        return (temp == null ? defaultValue : temp);
    }

    /**
     * Gets the value for a path. Returns "" if path not initilized or invalid.
     * 
     * @param key
     * @return
     */
    public synchronized String query(String key) {
        String temp = (String) (data.get(key));
        if (temp == null)
            return "";
        return temp;
    }

    public synchronized MML getAsMML(String key) {
        return (MML) (data.get(key));
    }

    public synchronized PreferencesMML getAsMML(String key, PreferencesMML defaultMML) {
        PreferencesMML mml = (PreferencesMML) (data.get(key));

        return (mml == null ? defaultMML : mml);
    }

    /**
     * Deletes that path and all sub paths.
     * 
     * @param key
     * @return
     */
    public synchronized String remove(String key) {
        String s_temp = (String) (data.remove(key));
        save();
        return s_temp;
    }

    /**
     * Checks if key exists.
     * 
     * @param key
     *            to check
     * @return true if key exists false otherwise.
     */
    public synchronized boolean containsKey(String key) {
        return data.containsKey(key);
    }

    public synchronized Object put(String key, String value) {
        Object s_temp = data.put(key, value);
        save();
        return s_temp;
    }

    public synchronized Object put(String key, MML value) {
        Object mml_temp = data.put(key, value);
        save();
        return mml_temp;
    }

    private synchronized void loadFile() {
        //try FILE try preferenceBackupFile then make a new file.

        try {
            try {
                loadFromFile(preferenceFile);
            } catch (Exception ex) {
                ex.printStackTrace();
                loadFromFile(preferenceBackupFile);
            }
        } catch (IOException ex) {
            System.out
                    .println("No prefs file or prefs file is corrupt or prefs file is old. Making a new one.");
            ex.printStackTrace();
        } catch (ClassCastException ex) {
            ex.printStackTrace();
            System.out.println("Serious bad class is happening");
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        } finally {
            if (data == null)
                data = new Hashtable();
        }
    }

    private synchronized void loadFromFile(File file) throws IOException, ClassNotFoundException,
            ClassCastException {
        ObjectInputStream in = null;
        try {
            if (!file.exists()) {
                throw new IOException("Toss and catch!");
            }

            in = new ObjectInputStream(new FileInputStream(file));
            String s_temp = (String) (in.readObject());
            if (s_temp.equals(HEADER_STRING)) {
                data = (Hashtable) (in.readObject());
            }
        } finally {
            try {
                in.close();
            } catch (Exception ex) {
            }
        }
    }

    Semaphore writeToDisk = new Semaphore(1);

    /**
     * Saves changes to the preferences. It should be caled if you have made a change. Ideally the
     * prefferences should save themselves, however they don't always since Speed will suffer.
     * Calling this routine makes sure the prefferences have been saved.
     * 
     * Returns true if the save has been successfull and false if it has not.
     * 
     * 
     * NOTE: The creation of the string to be saved must be synchronized with the entire prefference
     * object. The actualy writing to disk just needs to be semaphored.
     * 
     * @param flag -
     *            if set to 1 will print debug message on exceptions.
     * @return true if was successfully saved.
     */
    private boolean saveFile(int flag) {
        String stringToSave;

        writeToDisk.waitx(); //SEM BLOCK START!

        try {
            if (preferenceBackupFile.exists())
                preferenceBackupFile.delete(); //on the mac the next line
            // tosses an excption if file
            // already exists.
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(
                    preferenceBackupFile));
            out.writeObject(HEADER_STRING);

            synchronized (this) {
                out.writeObject(data);
            }

            out.flush();
            out.close();
            if (!preferenceFile.delete() && preferenceFile.exists())
                throw new Exception("Delete not sucessfull! is file in use??");
            if (!preferenceBackupFile.renameTo(preferenceFile))
                throw new Exception("Rename not sucessfull! is file in use??");
        } catch (Exception ex) {
            if (flag == DEBUG) {
                ex.printStackTrace();
            }

            return false;
        } finally {
            writeToDisk.signalx();//SEM BLOCK END HERE TO!
        }

        System.out.println("Preferences have been saved.");
        return true;
    }

    /**
     * Same as saveFile(int) however, sets the debug flag to true to return more information about
     * an error if one occurs during the save.
     */

    SaveThread savethread; //enables asynchronous saving.

    private boolean saveFile() { //should not be synchronized, should return
        // immediately.
        //Called by saver thread.
        saveFile(DEBUG);
        return true; //programs need not know if there has been an error.
        //return saveFile(DEBUG);
    }

    /**
     * Same as saveFile(); only does saving asychronously.
     */
    public boolean save() {
        savethread.asynchronousSave();
        return true; //wow.. retarded...
    }

    /**
     * Flushes the Preferences to disk
     * <p>
     * When preferences are told to save, the save operation might not happen immediately since
     * saved are batched automatically to avoid excessive disk activity. Calling this function
     * guarantees the information will be saved
     * <p>
     * Warning: is blocking and accesses io so this operation is slow.
     */
    public void flush() {
        saveFile();
    }

    private class SaveThread extends MysterThread {
        private Semaphore sem = new Semaphore(0);

        private volatile boolean needsSave = false; //is assumed operations

        // are synchronized.

        private static final long SAVETIME = 10 * 1000; //1000 == 1 sec.

        public void run() {
            setPriority(Thread.MIN_PRIORITY);
            do {
                sem.waitx();
                if (needsSave) { //this is strictly not necessary! good to
                    // check assumptions.
                    saveFile();
                    synchronized (this) { //abs(essential). is not here bad
                        // things happen.
                        needsSave = false;
                    }
                    try {
                        sleep(SAVETIME);
                    } catch (Exception ex) {
                    }
                } else {
                    System.out.println("FUNDEMENTAL ASSUMPTION VIOLATED IN SAVE THREAD");
                }
            } while (true);
        }

        public synchronized void asynchronousSave() {
            if (!needsSave) {
                needsSave = true;
                sem.signalx();
            }
        }
    }
}