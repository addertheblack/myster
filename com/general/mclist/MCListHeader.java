/*
 * MCList.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Panel;

public class MCListHeader extends Panel {
    private MCList callback;

    private static final int HEIGHT = 17;

    private static final boolean USE_ROLL_OVER = true;

    //Book Keeping
    private int numberOfColumns;

    private String[] columnarray;

    private int[] columnWidthArray; // A value of -1 means value should default

    // to width of header string.

    private int sortby = 0;

    private int mouseOverColumn = -1;

    private static final int CLICK_LATITUDE = 5;

    private static final int MIN_COLUMN_WIDTH = 15;

    public MCListHeader(MCList c, int numberOfColumns) {
        callback = c;
        this.numberOfColumns = numberOfColumns;
        columnarray = new String[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++)
            columnarray[i] = "unnamed";

        initColumnWidthArray();
        repaint();

        MCListHeaderEventHandler temp = new MCListHeaderEventHandler(this);
        addMouseListener(temp);
        addMouseMotionListener(temp);
    }

    public void initColumnWidthArray() {
        int[] oldArray = {};
        if (columnWidthArray != null)
            oldArray = columnWidthArray; //line to take care of case where
        // columnWidthArray is not inited. See
        // constructor.
        int i;
        columnWidthArray = new int[numberOfColumns];
        for (i = 0; i < oldArray.length && i < columnWidthArray.length; i++) {
            columnWidthArray[i] = oldArray[i];
        }

        //If the column width array is larger than the previous array then init
        // those values
        for (; i < columnWidthArray.length; i++) {
            columnWidthArray[i] = -1;
        }
    }

    private Image im;

    private int lastwidth = -1;

    public void update2(Graphics g) {
        if (getSize().width != lastwidth) {
            im = null;
            lastwidth = getSize().width;
        }
        if (im == null) {
            im = createImage(getSize().width, getSize().height);
        }
        paint2(im.getGraphics());
        g.drawImage(im, 0, 0, getSize().width, getSize().height, this);
    }

    public void update(Graphics g) {
        paint(g);
    }

    public void paint(Graphics g) {
        update2(g);
    }

    public void paint2(Graphics g) {

        RowStats rowstats = getRowStats();
        int padding = rowstats.getPadding();
        int internalPadding = rowstats.getInternalPadding();
        int doubleInternalPadding = 2 * internalPadding;

        FontMetrics tempfont = callback.getFontMetrics(callback.getFont()); //uses
        // default
        // font
        int height = tempfont.getHeight();
        int ascent = tempfont.getAscent();
        int descent = tempfont.getDescent();

        g.setColor(getBackground());
        g.fillRect(0, 0, getSize().width, getHeight());

        //g.setColor(new Color(200, 200, 255));
        g.setColor(new Color(210,210,210));
        g.fillRect(0, 0, getSize().width, getHeight());

        int hozoffset = 0;
        for (int i = 0; i < columnarray.length; i++) {
            //Calculates the "Selected Column" and/or "Mouser Over" colors.
            paintTitle(g, hozoffset, hozoffset + rowstats.getWidthOfColumn(i) + padding
                    + doubleInternalPadding, padding, internalPadding, i == sortby,
                    mouseOverColumn == i);

            hozoffset += padding + rowstats.getWidthOfColumn(i) + doubleInternalPadding;
        }

        if (hozoffset < getSize().width) {
            paintTitle(g, hozoffset, getSize().width, padding, internalPadding, false, false);
        }

        g.setColor(Color.black);
        hozoffset = 0;
        int yplace = padding + ascent;
        for (int i = 0; i < numberOfColumns; i++) {
            g.drawString(makeFit(columnarray[i], rowstats.getWidthOfColumn(i)), hozoffset + padding
                    + 2 + internalPadding, yplace + internalPadding);
            hozoffset += padding + rowstats.getWidthOfColumn(i) + doubleInternalPadding;
        }
    }

    private void paintTitle(Graphics g, int hozoffset, int endPixel, int padding,
            int internalPadding, boolean isSelected, boolean isMouseOver) {
        //Calculates the "Selected Column" and/or "Mouser Over" colors.
        if (isSelected) {
            g.setColor(isMouseOver ? new Color(222, 222, 222) : new Color(210, 210, 210));
        } else {
            g.setColor(isMouseOver ? new Color(222, 222, 222) : new Color(210, 210, 210));
        }

        int width = endPixel - hozoffset - padding;
        g.fillRect(padding + hozoffset, padding, width, 2 * internalPadding + HEIGHT);
        
        int height = 2 * internalPadding + padding + HEIGHT;

        g.setColor(new Color(245, 245, 245));
        g.drawLine(padding + hozoffset, padding, padding + hozoffset + width - 1, padding);
        g.drawLine(padding + hozoffset, padding, padding + hozoffset, height - 1);
        g.setColor(new Color(150, 150, 150));
        g.drawLine(padding + hozoffset, height - 1, padding + hozoffset + width - 1, height - 1);
        g.drawLine(padding + hozoffset + width - 1, padding, padding + hozoffset + width - 1,
                height - 1);

        g.setColor(new Color(255, 255, 255));
        g.fillRect(padding + hozoffset, padding, 1, 1);
        g.setColor(new Color(215, 215, 215));
        g.fillRect(padding + hozoffset + width - 1, padding, 1, 1);
        g.setColor(new Color(215, 215, 215));
        g.fillRect(padding + hozoffset, height - 1, 1, 1);
        g.setColor(new Color(100, 100, 100));
        g.fillRect(padding + hozoffset + width - 1, height - 1, 1, 1);
        
        if (isSelected) {
            g.setColor(new Color(50,50,255,45));
            g.fillRect(padding + hozoffset, padding, width, 2 * internalPadding + HEIGHT);
        }

    }

    public void setNumberOfColumns(int numberOfColumns) {
        this.numberOfColumns = numberOfColumns;
        columnarray = new String[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++)
            columnarray[i] = "unnamed";
        initColumnWidthArray();
        invalidate();
        repaint();
    }

    public int getNumberOfColumns() {
        return numberOfColumns;
    }

    public void setColumnWidth(int index, int size) {
        if (index > -1 && index < getNumberOfColumns() && size > -1) {
            if (size < MIN_COLUMN_WIDTH)
                size = MIN_COLUMN_WIDTH;
            columnWidthArray[index] = size;
            repaint();
        }
    }

    public String[] getColumnArray() {
        String[] temp = new String[columnarray.length];
        System.arraycopy(columnarray, 0, temp, 0, columnarray.length);
        return temp;
    }

    public RowStats getRowStats() {
        return new RowStats(getColumnWidthArray(), MCList.PADDING, MCList.INTERNAL_PADDING);
    }

    public Dimension getMinimumSize() {
        return new Dimension(15, 5);
    }

    public Dimension getPreferredSize() {
        int plength = MCList.PADDING;
        for (int i = 0; i < numberOfColumns; i++) {
            plength += Math.min(getPreferredColumnWidth(i), getColumnWidth(i)) + MCList.PADDING + 2
                    * MCList.INTERNAL_PADDING;
        }

        return new Dimension(plength, getHeight());
    }

    public Dimension calculateSize() {
        int plength = getRowStats().getTotalLength();
        int olength = callback.getSize().width + 250; //not, this is done to
        // avoid a flickering
        // effect that occures
        // when
        // a panel is resized (and repainted);
        //The idea is to avoid resizing by making the panel JUST big enough
        //to not flicker most of the time.
        //The flickering stll occures, however if a list is grown more than 250
        //pixels over it's current size. oh well..
        //This effect does not happen in MacOS X, because all windows are
        // double buffered.

        return new Dimension((plength > olength ? plength : olength), getHeight());
    }

    public void setColumnName(int columnnumber, String name) {
        if (columnnumber >= numberOfColumns)
            return;
        columnarray[columnnumber] = name;
        repaint();
    }

    /**
     * If there is only one column then fill all the space available else if the column width is
     * unspecified use the header title sting width else use the value form the column width array
     */
    public int getColumnWidth(int i) {
        return (numberOfColumns == 1 ? callback.getPane().getViewportSize().width
                - (2 * MCList.PADDING) : getPreferredColumnWidth(i));
    }

    private int getPreferredColumnWidth(int i) {
        return (columnWidthArray[i] == -1 ? getFontMetrics(getFont()).stringWidth(
                columnarray[i] + 1)
                + MCList.PADDING : columnWidthArray[i]);
    }

    public int[] getColumnWidthArray() {
        int[] columnWidthArray = new int[numberOfColumns];

        for (int i = 0; i < numberOfColumns; i++) {
            columnWidthArray[i] = getColumnWidth(i);
        }
        return columnWidthArray;
    }

    public int[] getPreferredColumnWidthArray() {
        int[] columnWidthArray = new int[numberOfColumns];

        for (int i = 0; i < numberOfColumns; i++) {
            columnWidthArray[i] = getPreferredColumnWidth(i);
        }

        return columnWidthArray;
    }

    public int getHeight() {
        return HEIGHT + getTotalPadding();
    }

    private int getTotalPadding() {
        return 2 * MCList.PADDING + 2 * MCList.INTERNAL_PADDING;
    }

    public String makeFit(String s, int size) {

        FontMetrics tempfont = callback.getFontMetrics(callback.getFont()); //uses
        // default
        // font

        int i = 0;
        if (tempfont.stringWidth(s) > size - 8) {
            for (i = s.length(); ((tempfont.stringWidth(s.substring(0, i) + "...") > size - 8) && i > 0); i--)
                ;
            return s.substring(0, i) + "...";
        }
        return s;
    }

    //Returns the last column if column of click is not valid.
    public int getColumnOfClick(int x, int y) {
        //Y is useless here BTW.
        RowStats rowstats = getRowStats();

        int xcounter = 0;
        for (int i = 0; i < numberOfColumns; i++) {
            if (x >= xcounter && x < (rowstats.getTotalWidthOfColunm(i) + xcounter))
                return i;
            xcounter += rowstats.getTotalWidthOfColunm(i);
        }
        return -1;
    }

    public void sortBy(int x) {
        sortby = x;
        repaint();
    }

    //returns -1 if there's currently no column to sort by.
    public int getSortBy() {
        return sortby;
    }

    public void setMouseOver(int x) {
        if (!USE_ROLL_OVER)
            return;

        boolean repaintNow = (mouseOverColumn != x);

        mouseOverColumn = x;

        if (repaintNow)
            repaint(); // to stop millions of repaints.
    }

    public MCList getMCLParent() {
        return callback;
    }

    public boolean isOnBorder(int x) {
        return (getResizeColumn(x) != -1);
    }

    public int getResizeColumn(int x) {
        int counter = 0;

        RowStats rowstats = getRowStats();
        for (int i = 0; i < numberOfColumns; i++) {
            counter += rowstats.getTotalWidthOfColunm(i);
            if (x < counter + CLICK_LATITUDE && x > counter - CLICK_LATITUDE) {
                return i;
            }
        }
        return -1;
    }
}