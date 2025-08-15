package com.myster.ui;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JMenu;

import com.myster.application.MysterGlobals;
import com.myster.ui.menubar.MysterMenuBar;
import com.myster.ui.menubar.MysterMenuFactory;
import com.myster.ui.menubar.MysterMenuItemFactory;
import com.myster.ui.menubar.event.NullAction;

/**
 * This class is responsible for managing the number and order of all Myster
 * windows. Unfortunately it has become overgrown and is now also managing the
 * "Windows" menu. It also is way too coupled with MysterFrame.
 */
public class WindowManager {
    private final Map<MysterFrame, JMenu> windowMenuMap = new HashMap<>();
    private final List<MysterFrame> windows = new ArrayList<>();

    private final JMenu noFrameMenu;
    private final List<MysterMenuItemFactory> menuItems;
    private final List<MysterMenuItemFactory> finalMenu;

    private MysterFrame frontMost;
    private final List<WindowListener> listeners = new ArrayList<>();

    public interface WindowListener {
        public void windowCountChanged(int windowCount);
    }
    
    public WindowManager() {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Should be on the EDT");
        }


        menuItems = new ArrayList<>();

        menuItems.add(new MysterMenuItemFactory("Cycle Windows",
                                                new CycleWindowsHandler(),
                                                KeyEvent.VK_1));
        menuItems.add(new MysterMenuItemFactory("Stack Windows", new StackWindowsHandler()));
        menuItems.add(new MysterMenuItemFactory("-", new NullAction()));

        finalMenu = new ArrayList<MysterMenuItemFactory>();

        updateMenu();
        
        // it's fine this is not updated since frameless menu is for when no other windows are showing
        if (MysterGlobals.ON_MAC) {
            noFrameMenu =  (new MysterMenuFactory("Windows", finalMenu)).makeMenu(null);
        } else {
            noFrameMenu = null;
        }
    }
    
    public void addMenus(MysterMenuBar mysterMenuBar) {
        mysterMenuBar.addMenu(new MysterMenuFactory("Windows", finalMenu) {
            @Override
            public JMenu makeMenu(JFrame frame) {
                return getCorrectWindowsMenu(frame);
            }
        });
    }
    
    public void addWindowListener(WindowListener listener) {
        listeners.add(listener);
    }
    
    private void fireWidowListenerEvent() {
        for (WindowListener l: listeners) {
            l.windowCountChanged(windows.size());
        }
    }

    protected  void addWindow(MysterFrame frame) {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Should be on the EDT");
        }

        if (!windows.contains(frame)) {
            windows.add(frame);
            windowMenuMap.put(frame, (new MysterMenuFactory("Windows", finalMenu)).makeMenu(frame));
            // Timer t=new Timer(doUpdateClass, 1);//might cause deadlocks.
            updateMenu();
            fireWidowListenerEvent();
        }
    }

     void removeWindow(MysterFrame frame) {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Should be on the EDT");
        }

        boolean yep = windows.remove(frame);
        windowMenuMap.remove(frame);
        if (yep) {
            // Timer t=new Timer(doUpdateClass, 1); //might cause deadlocks.
            updateMenu();
            
            fireWidowListenerEvent();
            if (windows.size() == 0) {
                // TODO: Fix this hack.
                if (MysterGlobals.ON_LINUX) { // hack hack hack!
                    MysterGlobals.quit();
                }
            }
        }
    }
     
     public List<MysterFrame> getWindowListCopy() {
         if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Should be on the EDT");
        }

        return new ArrayList<>(windows);
     }

    public void updateMenu() {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Should be on the EDT");
        }

        finalMenu.clear();

        for (int i = 0; i < menuItems.size(); i++) {
            finalMenu.add(menuItems.get(i));
        }

        // separator
        finalMenu.add(new MysterMenuItemFactory());

        for (MysterFrame frame : windows) {
            finalMenu.add(new MysterMenuItemFactory(frame.getTitle(),
                                                    new OtherWindowHandler(frame)));
        }

        for (JMenu menu : windowMenuMap.values()) {
            fixMenu(menu);
        }
    }

    private  void fixMenu(JMenu menu) {
        for (int i = menu.getItemCount(); i > 3; i--) {
            menu.remove(i - 1);
        }

        for (MysterFrame frame : windows) {
            new MysterMenuItemFactory(frame.getTitle(), new OtherWindowHandler(frame))
                    .makeMenuItem(frame, menu);
        }
    }
    
    private JMenu getCorrectWindowsMenu(Frame frame) {
        JMenu menu = windowMenuMap.get(frame);
        if (menu == null) {
            if (noFrameMenu != null) {
                return noFrameMenu;
            }

            throw new IllegalStateException("This frame has no windows menu and we're not on MacOS!"
                    + frame.getTitle());
        }
        return menu;
    }

     void setFrontWindow(MysterFrame frame) {
        frontMost = frame;
    }

    public  MysterFrame getFrontMostWindow() {
        return frontMost;
    }



    private class CycleWindowsHandler implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            List<MysterFrame> windows = WindowManager.this.windows;

            synchronized (windows) {
                if (windows.size() <= 0)
                    return; // (could happen if none-tracked window is
                // frontmost)

                int index = windows.indexOf(getFrontMostWindow());

                if (index == -1) {
                    index = 0;
                }

                MysterFrame frame;
                int counter = 0;
                do {
                    index++;
                    if (index >= windows.size()) {
                        index = 0;
                    }

                    frame = windows.get(index);
                    if (counter > windows.size()) {
                        throw new IllegalStateException("No MysterFrames with menu bars exist and yet we have been asked to do a window cycle!");
                    }
                    counter++;
                } while (!frame.isMenuBarEnabled());
                frame.toFrontAndUnminimize();
            }
        }
    }

    private class StackWindowsHandler implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            List<MysterFrame> windows = WindowManager.this.windows;

            final int MOD = 7;

            synchronized (windows) {
                for (int i = 0; i < windows.size(); i++) {
                    (windows.get(i))
                            .setLocation(new java.awt.Point(((i % MOD) * 20) + 10,
                                                            (i % MOD) * 20 + ((i / MOD) * 20) + 10
                                                                    + (windows.get(i))
                                                                            .getInsets().top));
                }

                for (MysterFrame mysterFrame : windows) {
                    mysterFrame.toFront();
                }
            }
        }
    }

    private static class OtherWindowHandler implements ActionListener {
        MysterFrame frame;

        public OtherWindowHandler(MysterFrame frame) {
            this.frame = frame;
        }

        public void actionPerformed(ActionEvent e) {
            frame.toFront();
        }
    }
}