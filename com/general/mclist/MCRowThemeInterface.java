/*
 * main.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

import java.awt.Color;
import java.awt.Graphics;

public interface MCRowThemeInterface {
    public int getHeight();

    public void paint(Graphics g, MCListItemInterface i, RowStats row,
            int yoffset, int xoffset, int itemnumber);

    public int getPadding();

    public Color getBackground();
}