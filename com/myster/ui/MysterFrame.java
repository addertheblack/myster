package com.myster.ui;

import java.awt.Frame;
import java.awt.Image;
import java.awt.MenuBar;
import java.awt.Point;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.myster.menubar.MysterMenuBar;
import com.myster.menubar.event.MenuBarListener;

public class MysterFrame extends Frame {
    static int xStart = 5;

    static int yStart = 5;

    MenuBarListener menuListener;

    private boolean menuBarEnabled = true;

    public MysterFrame() {
        super();//explicit good

        initEvents();
    }

    public MysterFrame(String windowName) {
        super(windowName);

        setTitle(windowName);

        initEvents();
    }

    public void setTitle(String windowName) {
        super.setTitle(com.myster.Myster.ON_LINUX ? windowName + " - Myster" : windowName);
        WindowManager.updateMenu();
    }

    private static synchronized Point getWindowStartingLocation() {
        Point location = new Point(xStart, yStart);
        xStart += 20;
        yStart += 20;

        if (xStart > 250) {
            xStart = 0;
            yStart = 0;
        }

        return location;
    }

    private void initEvents() {
        Image image =  com.general.util.Util.loadImage("myster_logo.gif", this, MysterFrame.class.getResource("myster_logo.gif"));
        if (image != null) {
            setIconImage(image);
        }
        
        setLocation(getWindowStartingLocation());

        addWindowListener(new WindowListener() {
            public void windowOpened(WindowEvent e) {

            }

            public void windowClosing(WindowEvent e) {
                WindowManager.removeWindow(MysterFrame.this);
                MysterMenuBar.removeMenuListener(menuListener);
            }

            public void windowClosed(WindowEvent e) {
                WindowManager.removeWindow(MysterFrame.this);
                MysterMenuBar.removeMenuListener(menuListener);
            }

            public void windowIconified(WindowEvent e) {

            }

            public void windowDeiconified(WindowEvent e) {

            }

            public void windowActivated(WindowEvent e) {
                WindowManager.setFrontWindow(MysterFrame.this);
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
                WindowManager.removeWindow(MysterFrame.this);
                MysterMenuBar.removeMenuListener(menuListener);
            }
        });

        menuListener = new MenuBarListener() {
            public void stateChanged(com.myster.menubar.event.MenuBarEvent e) {
                setNewMenuBar(e.makeNewMenuBar(MysterFrame.this));
            }
        };

    }

    public void show() {
        WindowManager.addWindow(MysterFrame.this);

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
                MysterMenuBar.removeMenuListener(menuListener);
            }

            setMenuBar(null);
        } else {
            if (isVisible()) {
                enableMenuBar();
            }
        }
    }

    private void enableMenuBar() {
        MysterMenuBar.addMenuListener(menuListener);
        setMenuBar(MysterMenuBar.getFactory().makeMenuBar(MysterFrame.this));
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

            method.invoke(this, new Object[] { new Integer(0) }); // 0 == normal.. Cannot use constant becaus eit doens't exist in 1.1
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

    private void setNewMenuBar(MenuBar newMenuBar) {
        MenuBar oldMenuBar = getMenuBar();

        if (oldMenuBar == null) {
            setMenuBar(newMenuBar);
        } else {
            System.out.println("Swapped menus");
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