package com.myster.application;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import com.general.application.ApplicationContext;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.Util;

/**
 */
public class MysterGlobals {
    public static final int DEFAULT_SERVER_PORT = 6669; // Default port. Changing this now works
    public static final String SPEEDPATH = "Globals/speed/";
    public static final String ADDRESSPATH = "Globals/address/";
    public static final String DEFAULT_ENCODING = "ASCII";

    private static final Logger log = Logger.getLogger(MysterGlobals.class.getName());
    private static final long programLaunchTime = System.currentTimeMillis(); //class load time really..
    
    public static final boolean ON_LINUX = (System.getProperty("os.name") != null ? System
            .getProperty("os.name").toLowerCase().contains("linux") : false);
    
    public static final boolean ON_MAC = (System.getProperty("os.name") != null ? System
            .getProperty("os.name").toLowerCase().contains("mac") : false);
    
    public static final boolean ON_WINDOWS = (System.getProperty("os.name") != null ? System
            .getProperty("os.name").toLowerCase().contains("win") : false);

    public static ApplicationContext appSigleton;
    
    private static final List<Runnable> shutdownListeners = new ArrayList<>();
    
    /**
     * Registers a shutdown listener to be called when quit() is invoked.
     * Listeners are called in the order they were registered.
     * 
     * @param listener the listener to register
     */
    public static synchronized void addShutdownListener(Runnable listener) {
        shutdownListeners.add(listener);
    }
    
    /**
     * Removes a shutdown listener.
     * 
     * @param listener the listener to remove
     * @return true if the listener was removed, false if it wasn't registered
     */
    public static synchronized boolean removeShutdownListener(Runnable listener) {
        return shutdownListeners.remove(listener);
    }
    
    /**
     * Instead of calling System.exit() directly to quit, call this routine. It makes sure cleanup
     * is done.
     * 
     * NOTE: It's a very frequent occurrence for the program to quit without calling this routine so
     * your code should in no way depend on it. (Some platform do not call this at all when
     * quitting!).
     */
    public static void quit() {
        log.info("Byeeeee.");


        // Call all registered shutdown listeners
        List<PromiseFuture<Runnable>> futures;
        synchronized (MysterGlobals.class) {
            futures = Util.map(shutdownListeners, l -> PromiseFutures.execute(() -> {
                l.run();
                
                return null;
            }));
        }
        
        var f = PromiseFutures.allCallResults(futures);

        try {
            f.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException _) {
            // nothing...
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

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