package com.general.util;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import com.myster.application.MysterGlobals;


public class Util { //This code was taken from an Apple Sample Code package,
    private static final Logger log = Logger.getLogger(Util.class.getName());
    public static final String SEPARATOR = "---------------------------------";

    @SuppressWarnings("unchecked")
    public static JComboBox<String> addSeparatorSupport(JComboBox<String> box) {
        // Capture the original renderer
        final ListCellRenderer<String> originalRenderer = ((ListCellRenderer<String>) box.getRenderer());
        
        // Create a new renderer that wraps the original one
        box.setRenderer(new ListCellRenderer<String>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends String> list, String value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                if (SEPARATOR.equals(value)) {
                    var separator = new javax.swing.JPopupMenu.Separator();
                    separator.setEnabled(false); 
                    return separator;
                }
                return originalRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });
        
        box.setModel(new DefaultComboBoxModel<String>() {
            public void setSelectedItem(Object anObject) {
                if (!SEPARATOR.equals(anObject)) {
                    super.setSelectedItem(anObject);
                }
            }
        });
        
        return box;
    }
    
    public static boolean isSystemDarkTheme() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("linux")) {
            // Try to detect GTK dark theme
            try {
                Process process = Runtime.getRuntime()
                        .exec(new String[] { "gsettings", "get", "org.gnome.desktop.interface",
                                "gtk-theme" });

                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process
                        .getInputStream()))) {
                    String theme = reader.readLine();
                    if (theme != null) {
                        theme = theme.toLowerCase();
                        // Remove quotes if present
                        theme = theme.replaceAll("'", "");
                        return theme.contains("dark");
                    }
                }
            } catch (Exception ex) {
                Util.log.info("Could not detect GTK theme: " + ex.getMessage());
            }
        } else if (osName.contains("mac")) {
            // For macOS we can check system appearance
            try {
                Process process = Runtime.getRuntime()
                        .exec(new String[] { "defaults", "read", "-g", "AppleInterfaceStyle" });

                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process
                        .getInputStream()))) {
                    String style = reader.readLine();
                    return "Dark".equalsIgnoreCase(style);
                }
            } catch (Exception ex) {
                Util.log.info("Could not detect macOS dark mode: " + ex.getMessage());
            }
        } else if (osName.contains("windows")) {
            try {
                Process process = Runtime.getRuntime()
                        .exec(new String[] { "reg", "query",
                                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                                "/v", "AppsUseLightTheme" });

                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process
                        .getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("AppsUseLightTheme")) {
                            // Output looks like:
                            // " AppsUseLightTheme REG_DWORD 0x0"
                            String[] parts = line.trim().split("\\s+");
                            String value = parts[parts.length - 1]; // last
                                                                    // token
                            return value.equalsIgnoreCase("0x0"); // 0 = dark, 1
                                                                  // = light
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Could not detect Windows dark mode: " + ex.getMessage());
            }
            // Default to light theme if unknown
            return false;

        }

        return false; // Default to light theme if we can't detect
    }

    /**
     * Reveals the specified file in the system file manager (Finder, Explorer, etc.).
     * On macOS, uses 'open -R' to highlight the file.
     * On Windows, uses 'explorer /select,' to highlight the file.
     * On Linux, attempts to use the desktop environment's file manager with selection support,
     * falling back to opening the parent directory if selection is not supported.
     * 
     * @param file the file to reveal in the file manager
     * @return true if the operation was successful, false otherwise
     */
    public static boolean revealFileInFileManager(File file) {
        if (file == null || !file.exists()) {
            log.warning("Cannot reveal file: file is null or does not exist");
            return false;
        }

        try {
            if (MysterGlobals.ON_MAC) {
                // macOS: use 'open -R' to reveal the file in Finder
                Runtime.getRuntime().exec(new String[] { "open", "-R", file.getAbsolutePath() });
                return true;
            } else if (MysterGlobals.ON_WINDOWS) {
                // Windows: use 'explorer /select,' to highlight the file
                Runtime.getRuntime().exec(new String[] { "explorer", "/select,", file.getAbsolutePath() });
                return true;
            } else if (MysterGlobals.ON_LINUX) {
                // Linux: Try various file managers with selection support
                
                // Try dbus-based file managers (most modern Linux desktops)
                if (tryLinuxDbusReveal(file)) {
                    return true;
                }
                
                // Try specific file managers
                String[] fileManagers = {
                    "nautilus",    // GNOME
                    "dolphin",     // KDE
                    "thunar",      // XFCE
                    "nemo",        // Cinnamon
                    "caja"         // MATE
                };
                
                for (String fm : fileManagers) {
                    if (tryLinuxFileManager(fm, file)) {
                        return true;
                    }
                }
                
                // Fallback: open parent directory using Desktop API
                File parent = file.getParentFile();
                if (parent != null && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(parent);
                    return true;
                }
            }
        } catch (IOException e) {
            log.warning("Failed to reveal file in file manager: " + e.getMessage());
        }

        // Final fallback: try to open parent directory with Desktop API
        try {
            File parent = file.getParentFile();
            if (parent != null && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(parent);
                return true;
            }
        } catch (IOException e) {
            log.warning("Failed to open parent directory: " + e.getMessage());
        }

        return false;
    }

    /**
     * Attempts to reveal a file using D-Bus on Linux systems that support org.freedesktop.FileManager1
     */
    private static boolean tryLinuxDbusReveal(File file) {
        try {
            // Use dbus-send to call the ShowItems method on the file manager
            String uri = file.toURI().toString();
            Process process = Runtime.getRuntime().exec(new String[] {
                "dbus-send",
                "--session",
                "--print-reply",
                "--dest=org.freedesktop.FileManager1",
                "/org/freedesktop/FileManager1",
                "org.freedesktop.FileManager1.ShowItems",
                "array:string:" + uri,
                "string:"
            });
            
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            // D-Bus not available or command failed
            return false;
        }
    }

    /**
     * Attempts to open a file with a specific Linux file manager
     */
    private static boolean tryLinuxFileManager(String fileManager, File file) {
        try {
            // Check if the file manager is available
            Process checkProcess = Runtime.getRuntime().exec(new String[] { "which", fileManager });
            if (checkProcess.waitFor() != 0) {
                return false; // File manager not installed
            }

            // Try to execute the file manager with the file or its parent directory
            String[] command;
            if ("nautilus".equals(fileManager) || "nemo".equals(fileManager)) {
                // Nautilus and Nemo support --select
                command = new String[] { fileManager, "--select", file.getAbsolutePath() };
            } else if ("dolphin".equals(fileManager)) {
                // Dolphin supports --select
                command = new String[] { fileManager, "--select", file.getAbsolutePath() };
            } else {
                // Other file managers: just open the parent directory
                File parent = file.getParentFile();
                if (parent == null) {
                    return false;
                }
                command = new String[] { fileManager, parent.getAbsolutePath() };
            }

            Runtime.getRuntime().exec(command);
            return true;
        } catch (Exception e) {
            return false;
        }
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
        GraphicsDevice primary = 
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                               .getDefaultScreenDevice();
        GraphicsConfiguration gc = primary.getDefaultConfiguration();

        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Rectangle visible = new Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width  - insets.left - insets.right ,
            bounds.height - insets.top  - insets.bottom 
        );
        
        frame.setLocation(visible.x + visible.width / 2 - frame.getSize().width / 2 + xOffset,
                          visible.y + visible.height / 2 - frame.getSize().height / 2 + yOffset);
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