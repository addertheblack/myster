package com.general.util;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

import com.general.thread.CallListener;
import com.general.thread.CancellableCallable;
import com.general.thread.Future;

public class Util { //This code was taken from an Apple Sample Code package,

    public static Image loadImage(String filename, Component watcher) {
        if (filename != null) {
            System.out.println(watcher.getClass().getName());
            URL url = watcher.getClass().getResource(filename);
            return loadImage(filename, watcher, url);
        }
        return null;
    }

    public static Image loadImage(String filename, Component watcher, URL url) {
        Image image = null;

        if (url == null) {
            System.err.println("loadImage() could not find \"" + filename + "\"");
        } else {
            image = watcher.getToolkit().getImage(url);
            if (image == null) {
                System.err.println("loadImage() getImage() failed for \"" + filename + "\"");
            } else {
                MediaTracker tracker = new MediaTracker(watcher);

                try {
                    tracker.addImage(image, 0);
                    tracker.waitForID(0);
                } catch (InterruptedException e) {
                    System.err.println("loadImage(): " + e);
                } finally {
                    boolean isError = tracker.isErrorAny();
                    if (isError) {
                        System.err.println("loadImage() failed to load \"" + filename + "\"");
                        int flags = tracker.statusAll(true);

                        boolean loading = 0 != (flags & MediaTracker.LOADING);
                        boolean aborted = 0 != (flags & MediaTracker.ABORTED);
                        boolean errored = 0 != (flags & MediaTracker.ERRORED);
                        boolean complete = 0 != (flags & MediaTracker.COMPLETE);
                        System.err.println("loading: " + loading);
                        System.err.println("aborted: " + aborted);
                        System.err.println("errored: " + errored);
                        System.err.println("complete: " + complete);
                    }
                }
            }
        }

        return image;
    }

    public static String getStringFromBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "bytes";
        }

        long kilo = bytes / 1024;
        if (kilo < 1024) {
            return kilo + "K";
        }

        double megs = (double) kilo / 1024;
        if (megs < 1024) {
            String temp = "" + megs;
            return temp.substring(0, temp.indexOf(".") + 2) + "MB";
        }

        double gigs = megs / 1024;
        if (gigs < 1024) {
            String temp = "" + gigs;
            return temp.substring(0, temp.indexOf(".") + 2) + "GB";
        }

        double tera = gigs / 1024;
        String temp = "" + tera;
        return temp.substring(0, temp.indexOf(".") + 2) + "TB";
    }

    public static byte[] concatenateBytes(byte[] array, byte[] array2) {
        byte[] buffer = new byte[array.length + array2.length];

        System.arraycopy(array, 0, buffer, 0, array.length);
        System.arraycopy(array2, 0, buffer, array.length, array2.length);

        return buffer;
    }

    public static byte[] fromHexString(String hash) throws NumberFormatException {
        if ((hash.length() % 2) != 0)
            throw new NumberFormatException("Even number of byte pairs");

        byte[] bytes = new byte[hash.length() / 2];

        for (int i = 0; i < hash.length(); i += 2) {
            bytes[i / 2] = (byte) (Short.parseShort(hash.substring(i, i + 2), 16)); //hopefully
            // the
            // compiler
            // will
            // convert
            // "/2"
            // into
            // >> 1
        }

        return bytes;
    }

    /**
     * Centers the frame passed on the screen. The offsets are to offset the frame from perfect
     * center. If you want it centered call this with offsets of 0,0
     */
    public static void centerFrame(Frame frame, int xOffset, int yOffset) {
        Toolkit tool = Toolkit.getDefaultToolkit();
        frame.setLocation(tool.getScreenSize().width / 2 - frame.getSize().width / 2 + xOffset,
                tool.getScreenSize().height / 2 - frame.getSize().height / 2 + yOffset);
    }

    /**
     * *************************** CRAMMING STUFF ON THE EVENT THREAD SUB SYSTEM START
     * *********************
     */

    /**
     * runs the current runnable on the event thread.
     * 
     * @param runnable -
     *            code to run on event thread.
     */
    public static void invokeLater(final Runnable runnable) {
        EventQueue.invokeLater(runnable);
    }
    
    public static Future invokeAsynchronously(final CancellableCallable callable, CallListener listener ) {
        CallableFutureGlue glueBall = new CallableFutureGlue(callable, listener);
        invokeLater(glueBall);
        return glueBall;
    }
    
    private static class CallableFutureGlue implements Future, Runnable {
        CancellableCallable callable;
        CallListener listener;
        private boolean cancelled;
        private boolean done;
        
        private CallableFutureGlue(CancellableCallable callable, CallListener listener) {
            this.callable = callable;
            this.listener = listener;
        }
        
        public void run() {
            try {
                Object result = callable.call();
                if (doCancel())
                    return;
                listener.handleResult(result);
            } catch (Exception ex) {
                if (doCancel())
                    return;
                listener.handleException(ex);
            } finally {
                listener.handleFinally();
            }
        }
        
        private synchronized boolean doCancel() {
            if (isCancelled())
                return true;
            done = true;
            listener.handleCancel();
            return false;
        }
        
        public synchronized boolean cancel() {
            if (done || cancelled)
                return false;
            cancelled = true;
            callable.cancel();
            return true;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            //can't interrupt since task is on event thread!
            return cancel();
        }

        public synchronized boolean isCancelled() {
            return cancelled;
        }

        public synchronized boolean isDone() {
            return done;
        }
        
    }

    public static boolean isEventDispatchThread() {
        return EventQueue.isDispatchThread();
    }

    public static void invokeAndWait(final Runnable runnable) throws InterruptedException {
        try {
            EventQueue.invokeAndWait(runnable);
        } catch (InvocationTargetException e) {
            e.printStackTrace(); //?
        }
    }
    
    
    /////////////// time \\\\\\\\\\\
    private static final int MINUTE = 1000 * 60;

    private static final int HOUR = MINUTE * 60;

    private static final int DAY = HOUR * 24;

    private static final int WEEK = DAY * 7;
    /**
     * Returns the uptime as a pre-formated string
     */
    public static String getLongAsTime(long number) {
        if (number == -1)
            return "-";
        if (number == -2)
            return "N/A";
        if (number < 0)
            return "Err";
    
        long numberTemp = number; //number comes from super.
    
        long weeks = numberTemp / WEEK;
        numberTemp %= WEEK;
    
        long days = numberTemp / DAY;
        numberTemp %= DAY;
    
        long hours = numberTemp / HOUR;
        numberTemp %= HOUR;
    
        long minutes = numberTemp / MINUTE;
        numberTemp %= MINUTE;
    
        //return h:MM
        //Ddays, h:MM
        //Wweeks
        //Wweeks Ddays
        return (weeks != 0 ? weeks + "weeks " : "") + (days != 0 ? days + "days " : "")
                + (weeks == 0 ? hours + ":" : "")
                + (weeks == 0 ? (minutes < 10 ? "0" + minutes : minutes + "") : "");
    
    }
}