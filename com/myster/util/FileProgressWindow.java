package com.myster.util;

import java.awt.Cursor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Vector;

import com.general.util.Timer;
import com.general.util.Util;
import com.myster.net.web.WebLinkManager;

public class FileProgressWindow extends ProgressWindow {
    public static final int BAR_1 = 0;

    public static final int BAR_2 = 1;

    RateTimer rateTimer;

    Vector barStartTime = new Vector(10, 10);

    Vector previouslyDownloaded = new Vector(10, 10);

    boolean overFlag = false;

    String url;

    public FileProgressWindow() {
        this("");
    }

    public FileProgressWindow(String title) {
        super(title);

        resizeVectorWithLongs(barStartTime, getProgressBarNumber());
        resizeVectorWithLongs(previouslyDownloaded, getProgressBarNumber());

        addComponentListener(new ComponentAdapter() {
            public synchronized void componentShown(ComponentEvent e) {
                rateTimer = new RateTimer();
            }

            public synchronized void componentHidden(ComponentEvent e) {
                if (rateTimer != null)
                    rateTimer.end();
            }
        });

        addAdClickListener(new AdClickHandler());

        resize();
    }

    public synchronized void setProgressBarNumber(int numberOfBars) {
        resizeVectorWithLongs(barStartTime, numberOfBars);
        resizeVectorWithLongs(previouslyDownloaded, numberOfBars);

        super.setProgressBarNumber(numberOfBars);
    }

    private static void resizeVectorWithLongs(Vector vector, int newSize) {
        int oldSize = vector.size();

        vector.setSize(newSize);

        if (newSize > oldSize) {
            for (int i = oldSize; i < newSize; i++) {
                vector.setElementAt(new Long(0), i);
            }
        }
    }

    public synchronized void setPreviouslyDownloaded(long someValue, int bar) {
        previouslyDownloaded.setElementAt(new Long(someValue), bar);
    }

    public synchronized void startBlock(int bar, long min, long max) {
        barStartTime.setElementAt(new Long(System.currentTimeMillis()), bar);

        super.startBlock(bar, min, max);
    }

    public synchronized void done() {
        overFlag = true;
    }

    private String calculateRate(int bar) {
        if (getValue(bar) < getMin(bar) || getValue(bar) > getMax(bar))
            return "";

        return formatRate(((Long) barStartTime.elementAt(bar)).longValue(),
                getValue(bar)
                        - ((Long) previouslyDownloaded.elementAt(bar))
                                .longValue());
    }

    private long rateCalc(long startTime, long value) {
        long int_temp = ((System.currentTimeMillis() - startTime) / 1000);

        return (int_temp <= 0 ? 0 : value / int_temp);
    }

    public synchronized void setURL(String urlString) {
        url = (urlString.equals("") ? null : urlString);

        if (url != null) {
            adPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        } else {
            adPanel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }

    private String formatRate(long startTime, long value) {
        long temp = rateCalc(startTime, value);
        if (temp == 0) {
            return "";
        }

        return Util.getStringFromBytes(temp) + "/s";
    }

    private class RateTimer implements Runnable {
        public static final int UPDATE_TIME = 100;

        Timer timer;

        private boolean endFlag = false;

        public RateTimer() {
            newTimer();
        }

        public void run() {
            if (endFlag)
                return;
            if (overFlag)
                return;
            //if (getValue() == getMax()) return;

            synchronized (FileProgressWindow.this) {
                for (int i = 0; i < getProgressBarNumber(); i++) {
                    setAdditionalText(calculateRate(i), i);
                }
            }

            newTimer();
        }

        private void newTimer() {
            timer = new Timer(this, UPDATE_TIME);
        }

        public synchronized void end() {
            if (timer != null)
                timer.cancelTimer();
            endFlag = true;
        }
    }

    private class AdClickHandler extends MouseAdapter {
        public long lastMouseReleaseTime = 0;

        public synchronized void mouseEntered(MouseEvent e) {
            if (url != null)
                adPanel.setLabelText(url);
        }

        public synchronized void mouseExited(MouseEvent e) {
            adPanel.setLabelText("");
        }

        public synchronized void mouseReleased(MouseEvent e) {
            if ((e.getX() > 0 && e.getX() < X_SIZE)
                    && (e.getY() > 0 && e.getY() < AD_HEIGHT)) {
                if ((System.currentTimeMillis() - lastMouseReleaseTime > 500)
                        && url != null && (!url.equals(""))) {
                    try {
                        WebLinkManager.openURL(new URL(url));
                    } catch (MalformedURLException ex) {
                        ex.printStackTrace();
                    }
                }

                lastMouseReleaseTime = System.currentTimeMillis(); //so that
                                                                   // double -
                                                                   // triple
                                                                   // clicks by
                                                                   // spastic
                                                                   // people
                                                                   // don't
                                                                   // generate
                                                                   // multipe
                                                                   // browsers
            }
        }
    }
}