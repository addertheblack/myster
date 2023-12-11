/*
 * MCList.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

import java.awt.Adjustable;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

import com.general.util.Util;

//import java.awt.image.BufferedImage;//testing

public class AWTMCList extends Panel implements MCList {
    private Image im;

    //For image double buffer:
    private int previousimagex = -1;

    private int previousimagey = -1;

    //Themes support of sorts
    private MCRowThemeInterface rowTheme;

    //List Itself:
    MCListList list;

    //I add Myself to the scroll pane so it can scroll me around! (yeah, I
    // know)
    private ScrollPane pane;

    //Header
    private MCListHeader header;

    //Event Handler
    private MCListEventHandler eventhandler;

    //Single select flag:
    private boolean singleselectboolean = false;

    public static final int PADDING = 1;

    public static final int INTERNAL_PADDING = 1;

    public AWTMCList(int numberofcolumns, boolean singleselect, Component c) {
        this.rowTheme = new DefaultMCRowTheme(c);

        header = new MCListHeader(this, numberofcolumns);

        setBackground(rowTheme.getBackground());

        eventhandler = new MCListEventHandler(this);

        singleselectboolean = singleselect;
        list = new MCListList();

        pane = new MCListScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        setSize(1024, 1024);
        setLayout(null);
        pane.add(this);

        add(header);
        addMouseListener(eventhandler);
        addMouseMotionListener(eventhandler);

        Adjustable horizontal = pane.getHAdjustable();
        horizontal.setUnitIncrement(10);
        Adjustable vertical = pane.getVAdjustable();
        vertical.setUnitIncrement(10);
        vertical.addAdjustmentListener(new AdjustmentListener() {
            public void adjustmentValueChanged(AdjustmentEvent e) {
                header.setLocation(0, e.getValue());
            }
        });

        addKeyListener(eventhandler);
    }

    public void setNumberOfColumns(int c) {
        header.setNumberOfColumns(c);
        listChangedSize(); //blah.

    }

    public void setColumnName(int columnnumber, String name) {
        header.setColumnName(columnnumber, name);
        listChangedSize();
    }

    public void setColumnWidth(int index, int size) {
        header.setColumnWidth(index, size);
        listChangedSize();
    }

    public synchronized void sortBy(int column) {
        list.setSortBy(column);
        header.sortBy(list.isSorted() ? column : -1);
        repaint();
    }

    public Container getPane() {
        return pane;
    }

    public void addItem(MCListItemInterface m) {
        addItem(new MCListItemInterface[] { m });
    }

    public void addItem(MCListItemInterface[] m) { //oh for templates...
        list.addElement(m);
        listChangedSize();
    }

    public void setSorted(boolean isSorted) {
        list.setSorted(isSorted);
        if (!isSorted)
            header.sortBy(-1);
    }

    public boolean isSorted() {
        return list.isSorted();
    }

    //Important for canvas
    public synchronized Dimension getPreferredSize() {
        int ysize = list.size() * rowTheme.getHeight(PADDING, INTERNAL_PADDING) - 1 + header.getHeight();
        int xsize = header.getPreferredSize().width;
        return new Dimension(xsize, ysize);
    }

    public void update(Graphics g) { //done.
        paint(g);
    }

    private void updatey(Graphics g) { //done.
        if (pane.getViewportSize().height - header.getHeight() < 0)
            return;
        if (previousimagex != pane.getViewportSize().width || //makes sure the
                // image buffer is
                // up to date!
                previousimagey != pane.getViewportSize().height
                || false) {
            im = createImage(pane.getViewportSize().width, pane.getViewportSize().height
                    - header.getHeight());//new
            // BufferedImage(pane.getViewportSize().width,pane.getViewportSize().height-header.getHeight(),BufferedImage.TYPE_INT_BGR);//
            previousimagex = pane.getViewportSize().width;
            previousimagey = pane.getViewportSize().height;
        }
        paint(im.getGraphics(), pane.getScrollPosition().x, pane.getScrollPosition().x
                + pane.getViewportSize().width, pane.getScrollPosition().y, pane
                .getScrollPosition().y
                + pane.getViewportSize().height);
        g.setClip(pane.getScrollPosition().x, pane.getScrollPosition().y,
                pane.getViewportSize().width, pane.getViewportSize().height);
        g.drawImage(im, pane.getScrollPosition().x,
                pane.getScrollPosition().y + header.getHeight(), this);
    }

    public void paint(Graphics g) { //done.
        header.setLocation(0, pane.getScrollPosition().y);
        header.setSize(header.calculateSize());
        updatey(g);
        //paint(g, pane.getScrollPosition().y,
        // pane.getViewportSize().height+pane.getScrollPosition().y);
        // //imporant.
    }

    public void paint(Graphics g, int x1, int x2, int y1, int y2) { //uppper
        // and lower
        // bounds to
        // draw
        //if (true==true) return ;
        g.setColor(getBackground());
        g.fillRect(0, 0, im.getWidth(this), im.getHeight(this));
        //g.fillRect(0,0,y2-y1,x2-x1);
        if (list.size() == 0)
            return;
        int correctedy1 = y1 + header.getHeight(); //why bother over drawing like this?
        int c1 = getClicked(1, correctedy1);
        int c2 = getClicked(1, y2);
        if (c2 == -1)
            c2 = list.size() - 1;
        if (c1 == -1)
            c2 = -1; //If c1=-1 the means the scroll pane is outside any
        // visible area so draw nothing.

        int offsetcounter = getYFromClicked(c1) - correctedy1; //rounding routine (get
        // the offset properly.
        // Gtes initial offset.

        RowStats rowstats = header.getRowStats();

        Dimension dimension = ((ScrollPane)getPane()).getViewportSize();

        for (int i = c1; i <= c2; i++) {
            rowTheme.paint(g, list.getElement(i), rowstats, offsetcounter, x1, i, dimension);
            offsetcounter += rowTheme.getHeight(PADDING, INTERNAL_PADDING);
        }
        g.dispose();
    }

    public boolean isSelected(int i) {
        if (!(i >= 0 && i < list.size()))
            return false;
        return list.getElement(i).isSelected();
    }

    public void select(int i) {
        if (!(i >= 0 && i < list.size()))
            return;
        list.getElement(i).setSelected(true);
    }

    public void unselect(int i) {
        if (!(i >= 0 && i < list.size()))
            return;
        list.getElement(i).setSelected(false);
    }

    public synchronized void clearAllSelected() {
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                list.getElement(i).setSelected(false);
            }
        }
    }

    public void toggle(int i) {
        synchronized (list) {
            if (!(i >= 0 && i < list.size()))
                return;
            if (list.getElement(i).isSelected())
                list.getElement(i).setSelected(false);
            else
                list.getElement(i).setSelected(true);
        }
    }

    synchronized int getClicked(int x, int y) {
        //No joking around
        int correctedY = y -header.getHeight();
        int temp = correctedY / rowTheme.getHeight(PADDING, INTERNAL_PADDING);
        if (temp < list.size())
            return temp;
        return -1;
    }

    public boolean isAnythingSelected() {
        return list.isAnythingSelected();
    }

    public int[] getSelectedIndexes() {
        return list.getSelectedIndexes();
    }

    /**
     * Returns the selected index Returns -1 is there is none selected or if more than one item is
     * selected.
     */

    public int getSelectedIndex() {
        return list.getSelectedIndex();
    }

    /**
     * If set the list will only allow one item o be selected at one time.
     */
    public void setSingleSelect(boolean b) {
        singleselectboolean = b;
    }

    public boolean isSingleSelect() {
        return singleselectboolean;
    }

    public synchronized void addMCListEventListener(MCListEventListener e) {
        eventhandler.addMCListEventListener(e);
    }

    public void clearAll() {
        list.removeAllElements();
        listChangedSize();
        pane.repaint();
    }

    public void removeItem(int i) {
        list.removeElement(i);
        listChangedSize();
    }

    public void removeItem(MCListItemInterface o) {
        list.removeElement(o);
        listChangedSize();
    }

    public void removeItem(int[] indexes) {
        list.removeIndexes(indexes);
        listChangedSize();
    }

    /**
     * Return that indexe's Item's Object
     */
    public Object getItem(int i) {
        return getMCListItem(i).getObject();
    }

    /**
     * Return the MCListItemInterface for this index... A bit confusing, yes...
     */
    public MCListItemInterface getMCListItem(int i) {
        return list.getElement(i);
    }

    public void reverseSortOrder() {
        list.reverseSortOrder();
        repaint();
    }

    private int getYFromClicked(int c) {
        int spacingindex = 0;
        spacingindex = c * rowTheme.getHeight(PADDING, INTERNAL_PADDING) + header.getHeight();
        return spacingindex;

    }

    private void listChangedSize() { //if it is synchronized, the it wil break
        // under MacOS X.
        try {
            pane.invalidate(); //invalidate current layout.
            Util.invokeLater(new Runnable() {
                public void run() {
                    pane.validate(); //update scroll pane to possible changes in size.
                    repaint();
                }
            });
        } catch (Exception ex) {
        }
    }

    public int length() {
        return list.size();
    }

    public Font getFont() {
        Font font = super.getFont();
        if (super.getFont() == null) {
            return new Font("Courier", 0, 12);
        }
        return font;
    }

    private class MCListScrollPane extends ScrollPane {
        public MCListScrollPane(int m) {
            super(m);
        }

        public Dimension getPreferredSize() {
            return super.getPreferredSize();
        }

        public Dimension getMinimumSize() {
            return new Dimension(20, 20);
        }

        public Dimension getMaximumSize() {
            return new Dimension(999999, 999999);
        }
    }
}