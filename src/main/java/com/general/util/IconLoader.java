package com.general.util;

import java.awt.Component;
import java.awt.Image;
import java.net.URL;
import java.util.logging.Logger;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

public final class IconLoader {
    private static final Logger LOGGER = Logger.getLogger(IconLoader.class.getName());

    private IconLoader() {}

    // Current: SVG via FlatLaf
    public static com.formdev.flatlaf.extras.FlatSVGIcon loadSvg(Class<?> clazz, String name) {
        com.formdev.flatlaf.extras.FlatSVGIcon flatSVGIcon = new com.formdev.flatlaf.extras.FlatSVGIcon(clazz.getResource(name + ".svg"));
        return flatSVGIcon;
    }

    // Future drop-in (if you leave FlatLaf): multi-res PNGs
    // Example naming: connect_16.png, connect_32.png, connect_48.png
    public static Icon loadMultiSizePng(String name) {
        Image i16 = new ImageIcon(IconLoader.class.getResource("/icons/" + name + "_16.png"))
                .getImage();
        Image i32 = new ImageIcon(IconLoader.class.getResource("/icons/" + name + "_32.png"))
                .getImage();
        Image i48 = new ImageIcon(IconLoader.class.getResource("/icons/" + name + "_48.png"))
                .getImage();

        java.awt.Image mri =
                new java.awt.image.BaseMultiResolutionImage(new Image[] { i16, i32, i48 });
        return new ImageIcon(mri);
    }

    public static Image loadImage(String filename, Component watcher) {
        if (filename == null)
            return null;

        // Use the caller's class to resolve resources, just like before
        Class<?> cls = (watcher != null ? watcher.getClass() : IconLoader.class);
        URL url = cls.getResource(filename);
        return IconLoader.loadImage(filename, url);
    }

    public static Image loadImage(String filename, URL url) {
        LOGGER.severe("loadImage() is loading  \"" + filename + "\"");

        if (filename == null)
            return null;

        if (url == null) {
            LOGGER.severe("loadImage() could not find \"" + filename + "\"");

            return null;
        }

        try {
            // Synchronous, fully decoded; returns BufferedImage (which is-a
            // Image)
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(url);
            if (img == null) {
                LOGGER.severe("loadImage(): unsupported or unreadable image format for \""
                        + filename + "\"");
            }
            return img; // drop-in: still an Image
        } catch (java.io.IOException e) {
            LOGGER.severe("loadImage(): " + e + " for \"" + filename + "\"");
            return null;
        }
    }
}