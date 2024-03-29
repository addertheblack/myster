
package com.myster.ui.tray;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.logging.Logger;

import javax.swing.JFrame;

import com.myster.application.MysterGlobals;
import com.myster.server.ui.ServerStatsWindow;
import com.myster.ui.MysterFrame;

public class MysterTray {
    private static final Logger LOGGER = Logger.getLogger(MysterTray.class.getName());
    
    public static void init() {
    	if ( MysterGlobals.ON_MAC) {
    		return;
    	}
        if (SystemTray.isSupported()) {
            SystemTray t = SystemTray.getSystemTray();
            try {
                Dimension trayIconSize = t.getTrayIconSize();
                TrayIcon trayIcon = new TrayIcon(com.general.util.Util
                        .loadImage("myster_logo.gif",
                                   new JFrame(),
                                   MysterFrame.class.getResource("myster_logo.gif"))
                        .getScaledInstance(trayIconSize.width,
                                           trayIconSize.height,
                                           Image.SCALE_SMOOTH));
                trayIcon.setToolTip("Myster Open Source Server PR X");
                PopupMenu menu = new PopupMenu("Myster System Menu");
                
                MenuItem serverStats = new MenuItem("Server Stats");
                serverStats.addActionListener((e) -> {
                    ServerStatsWindow.getInstance().setVisible(true);
                    ServerStatsWindow.getInstance().toFrontAndUnminimize();
                });
                menu.add(serverStats);
                
                menu.addSeparator();
                
                MenuItem exitMenu = new MenuItem("Exit");
                exitMenu.addActionListener((e) -> MysterGlobals.quit());
                menu.add(exitMenu);
                
                trayIcon.setPopupMenu(menu);
                t.add(trayIcon);
                t.addPropertyChangeListener(null, null);
            } catch (AWTException exception) {
                exception.printStackTrace();
            }
        } else {
            LOGGER.info("SystemTray                   :not supported");
        }
    }
}
