/*
 * MCList.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.Locale;

public class DefaultMCRowTheme implements MCRowThemeInterface {
    private Component component; //for font metrics

    //font stuff
    private FontMetrics tempfont;

    private int height;

    private int ascent;

    //private int descent;

    //Colors
    private static final Color lightselectedc = new Color(205, 205, 255),
            selectedc = new Color(200, 200, 250), lightc = new Color(240, 240,
                    240), c = new Color(230, 230, 230);

    private Color backgroundColor = lightc;

    private boolean filterNonEnglish;
    
    public DefaultMCRowTheme(Component c) {
        component = c;

        filterNonEnglish = Locale.getDefault().getDisplayLanguage().equals(
                Locale.ENGLISH.getDisplayLanguage());
        filterNonEnglish = false;
    }

    public int getHeight(int padding, int internalPadding) {
        try {
            return component.getFontMetrics(component.getFont()).getHeight()
                    + padding + (internalPadding * 2);
        } catch (Exception ex) {
            return 20;
        }
    }

    public Color getBackground() {
        return backgroundColor;
    }

    public void paint(Graphics g, MCListItemInterface item, RowStats rowStats,
            int yoffset, int xoffset, int itemnumber, Dimension dimension) {
        int padding = rowStats.getPadding();
        int internalPadding = rowStats.getInternalPadding();
        
        try {
            if (tempfont == null) {
                tempfont = component.getFontMetrics(component.getFont()); //uses
                                                                          // default
                                                                          // font
                height = tempfont.getHeight();
                ascent = tempfont.getAscent();
                //descent= tempfont.getDescent();
            }
        } catch (Exception ex) {
            tempfont = null;
            height = 10;
            ascent = 10;
            //descent= 10;
        }
        
        //Clear secleciton to white:
        g.setColor(Color.white);
        g.fillRect(0, yoffset, Math.max(rowStats.getTotalLength(), dimension.width), getHeight(padding, internalPadding));

        //Paint boxes - padding on ONE SIDE!
        if (itemnumber % 2 == 0) {
            if (item.isSelected())
                g.setColor(selectedc);
            else
                g.setColor(c);
        } else {
            if (item.isSelected())
                g.setColor(lightselectedc);
            else
                g.setColor(lightc);
        }

        int hozoffset = -xoffset;
        for (int i = 0; i < rowStats.getNumberOfColumns(); i++) {
//            g.fillRect(padding + hozoffset, yoffset + padding, row
//                    .getWidthOfColumn(i), height);
            paintRow(g, hozoffset, hozoffset + padding + rowStats.getWidthOfColumn(i), padding, internalPadding, yoffset, height);
            hozoffset += padding + rowStats.getWidthOfColumn(i) + (2* internalPadding);
        }
        
        if (hozoffset + xoffset < dimension.width ) {
            paintRow(g, hozoffset, xoffset + dimension.width, padding, internalPadding, yoffset, height);
        }
        
        //Add text at proper off set
        //->Get correct size!!!!! ^%&!
        g.setColor(Color.black);

        hozoffset = -xoffset;
        int yplace = yoffset + ascent;
        for (int i = 0; i < rowStats.getNumberOfColumns(); i++) {
            g.drawString(makeFit(item.getValueOfColumn(i).toString(), rowStats
                    .getWidthOfColumn(i)), hozoffset + padding + internalPadding, yplace + internalPadding);
            hozoffset += padding + rowStats.getWidthOfColumn(i) + (2 * internalPadding);
        }
    }
    
    private void paintRow(Graphics g, int hozoffset, int end, int padding, int internalPadding, int yOffset, int height) {
        int doubleInternalPadding = 2 * internalPadding;
        int width = end - hozoffset - padding;
        g.fillRect(padding + hozoffset, yOffset, width + doubleInternalPadding, height + doubleInternalPadding);
    }

    public String makeFit(String s, int size) {
        if (!component.isVisible())
            return s;

        if (tempfont == null) {
            tempfont = component.getFontMetrics(component.getFont()); //uses
                                                                      // default
                                                                      // font
            height = tempfont.getHeight();
            ascent = tempfont.getAscent();
            //descent= tempfont.getDescent();
        }

        if (filterNonEnglish) {
            char[] array = s.toCharArray();
            for (int i = 0; i < array.length; i++) {
                if (128 < array[i]) {
                    array[i] = '?';
                }
            }
            s = new String(array);
        }

        int i = 0;
        if (tempfont.stringWidth(s) > size - 8) {
            for (i = s.length(); ((tempfont.stringWidth(s.substring(0, i)
                    + "...") > size - 8) && i > 0); i--)
                ;
            return s.substring(0, i) + "...";
        }
        return s;
    }
}