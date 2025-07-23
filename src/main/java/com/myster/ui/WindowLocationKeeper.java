package com.myster.ui;

import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.myster.pref.MysterPreferences;

/**
 * Creates a window keeper object for your class of window. A window keeper
 * takes upon itself the responsibility of managing the location preferences for
 * your window type. Simply create a window keeper for you class of window and
 * add every new frame of that class. Window keeper will keep track of that
 * window's location and visibility. In order to read back this information
 * simply call getLastLocs(..) for your window type. Window keeper will return
 * the locations for every window of that type that was on screen last time
 * Myster was quit. It will even makes sure the previous window locs are all on
 * screen when the routine is called (ie: not too far to the left or right).
 */
public class WindowLocationKeeper {
    private static final String PREF_NODE_NAME = "Window Locations and Sizes";
    
    private static final Logger LOGGER = Logger.getLogger(WindowLocationKeeper.class.getName());

    private static final String LOCATION = "location";
    private static final String VISIBLE = "visible";
    public static final boolean SINGLETON_WINDOW = true;
    public static final boolean MULTIPLE_WINDOWS = false;

    private final MysterPreferences prefs;

    public WindowLocationKeeper(MysterPreferences p) {
        prefs = p;
    }

    /**
     * Adds a Frame object to be *tracked*. If the frame is visible it's
     * location is automatically stored in the prefs else it won't be.
     * 
     * @param singletonWindow
     *            can be {@link #SINGLETON_WINDOW} or {@link #MULTIPLE_WINDOWS}.
     *            Multiple windows are for things like the search or client
     *            windows and singleton windows are for windows with only one
     *            expected instance.
     */
    public void addFrame(MysterFrame frame, String key, boolean singletonWindow) {
        if (key.indexOf("/") != -1)
            throw new RuntimeException("Key cannot contain a \"/\"!");
        
        final String privateID = UUID.randomUUID().toString();

        if (frame.isVisible()) {
            saveLocation(frame, key, privateID);
        }

        frame.addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (((Component) (e.getSource())).isVisible())
                    saveLocation(((Component) (e.getSource())), key, privateID);
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                if (((Component) (e.getSource())).isVisible())
                    saveLocation(((Component) (e.getSource())), key, privateID);
            }

            @Override
            public void componentShown(ComponentEvent e) {
                saveLocation(((Component) (e.getSource())), key, privateID);
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                hideComponent(key, privateID, singletonWindow);
            }

        });
        
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                hideComponent(key, privateID, singletonWindow);
            }

            @Override
        	public void windowClosed(WindowEvent e) {
                hideComponent(key, privateID, singletonWindow);
        	}
        });
    }
    
    private void hideComponent(String key, String id, boolean singletonWindow) {
        if (singletonWindow) {
            findWindowNode(key, id).putBoolean(VISIBLE, false);
        } else {
            deleteLocation(key, id);
        }
    }

    private void saveLocation(Component c, String key, String id) {
        findWindowNode(key, id).put(LOCATION, rect2String(c.getBounds()));
        findWindowNode(key, id).putBoolean(VISIBLE, true);
    }
    
    private void deleteLocation(String key, String id) {
        try {
            findWindowNode(key, id).removeNode();
        } catch (BackingStoreException _) {
            // ignore
        }
    }

    private Preferences findWindowNode(String key, String id) {
        return prefs.getPreferences().node(PREF_NODE_NAME).node(key).node(id);
    }

    /////////// STATIC SUB SYSTEM
    public static boolean fitsOnScreen(Rectangle windowBounds) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            Rectangle bounds = gc.getBounds();

            // subtract taskbar/menu bar insets
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            Rectangle visible = new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width  - insets.left - insets.right,
                bounds.height - insets.top  - insets.bottom
            );

            if (visible.contains(windowBounds)) {
                return true;
            }
        }
        return false;
    }

    public static record WindowLocation(Rectangle bounds, boolean visible) {}

    public synchronized WindowLocation[] getLastLocs(String p_key) {
        Preferences rootNode = prefs.getPreferences().node(PREF_NODE_NAME).node(p_key);
        String[] nodeNames;
        try {
            nodeNames = rootNode.childrenNames();
        } catch (BackingStoreException e) {
            LOGGER.severe("Could not get list of window locations for key " + p_key + " because of BackingStoreException " + e);
            return new WindowLocation[0];
        }

        var locations = new ArrayList<WindowLocation>();

        for (String nodeName : nodeNames) {
            Preferences node = rootNode.node(nodeName);
            boolean visible = node.getBoolean(VISIBLE, false);
            Rectangle rectangle = string2Rect(node.get(LOCATION, "0,0,400,400"));
            if (!fitsOnScreen(rectangle)) {
                rectangle.setLocation(50, 50);
            }
            locations.add(new WindowLocation(rectangle, visible));
            try {
                node.removeNode();
            } catch (BackingStoreException _) {
                // ignore, best effort
            }
            LOGGER.fine("Getting the last window location " + p_key + " " + rectangle.toString());
        }

        return locations.toArray(new WindowLocation[0]);
    }

    private static synchronized Rectangle string2Rect(String s) {
        int x, y, width, height;

        StringTokenizer tokenizer = new StringTokenizer(s, SEPERATOR, false);

        try {
            x = Integer.parseInt(tokenizer.nextToken());
            y = Integer.parseInt(tokenizer.nextToken());
            width = Integer.parseInt(tokenizer.nextToken());
            height = Integer.parseInt(tokenizer.nextToken());
        } catch (NoSuchElementException ex) {
            return new Rectangle(0, 0, 100, 100);
        } catch (NumberFormatException ex) {
            return new Rectangle(0, 0, 100, 100);
        }

        return new Rectangle(x, y, width, height);
    }

    private static final String SEPERATOR = ",";

    private static String rect2String(Rectangle rect) { //here for code reuse
        return "" + rect.x + SEPERATOR + rect.y + SEPERATOR + rect.width
                + SEPERATOR + rect.height;
    }
}