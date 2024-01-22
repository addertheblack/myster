package com.myster.application;

import java.io.File;
import java.util.logging.Logger;

import com.general.application.ApplicationContext;

/**
 */
public class MysterGlobals {
    public static final int SERVER_PORT = 6669; // Default port. Changing this now works
    public static final String SPEEDPATH = "Globals/speed/";
    public static final String ADDRESSPATH = "Globals/address/";
    public static final String DEFAULT_ENCODING = "ASCII";

    private static final Logger LOGGER = Logger.getLogger(MysterGlobals.class.getName());
    private static final long programLaunchTime = System.currentTimeMillis(); //class load time really..
    
    public static final boolean ON_LINUX = (System.getProperty("os.name") != null ? System
            .getProperty("os.name").equals("Linux") : false);
    
    public static final boolean ON_MAC = (System.getProperty("os.name") != null ? System
            .getProperty("os.name").toLowerCase().startsWith("mac os") : false);

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

    public static File getCurrentDirectory() {
            File result = new File(new File(System.getProperty("user.home")), "myster");
            if (!result.exists())
                result.mkdir();
            return result;
    }
}