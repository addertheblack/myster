/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */
package com.myster.client.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JPanel;

public class FileInfoPane extends JPanel {
    private static final int HOFFSET = 3;
    private static final int VOFFSET = 25;

    private Map<String, String> keyvalue;

    public FileInfoPane() {
        keyvalue = new HashMap<String, String>();
    }

    public void display(Map<String, String> k) {
        keyvalue = k;
        repaint();
    }

    public void paintComponent(Graphics g) {
        g.setColor(getBackground());
        Rectangle clipBounds = g.getClipBounds();
        g.fillRect((int) clipBounds.getX(), (int) clipBounds.getY(), (int) clipBounds.getWidth(),
                (int) clipBounds.getHeight());
        FontMetrics metric = getFontMetrics(getFont());
        int vertical = metric.getHeight() + 3;
        g.setColor(Color.black);
        int i = 0;
        for (Entry<String, String> entry : keyvalue.entrySet()) {
            if (entry.getKey().equals("size")) { // hack to
                // show
                // size as
                // bytes string
                // like
                // XXXbytes or
                // XXXMB
                try {
                    g.drawString("" + entry.getKey() + " : "
                            + com.general.util.Util
                                    .getStringFromBytes(Long.parseLong(entry.getValue())),
                                 HOFFSET,
                                 VOFFSET + i * vertical);
                } catch (NumberFormatException ex) {
                    g.drawString("" + entry.getKey() + " : " + entry.getValue(),
                                 HOFFSET,
                                 VOFFSET + i * vertical);
                }
            } else {
                g.drawString("" + entry.getKey() + " : " + entry.getValue(),
                             HOFFSET,
                             VOFFSET + i * vertical);
            }

            i++;
        }
        // Util.getStringFromBytes(Long.valueOf((String)keyvalue.valueAt(i)).longValue())

    }

    public Map<String, String> getData() {
        return keyvalue;
    }

    public void clear() {
        keyvalue.clear();
        repaint();
    }

    public Dimension getMinimumSize() {
        return new Dimension(0, 0);
    }

    public Dimension getPreferredSize() {
        return new Dimension(10, 10);
    }
}