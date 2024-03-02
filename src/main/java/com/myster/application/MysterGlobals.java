package com.myster.application;

import java.io.File;
import java.util.logging.Logger;

import com.general.application.ApplicationContext;

/**
 */
public class MysterGlobals {
    public static final int DEFAULT_SERVER_PORT = 6669; // Default port. Changing this now works
    public static final String SPEEDPATH = "Globals/speed/";
    public static final String ADDRESSPATH = "Globals/address/";
    public static final String DEFAULT_ENCODING = "ASCII";

    private static final Logger LOGGER = Logger.getLogger(MysterGlobals.class.getName());
    private static final long programLaunchTime = System.currentTimeMillis(); //class load time really..
    
    public static final boolean ON_LINUX = (System.getProperty("os.name") != null ? System
            .getProperty("os.name").toLowerCase().contains("linux") : false);
    
    public static final boolean ON_MAC = (System.getProperty("os.name") != null ? System
            .getProperty("os.name").toLowerCase().contains("mac") : false);
    
    public static final boolean ON_WINDOWS = (System.getProperty("os.name") != null ? System
            .getProperty("os.name").toLowerCase().contains("win") : false);

    public static ApplicationContext appSigleton;
    
    /**
     * Instead of calling System.exit() directly to quit, call this routine. It makes sure cleanup
     * is done.
     * 
     * NOTE: It's a very frequent occurrence for the program to quit without calling this routine so
     * your code should in no way depend on it. (Some platform do not call this at all when
     * quitting!).
     */
    public static void quit() {
        LOGGER.info("Byeeeee.");
        if (appSigleton!=null)
            appSigleton.close();
        System.exit(0);
    }

    /**
     * Returns the time that was returned by System.currentTimeMillis when the program was first
     * launched.
     */
    public static long getLaunchedTime() {
        return programLaunchTime;
    }

    public static final String APP_NAME = "myster";

    public static File getAppDataPath() {
        String osName = System.getProperty("os.name").toLowerCase();
        String appDataDir;
        if (osName.contains("win")) {
            // Windows
            appDataDir = System.getenv("LOCALAPPDATA") + "\\Myster";
        } else if (osName.contains("mac")) {
            // macOS
            appDataDir = System.getProperty("user.home") + "/Library/Application Support/Myster";
        } else {
            // Linux and others
            appDataDir = System.getProperty("user.home") + "/." + APP_NAME;
        }
        File result = new File(appDataDir);
        if (!result.exists()) {
            result.mkdir();
        }
        return result;
    }
}