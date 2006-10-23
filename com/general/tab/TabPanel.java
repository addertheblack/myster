/*
 * Main.java
 * 
 * Title: Server Stats Window Test App Author: Andrew Trumper Description: An
 * app to test the server stats window
 */

package com.general.tab;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Vector;

import javax.swing.JPanel;

public class TabPanel extends JPanel {
    TabVector tabs;

    ListenerVector tabListeners;

    private Image backgroundImage;

    public static final int XSIZE = 600;

    public static final int YSIZE = 50;

    public TabPanel() {
        //setBackground(Color.red);
        tabs = new TabVector(this);
        tabListeners = new ListenerVector();
        addMouseListener(new MyHandler());
        backgroundImage = (TabUtilities.loadBackground(this));
    }

    ////////Tab creation routines:

    //This orutine creates a tab with the generic middle graphic and uses the
    // Stirng sent to it as a label.
    public void addTab(String s) {
        addTab(new Tab(s, this));
    }

    //THis routine creates a tab with a special graphic (the String) and no
    // label.
    public void addCustomTab(String s) {
        addTab(new Tab(this, s));
    }

    //Same thing but acts as a keeper for object.
    public void addTab(String s, Object o) {
        addTab(new Tab(s, this));
    }

    //See above
    public void addCustomTab(String s, Object o) {
        addTab(new Tab(this, s));
    }

    /////// End tab creation routines.

    //public void removeTab(Tab t) {
    //	tabs.removeTab(t);
    //}

    public void addTabListener(TabListener l) {
        tabListeners.addElement(l);
    }

    public void removeTabListener(TabListener l) {
        tabListeners.removeElement(l);
    }

    public int getMaxLength() {
        return tabs.getMaxLength();
    }

    public int getTabClicked(int x) {
        return tabs.getTabClicked(x);
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        //Display background

        if (backgroundImage == null) {
            backgroundImage = createImage(XSIZE, YSIZE);
            Graphics gr = backgroundImage.getGraphics();
            gr.setColor(Color.red);
            gr.fillRect(0, 0, XSIZE, YSIZE);
            gr.setColor(Color.black);
            gr.drawString("<Missing Image>", 5, 25);
        }
        g.drawImage(backgroundImage, 0, 0, this);
        g.setColor(Color.black);
        g.drawLine(getMaxLength(), getSize().height - 1, getSize().width - 1,
                getSize().height - 1);

        tabs.paint(g);
    }

    public void setOverlap(int i) {
        tabs.setOverlap(i);
    }

    public void addTab(Tab t) {
        tabs.addTab(t);
        if (tabs.size() == 1)
            tabs.setSelect(0);
        repaint();
    }

    private Image doubleBuffer; //adds double buffering

    public void update(Graphics g) {
        if (doubleBuffer == null) {
            doubleBuffer = createImage(600, 50);
        }
        Graphics graphics = doubleBuffer.getGraphics();
        //clear "canvas"
        paint(graphics);
        g.drawImage(doubleBuffer, 0, 0, this);
    }

    private class TabVector extends Vector {
        public int overlap = 0;

        int lastselected = 0; //used by set selected.

        TabPanel parent;

        public TabVector(TabPanel parent) {
            this.parent = parent;
        }

        public synchronized void addTab(Tab b) {
            if (indexOf(b) == -1)
                addElement(b);
        }

        public synchronized void setSelect(int tabnum) {
            if (tabnum >= size())
                return;
            for (int i = 0; i < tabs.size(); i++) {
                tabs.getTab(i).setSelect(false);
            }
            if (tabnum != -1) {
                tabs.getTab(tabnum).setSelect(true);
                lastselected = tabnum;
            } else {
                tabs.getTab(lastselected).setSelect(true);//this is dependent
                                                          // on there being no
                                                          // remove tab routine.
                getToolkit().beep();
            }
            parent.tabListeners.fireEvents(parent, lastselected);
        }

        //public synchronized void removeTab(Tab b) { //Don't use this routine.
        //	removeElement(b); //yay wrapper fun.
        //}

        public synchronized Tab getTab(int index) {
            return (Tab) (elementAt(index));
        }

        public synchronized void paint(Graphics g) {
            Tab selectedtab = null;
            Tab workingtab;

            int cwxloc = getMaxLength();//current working x location.. for
                                        // graphics context transpose.
            int selectedcwxloc = 0;//yippy.

            for (int i = tabs.size() - 1; i >= 0; i--) {
                workingtab = getTab(i);
                cwxloc -= workingtab.getSize().width;
                g.translate(cwxloc, 0);
                workingtab.paint(g);
                g.translate(-cwxloc, 0);
                if (workingtab.isSelected()) {
                    selectedtab = workingtab;
                    selectedcwxloc = cwxloc;
                }
                cwxloc += overlap;
            }

            g.translate(selectedcwxloc, 0);
            if (selectedtab != null)
                selectedtab.paint(g);
            g.translate(-selectedcwxloc, 0);
        }

        public synchronized int getMaxLength() {
            int counter = 0;

            for (int i = 0; i < tabs.size(); i++) {
                counter += getTab(i).getSize().width - overlap; //another good
                                                                // line of code.
            }

            counter += overlap; //The last one doesn't count;
            return counter;
        }

        public synchronized int getTabClicked(int x) {
            int counter = 0;

            for (int i = 0; i < tabs.size(); i++) {
                counter += getTab(i).getSize().width - overlap; //another good
                                                                // line of code.
                if (x < counter)
                    return i;
            }

            counter += overlap; //The last one doesn't count;
            if (x < counter)
                return tabs.size() - 1;
            return -1;
        }

        public void setOverlap(int i) {
            overlap = i;
        }

    }

    private class MyHandler extends MouseAdapter {
        public void mousePressed(MouseEvent e) {
            tabs.setSelect(getTabClicked(e.getX()));
        }
    }

    private static class ListenerVector extends Vector {
        public ListenerVector() {
            super(10, 10);
        }

        public synchronized void fireEvents(TabPanel p, int tabid) {
            for (int i = 0; i < size(); i++) {
                ((TabListener) (elementAt(i)))
                        .tabAction(new TabEvent(p, tabid));
            }
        }
    }
}