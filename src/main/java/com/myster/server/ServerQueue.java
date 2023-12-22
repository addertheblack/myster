package com.myster.server;

import com.myster.pref.MysterPreferences;
import com.myster.transferqueue.AbstractDownloadQueue;

public class ServerQueue extends AbstractDownloadQueue {
    public static final String PATH_IN_PREFS = "Server Transfer Queued";

    public static final int DEFAULT_POSITIONS = 2;

    public ServerQueue() {
        super(getQueueLength(PATH_IN_PREFS, DEFAULT_POSITIONS));
    }

    public void saveDownloadSpotsInPrefs(int newSpots) {
        setQueueLength(PATH_IN_PREFS, newSpots);
    }

    ///////////////////Static utilities\\\\\\\\\\\\\\
    private final static String PREF_PATH = "downloadSpots";

    private static final void setQueueLength(String UNIQUE_PATH_IN_PREFS, int i) { //sets
                                                                                   // the
                                                                                   // queue
                                                                                   // length
                                                                                   // in
                                                                                   // the
                                                                                   // !prefs!.
        MysterPreferences.getInstance().put(PREF_PATH + "/" + UNIQUE_PATH_IN_PREFS,
                "" + i);
    }

    private static final int getQueueLength(String UNIQUE_PATH_IN_PREFS,
            int defaultValue) { //gets the queue length from the !prefs!.
        String s_spots = MysterPreferences.getInstance().get(
                PREF_PATH + "/" + UNIQUE_PATH_IN_PREFS);

        if (s_spots != null) {
            try {
                int spots = Integer.parseInt(s_spots);
                if (spots < 2)
                    spots = defaultValue;
                return spots;
            } catch (NumberFormatException ex) {
            }
        }
        return defaultValue;
    }
}