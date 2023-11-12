package com.general.util;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JPanel;

public class ProgressBar extends JPanel {
    public final static int DEFAULT_Y_SIZE = 10;

    public final static int DEFAULT_X_SIZE = 440;

    private volatile long min; //volatile for threading

    private volatile long max;

    private volatile long value;

    private volatile boolean hasBorder = true;

    private Dimension doubleBufferSize;

    private Image im;

    private volatile Timer updaterTimer;

    /**
     * Mac OS X 1.4.2 VM blinks when setting the foreground color.. so
     * re-implement it here.. Stupid JVM
     */
    private Color hackForMacOSX = Color.blue;

    private final Runnable timerCode = new Runnable() {
        public void run() {
            timerCode();
        }
    };

    public ProgressBar() {
        this(0, 100);

        init();
    }

    public ProgressBar(long min, long max) {
        setMin(min);
        setMax(max);

        init();
    }

    private void init() {
        setBackground(Color.white);
        doubleBufferSize = getSize(); //! important

        addComponentListener(new ComponentAdapter() {

            public void componentResized(ComponentEvent e) {
                resetDoubleBuffer();
            }
        });
    }

    private synchronized void resetDoubleBuffer() {
        Dimension currentSize = getSize();
        doubleBufferSize = currentSize;

        im = createImage(currentSize.width, currentSize.height);
    }

    public void update(Graphics g) {

        if (im == null) {
            resetDoubleBuffer();
        }

        paint(im.getGraphics());

        g.drawImage(im, 0, 0, this);
    }

    public final synchronized boolean isValueOutOfBounds() { //inline
        //return true;
        return (value < min || value > max);
    }

    private synchronized void timerCode() {
        updaterTimer = null;
        repaint();
        runTimerIfAppropriate();
    }

    private synchronized void stopTimer() {
        updaterTimer = null;
    }

    private synchronized void assertTimer() {
        if (updaterTimer == null)
            updaterTimer = new Timer(timerCode, 75);
    }

    private synchronized void runTimerIfAppropriate() {
        if (isShowing()) {//&& isValueOutOfBounds()) {
            assertTimer();
        } else {
            stopTimer();
        }
    }

    public void paint(Graphics g) {
        runTimerIfAppropriate();

        Dimension size = getSize();
        if (isValueOutOfBounds()) {
            double percent = Math.sin((((double) System.currentTimeMillis() / (double) 70))
                    / (2 * Math.PI));
            percent = (percent + 1) / 2;
            int gray = (int) ((255) * percent);
            g.setColor(new Color(gray, gray, gray));
            g.fillRect(0, 0, size.width, size.height);
        } else {
            g.setColor(getBackground());
            g.fillRect(0, 0, size.width, size.height);

            g.setColor(getForeground());
            g.fillRect(0, 0, getXSize(size.width), size.height);
        }

        if (hasBorder) {
            g.setColor(Color.black);
            g.drawRect(0, 0, size.width - 1, size.height - 1);
        }
    }

    public void setForeground(Color color) {
        hackForMacOSX = color;
    }

    public Color getForeground() {
        return hackForMacOSX;
    }

    private int getXSize(int maxWidth) {
        double percent = (double) (value - min) / (double) (max - min);

        return (int) (percent * maxWidth);
    }

    public synchronized Dimension getPreferredSize() {
        return new Dimension(DEFAULT_X_SIZE, DEFAULT_Y_SIZE);
    }

    public synchronized Dimension getMinimumSize() {
        return new Dimension(100, DEFAULT_Y_SIZE);
    }

    public synchronized void setBorderVisible(boolean hasBorder) {
        this.hasBorder = hasBorder;
        repaint();
    }

    public synchronized void setMin(long min) {
        this.min = min;
    }

    public synchronized void setMax(long max) {
        this.max = max;
    }

    public long getMax() {
        return max;
    }

    public long getMin() {
        return min;
    }

    public long getValue() {
        return value;
    }

    int lastValue = 0; //To make sure not repaint is done if it's not needed.

    public synchronized void setValue(long value) {
        if (this.value == value)
            return;
        this.value = value;

        if (isValueOutOfBounds()) {
            int temp_xsize = getXSize(doubleBufferSize.width);
            if (lastValue == temp_xsize)
                return;

            lastValue = temp_xsize;
        }

        runTimerIfAppropriate();
        //repaint();
    }
}