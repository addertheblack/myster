package com.myster.ui;

import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.swing.JFrame;
import javax.swing.JMenuBar;

import com.myster.application.MysterGlobals;
import com.myster.ui.menubar.event.MenuBarListener;

public class MysterFrame extends JFrame {
    // how much to shift each new window
    private static final int DELTA = 20;
    // how far out before we wrap
    private static final int MAX_OFFSET = 250;
    // extra cushion from the very edge
    private static final int BUFFER = 10;

    // current offsets from the top-left of the visible area
    private static int offsetX = 0;
    private static int offsetY = 0;
    
    // end

    private final MysterFrameContext context;

    private MenuBarListener menuListener;
    private boolean menuBarEnabled = true;

    public MysterFrame(MysterFrameContext context) {
        this.context = context;
        initEvents();
    }

    public MysterFrame(MysterFrameContext context, String windowName) {
        super(windowName);
        this.context = context;

        setTitle(windowName);

        initEvents();
    }
    
    public final MysterFrameContext getMysterFrameContext() {
        return context;
    }
    
    public void setTitle(String windowName) {
        super.setTitle(MysterGlobals.ON_LINUX ? windowName + " - Myster" : windowName);
        context.windowManager().updateMenu();
    }

    private static synchronized Point getWindowStartingLocation() {
        // 1) Get primary screen's GraphicsConfiguration
        GraphicsDevice primary = 
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                               .getDefaultScreenDevice();
        GraphicsConfiguration gc = primary.getDefaultConfiguration();

        // 2) Figure otut the visible area (subtract OS insets)
        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Rectangle visible = new Rectangle(
            bounds.x + insets.left + BUFFER,
            bounds.y + insets.top  + BUFFER,
            bounds.width  - insets.left - insets.right  - 2*BUFFER,
            bounds.height - insets.top  - insets.bottom - 2*BUFFER
        );

        // 3) Compute the next origin
        Point next = new Point(visible.x + offsetX,
                               visible.y + offsetY);

        // 4) Advance and wrap if past MAX_OFFSET
        offsetX += DELTA;
        offsetY += DELTA;
        if (offsetX > MAX_OFFSET || offsetY > MAX_OFFSET) {
            offsetX = 0;
            offsetY = 0;
        }

        return next;
    }

    private void initEvents() {
        Image image =  com.general.util.IconLoader.loadImage("myster_logo.gif", MysterFrame.class.getResource("myster_logo.gif"));
        if (image != null) {
            setIconImage(image);
        }
        
        setLocation(getWindowStartingLocation());

        addWindowListener(new WindowListener() {
            public void windowOpened(WindowEvent e) {

            }

            public void windowClosing(WindowEvent e) {
                context.windowManager().removeWindow(MysterFrame.this);
                context.menuBar().removeMenuListener(menuListener);
            }

            public void windowClosed(WindowEvent e) {
                context.windowManager().removeWindow(MysterFrame.this);
                context.menuBar().removeMenuListener(menuListener);
            }

            public void windowIconified(WindowEvent e) {

            }

            public void windowDeiconified(WindowEvent e) {

            }

            public void windowActivated(WindowEvent e) {
                context.windowManager().setFrontWindow(MysterFrame.this);
            }

            public void windowDeactivated(WindowEvent e) {

            }
        });

        addComponentListener(new ComponentListener() {
            public void componentResized(ComponentEvent e) {

            }

            public void componentMoved(ComponentEvent e) {

            }

            public void componentShown(ComponentEvent e) {
                
            }

            public void componentHidden(ComponentEvent e) {
                context.windowManager().removeWindow(MysterFrame.this);
                context.menuBar().removeMenuListener(menuListener);
            }
        });

        menuListener = new MenuBarListener() {
            public void stateChanged(com.myster.ui.menubar.event.MenuBarEvent e) {
                setNewMenuBar(e.makeNewMenuBar(MysterFrame.this));
            }
        };

    }

    public void show() {
        context.windowManager().addWindow(MysterFrame.this);

        if (menuBarEnabled) {
            enableMenuBar();
        }
        
        super.show();
    }

    public void setMenuBarEnabled(boolean enable) {
        if (menuBarEnabled == enable)
            return;

        this.menuBarEnabled = enable;

        if (enable) {
            if (menuListener != null) {
                context.menuBar().removeMenuListener(menuListener);
            }

            setMenuBar(null);
        } else {
            if (isVisible()) {
                enableMenuBar();
            }
        }
    }

    private void enableMenuBar() {
        context.menuBar().addMenuListener(menuListener);
    }

    public boolean isMenuBarEnabled() {
        return menuBarEnabled;
    }

    /**
     * Shows the frame if not visible and de-iconifies it if possible as well.
     */
    public void toFrontAndUnminimize() {
        try {
            Method method = Frame.class.getMethod("setState", new Class[] { Integer.TYPE });

            // 0 == normal.. Cannot use constant because it doens't exist in 1.1
            method.invoke(this, new Object[] { 0 });
        } catch (SecurityException ex) {
            //ex.printStackTrace();
        } catch (NoSuchMethodException ex) {
            //ex.printStackTrace();
        } catch (IllegalArgumentException ex) {
            //ex.printStackTrace();
        } catch (IllegalAccessException ex) {
            //ex.printStackTrace();
        } catch (InvocationTargetException ex) {
            //ex.printStackTrace();
        }

        toFront();
    }

    private void setNewMenuBar(JMenuBar newMenuBar) {
        JMenuBar oldMenuBar = getJMenuBar();

        if (oldMenuBar == null) {
            setJMenuBar(newMenuBar);
        } else {
            int maxOldMenus = oldMenuBar.getMenuCount();
            int maxNewMenus = newMenuBar.getMenuCount();

            if (maxNewMenus > 0)
                oldMenuBar.add(newMenuBar.getMenu(0));

            for (int i = maxOldMenus - 1; i >= 0; i--) {
                oldMenuBar.remove(i);
            }

            for (int i = 1; i < maxNewMenus; i++) {
                oldMenuBar.add(newMenuBar.getMenu(0));
            }

        }
    }

    public void closeWindowEvent() {
        processWindowEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    public void close() {
        closeWindowEvent();
        setVisible(false);
    }

    public void dispose() {
        super.dispose();
    }
}