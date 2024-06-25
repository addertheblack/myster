package com.general.util;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;


public class Util { //This code was taken from an Apple Sample Code package,
    public static Image loadImage(String filename, Component watcher) {
        if (filename != null) {
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
            return kilo + "KiB";
        }

        double megs = (double) kilo / 1024;
        if (megs < 1024) {
            String temp = "" + megs;
            return temp.substring(0, temp.indexOf(".") + 2) + "MiB";
        }

        double gigs = megs / 1024;
        if (gigs < 1024) {
            String temp = "" + gigs;
            return temp.substring(0, temp.indexOf(".") + 2) + "GiB";
        }

        double tera = gigs / 1024;
        String temp = "" + tera;
        return temp.substring(0, temp.indexOf(".") + 2) + "TiB";
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
            bytes[i / 2] = (byte) (Short.parseShort(hash.substring(i, i + 2), 16));
        }

        return bytes;
    }

    public static String asHex(byte hash[]) {
        StringBuilder buf = new StringBuilder(hash.length * 2);
    
        for (int i = 0; i < hash.length; i++) {
            if ((hash[i] & 0xff) < 0x10)
                buf.append("0");
    
            buf.append(Long.toString(hash[i] & 0xff, 16));
        }
    
        return buf.toString();
    }

    public static String getMD5Hash(String input) {
        try {
            // Create MessageDigest instance for MD5
            MessageDigest digest = MessageDigest.getInstance("MD5");
            
            // Update input string in message digest
            digest.update(input.getBytes(), 0, input.length());

            // Convert the byte array to hex format
            return asHex(digest.digest());
            
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Centers the frame passed on the screen. The offsets are to offset the frame from perfect
     * center. If you want it centered call this with offsets of 0,0
     */
    public static void centerFrame(Window frame, int xOffset, int yOffset) {
        Toolkit tool = Toolkit.getDefaultToolkit();
        frame.setLocation(tool.getScreenSize().width / 2 - frame.getSize().width / 2 + xOffset,
                tool.getScreenSize().height / 2 - frame.getSize().height / 2 + yOffset);
    }
//
//    public static PublicKey covertToPublicKey(String identity) {
//        try {
//            byte[] encodedPublicKey = Base64.getDecoder().decode(identity);
//            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedPublicKey);
//            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
//            
//            return keyFactory.generatePublic(keySpec);
//        } catch (NoSuchAlgorithmException | InvalidKeySpecException
//                | IllegalArgumentException exception) {
//            exception.printStackTrace();
//            
//            return null;
//        }
//    }

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
    
    public static void invokeNowOrLater(final Runnable runnable) {
        if (EventQueue.isDispatchThread()) {
            try {
                runnable.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            EventQueue.invokeLater(runnable);
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
    
    public static void invokeAndWaitForAnyThread(final Runnable runnable) throws InterruptedException {
        try {
            if (EventQueue.isDispatchThread()) {
                runnable.run();
            } else {
                EventQueue.invokeAndWait(runnable);
            }
        } catch (InvocationTargetException e) {
            e.printStackTrace(); //?
        }
    }

    /**
     * This method gets around much of the idiocy of invoke and wait. If it's a
     * runtime exception we just rethrow it. If it's an interrupt exception we
     * throw it as a RuntimeInterruptedException. If it's any other exception we
     * throw an UnexpectedException.
     */
    public static void invokeAndWaitNoThrows(final Runnable runnable) {
        try {
            EventQueue.invokeAndWait(runnable);
        } catch (InvocationTargetException e) {
            Throwable ex = e.getCause();
            if (ex instanceof RuntimeException exception) {
                throw exception;
            }
            
            throw new UnexpectedException(ex);
        } catch (InterruptedException exception) {
           throw new RuntimeInterruptedException(exception);
        }
    }
    
    /**
     * Runs a callable on the EDT.
     * 
     * @see Util#invokeAndWaitNoThrows(Runnable)
     */
    @SuppressWarnings("unchecked")
    public static <T> T callAndWaitNoThrows(final Callable<T> callable) {
        final Object[] result = new Object[1];
        invokeAndWaitNoThrows(() -> {
            try {
                result[0] = callable.call();
            } catch (Exception exception) {
                throw new UnexpectedException(exception);
            }
        });

        return (T) result[0];
    }

    public static String publicKeyToString(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
    
    public static Optional<PublicKey> publicKeyFromString(String publicKeyString) {
        byte[] encodedPublicKey = Base64.getDecoder().decode(publicKeyString);

        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedPublicKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return Optional.of(keyFactory.generatePublic(keySpec));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException exception) {
            exception.printStackTrace();

            return Optional.empty();
        }
    }
    
    /**
     * Because streams are a great concept that's too much of a pain in the ass to use.
     * 
     * (also this is faster)
     */
    public static <T> List<T> filter(Collection<T> input, Predicate<T> p) {
        List<T> result = new ArrayList<>(input.size());
        
        for (T t : input) {
            if (p.test(t)) {
                result.add(t);
            }
        }
        
        return result;
    }
    
    /**
     * Because streams are a great concept that's too much of a pain in the ass to use.
     * 
     * (also this is faster)
     */
    public static <F, T> List<T> map(Collection<F> input, Function<F, T>m) {
        List<T> result = new ArrayList<>(input.size());

        for (F f : input) {
            result.add(m.apply(f));
        }

        return result;
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