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
import java.awt.Dimension;
import java.awt.Graphics;

public interface MCRowThemeInterface {
    public int getHeight();

    public void paint(Graphics g, MCListItemInterface i, RowStats row,
            int yoffset, int xoffset, int itemnumber, Dimension dimension);

    public int getPadding();

    public Color getBackground();
}