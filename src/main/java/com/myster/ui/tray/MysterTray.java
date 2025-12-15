package com.myster.ui.tray;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import com.myster.application.MysterGlobals;
import com.myster.server.ui.ServerStatsWindow;
import com.myster.ui.MysterFrame;

public class MysterTray {
    private static final Logger log = Logger.getLogger(MysterTray.class.getName());
    
    /**
     * Determines the appropriate tray icon size for the current system.
     * On Linux systems, particularly XFCE, the reported size is often incorrect
     * and needs adjustment.
     */
    private static Dimension determineTrayIconSize(SystemTray systemTray) {
        Dimension trayIconSize = systemTray.getTrayIconSize();
        log.info("System reported tray icon size: " + trayIconSize);
        
        // Linux-specific adjustments for common desktop environments
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            String desktop = System.getenv("XDG_CURRENT_DESKTOP");
            String session = System.getenv("DESKTOP_SESSION");
            log.info("Desktop environment: " + desktop + ", Session: " + session);
            
            // XFCE and Mint XFCE often report incorrect sizes
            if ((desktop != null && desktop.toLowerCase().contains("xfce")) ||
                (session != null && session.toLowerCase().contains("xfce"))) {
                log.info("XFCE detected - adjusting tray icon size");
                
                // XFCE typically uses smaller icons than reported
                int adjustedWidth = Math.max(16, trayIconSize.width - 4);
                int adjustedHeight = Math.max(16, trayIconSize.height - 4);
                trayIconSize = new Dimension(adjustedWidth, adjustedHeight);
                
                log.info("Adjusted tray icon size for XFCE: " + trayIconSize);
            }
            
            // Fallback for unreasonable sizes
            if (trayIconSize.width > 48 || trayIconSize.height > 48 ||
                trayIconSize.width < 8 || trayIconSize.height < 8) {
                log.info("Unreasonable tray size detected, using common Linux default");
                trayIconSize = new Dimension(22, 22); // Common Linux tray size
            }
        }
        
        log.info("Final tray icon size: " + trayIconSize);
        return trayIconSize;
    }
    
    public static void init() {
        if (MysterGlobals.ON_MAC) {
            return;
        }
        if (SystemTray.isSupported()) {
            SystemTray t = SystemTray.getSystemTray();
            try {
                Dimension trayIconSize = determineTrayIconSize(t);
                
                // Load image using ImageIO for better control
                URL imageUrl = MysterFrame.class.getResource("myster_logo.gif");
                if (imageUrl == null) {
                    log.severe("Could not find myster_logo.gif resource");
                    return;
                }
                
                BufferedImage originalImage = ImageIO.read(imageUrl);
                if (originalImage == null) {
                    log.severe("Failed to load myster_logo.gif");
                    return;
                }
                
                log.info("Original image size: " + originalImage.getWidth() + "x" + originalImage.getHeight());
                
                // Create properly scaled image for tray
                Image scaledImage = originalImage.getScaledInstance(
                    trayIconSize.width, 
                    trayIconSize.height, 
                    Image.SCALE_SMOOTH
                );
                
                TrayIcon trayIcon = new TrayIcon(scaledImage);
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
                
            } catch (AWTException exception) {
                log.severe("Failed to add tray icon: " + exception.getMessage());
                exception.printStackTrace();
            } catch (IOException exception) {
                log.severe("Failed to load tray icon image: " + exception.getMessage());
                exception.printStackTrace();
            }
        } else {
            log.info("SystemTray not supported");
        }
    }
}