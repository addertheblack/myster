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

    private final MysterPreferences prefs;

    public WindowLocationKeeper(MysterPreferences p) {
        prefs = p;
    }

    /**
     * Adds a Frame object to be *tracked*. If the frame is visible it's
     * location is automatically stored in the prefs else it won't be.
     */
    public void addFrame(MysterFrame frame, String key) {
        if (key.indexOf("/") != -1)
            throw new RuntimeException("Key cannot contain a \"/\"!");
        
        final String privateID = UUID.randomUUID().toString();

        if (frame.isVisible()) {
            saveLocation(frame, key, privateID);
        }

        frame.addComponentListener(new ComponentListener() {
            public void componentResized(ComponentEvent e) {
                if (((Component) (e.getSource())).isVisible())
                    saveLocation(((Component) (e.getSource())), key, privateID);
            }

            public void componentMoved(ComponentEvent e) {
                if (((Component) (e.getSource())).isVisible())
                    saveLocation(((Component) (e.getSource())), key, privateID);
            }

            public void componentShown(ComponentEvent e) {
                saveLocation(((Component) (e.getSource())), key, privateID);
            }

            public void componentHidden(ComponentEvent e) {
                deleteLocation(((Component) (e.getSource())), key, privateID);
            }

        });
        
        frame.addWindowListener(new WindowAdapter() {
        	public void windowClosing(WindowEvent e) {
        		deleteLocation(((Component) (e.getSource())), key, privateID);
        	}
        	
        	public void windowClosed(WindowEvent e) {
        		deleteLocation(((Component) (e.getSource())), key, privateID);
        	}
        });
    }

    private void saveLocation(Component c, String key, String id) {
        prefs.getPreferences().node(PREF_NODE_NAME).node(key).put(id, rect2String(c.getBounds()));
    }
    
    private void deleteLocation(Component c, String key, String id) {
        prefs.getPreferences().node(PREF_NODE_NAME).node(key).remove(id);
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

    public synchronized Rectangle[] getLastLocs(String p_key) {
        Preferences node = prefs.getPreferences().node(PREF_NODE_NAME).node(p_key);
        String[] keyList;
        try {
            keyList = node.keys();
        } catch (BackingStoreException e) {
            LOGGER.severe("Could not get list of window locations for key " + p_key + " because of BackingStoreException " + e);
            
            return new Rectangle[0];
        }

        Rectangle[] rectangles = new Rectangle[keyList.length];
        
        for (int i = 0; i < keyList.length; i++) {
            Rectangle rectangle = string2Rect(node.get(keyList[i], "0,0,400,400"));
            if (!fitsOnScreen(rectangle))
                rectangle.setLocation(50, 50);
            
            rectangles[i] = rectangle;
            
            node.remove(keyList[i]);
            
            LOGGER.fine("Getting the last window location " + p_key + " " + rectangle.toString());
        }
        
        return rectangles;
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