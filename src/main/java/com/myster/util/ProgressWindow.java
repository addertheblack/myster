package com.myster.util;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Taskbar;
import java.awt.Taskbar.Feature;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import com.general.util.ProgressBar;
import com.general.util.Util;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;

public class ProgressWindow extends MysterFrame {
    public static final int X_SIZE = 468;
    public static final int Y_SIZE = 50;
    public static final int AD_HEIGHT = 60;
    public static final int X_TEXT_OFFSET = 5; // x offset of text
    public static final int Y_TEXT_OFFSET = 5; // y offset of text

    private final List<ProgressPanel> progressPanels = new ArrayList<>();

    protected final AdPanel adPanel = new AdPanel();

    public ProgressWindow(MysterFrameContext c) {
        super(c);
        commonInit();
    }

    public ProgressWindow(MysterFrameContext c, String title) {
        super(c, title);

        commonInit();
    }

    private void commonInit() {
        Container c = getContentPane();
        c.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));

        c.add(adPanel);
        addProgressPanel();

        setResizable(false);

        Image adImage = com.general.util.Util.loadImage("defaultProgressImage.gif", adPanel);

        if (adImage != null)
            adPanel.addImage(adImage);
        
        
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    public synchronized void show() {
        super.show();
        resize();
        doLayout();
    }

    public void pack() {
        super.pack();
    }

    protected synchronized void resize() {
        Container c = getContentPane();
        
        //Usually I would do a setSize() and leave it at that but
        //MacOS X 1.3.1 tends to ignore my setSize command if
        //the user is dragging the window and/or crash if
        //I continue too soon after sending it. *sigh*
        int xSize = X_SIZE;
        int ySize = AD_HEIGHT + (Y_SIZE * progressPanels.size()) ;
        if (Util.isEventDispatchThread()) {
            c.setPreferredSize(new Dimension(xSize, ySize));
        }
        pack();
//        while ((getSize().width != xSize) || (getSize().height != ySize)) {
//            setSize(xSize, ySize);
//            try {
//                Thread.sleep(500);
//            } catch (Exception ex) {
//                // nothing
//            } //an attempt to stop crashing on resizing...
//            counter++;
//
//            if (counter > 20) {
//                System.out
//                        .println("Fine, I won't resize the window, but it will be the wrong size!");
//                break;
//            }
//
//            if (counter > 1) {
//                System.out.println("I have tried " + counter
//                        + " times to change the size of the progress window!");
//            }
//        }
    }

    // methods to update progress window text
    public synchronized void setText(String s) {
        setText(s, 0);
    }

    public synchronized void setText(String s, int bar) {
        getProgressPanel(bar).setText(s);
    }

    public synchronized void setAdditionalText(String newText) {
        setAdditionalText(newText, 0);
    }

    public synchronized void setAdditionalText(String newText, int bar) {
        getProgressPanel(bar).setAdditionalText(newText);
    }

    public synchronized void setProgressBarNumber(int numberOfBars) {
        if (numberOfBars < 1)
            return; //yeah ha ha, less than 1, funny guy
        if (numberOfBars > 50)
            return; //more than 50 is rediculous for this implementation

        if (numberOfBars > progressPanels.size()) {
            for (int i = progressPanels.size(); i < numberOfBars; i++) {
                addProgressPanel();
            }
        } else if (numberOfBars < progressPanels.size()) {
            for (int i = progressPanels.size(); i > numberOfBars; i--) {
                removeProgressPanel(i - 1);
            }
        } else {
            return; //skip out the resize below
        }

        resize();
    }

    public synchronized int getProgressBarNumber() {
        return progressPanels.size();
    }

    private void removeProgressPanel(int index) {
        remove(progressPanels.get(index));

        progressPanels.remove(index);
    }

    private void addProgressPanel() {
        Container c = getContentPane();
        ProgressPanel panel = new ProgressPanel();

        c.add(panel);
        progressPanels.add(panel);
    }

    public synchronized void startBlock(int bar, long min, long max) {
        setMin(min, bar);
        setMax(max, bar);
        setValue(min, bar);
    }

    public synchronized void makeImage(byte[] b) {
        Image ad = getToolkit().createImage(b);

        MediaTracker tracker = new MediaTracker(adPanel);
        tracker.addImage(ad, 0);
        try {
            tracker.waitForID(0);
        } catch (Exception ex) {
            System.out.println("Crap");
        }

        adPanel.addImage(ad);
    }

    // Variation on standard suite
    public synchronized void setValue(long value, int bar) {
        getProgressPanel(bar).setValue(value);

        updateIcon();
    }

    public synchronized void setMax(long max, int bar) {
        getProgressPanel(bar).setMax(max);
    }

    public synchronized void setMin(long min, int bar) {
        getProgressPanel(bar).setMin(min);
    }

    public synchronized long getMax(int bar) {
        return getProgressPanel(bar).getMax();
    }

    public synchronized long getMin(int bar) {
        return getProgressPanel(bar).getMin();
    }

    public synchronized long getValue(int bar) {
        return getProgressPanel(bar).getValue();
    }

    //Ironically enough this check is done again in the progressPanels and
    // again for the array.
    //I guess you can never be too safe.
    private void checkBounds(int index) {
        if (index < 0 || index > progressPanels.size())
            throw new IndexOutOfBoundsException(index + " is not a valid progress bar");
    }

    private ProgressPanel getProgressPanel(int index) {
        checkBounds(index);

        return progressPanels.get(index);
    }

    //Standard progress suite
    public synchronized void setValue(long value) {
        setValue(value, 0);
    }

    public synchronized void setMax(long max) {
        setMax(max, 0);
    }

    public synchronized void setMin(long min) {
        setMin(min, 0);
    }

    public synchronized long getMax() {
        return getMax(0);
    }

    public synchronized long getMin() {
        return getMin(0);
    }

    public synchronized long getValue() {
        return getValue(0);
    }

    Image piChart;

    int lastPercent;

    private void updateIcon() {
        //if (true==true) return;
        if (piChart == null)
            piChart = createImage(32, 32);
        if (piChart == null)
            return; // How does this happen??
        double percent = 0;

        percent = (getValue() < getMin() || getValue() > getMax() ? 0
                : ((double) (getValue() - getMin())) / ((double) (getMax() - getMin())));

        int int_temp = (int) (percent * 100);

        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Feature.PROGRESS_VALUE_WINDOW)) {
        	Taskbar.getTaskbar().setWindowProgressValue(this, int_temp);
        }

        if (int_temp == lastPercent) {
            return;
        }

        lastPercent = int_temp;

        Graphics gp = piChart.getGraphics();
        gp.setColor(Color.white);
        gp.fillRect(0, 0, 32, 32);
        gp.setColor(new Color(240, 240, 240));
        gp.fillArc(1, 1, 30, 30, 90, 360);
        gp.setColor(getBarColor());
        gp.fillArc(1, 1, 30, 30, 90, -(int) (percent * 360));
        //gp.setColor(Color.black);
        //gp.drawString(""+((int)(percent/100-1))+"%", 1, 16);

        setIconImage(piChart);
    }

    public synchronized void setBarColor(Color color) {
        setBarColor(color, 0);
    }

    public synchronized void setBarColor(Color color, int bar) {
        getProgressPanel(bar).setBarColor(color);
    }

    public synchronized Color getBarColor() {
        return getBarColor(0);
    }

    public synchronized Color getBarColor(int bar) {
        return getProgressPanel(bar).getBarColor();
    }

    public synchronized void addAdClickListener(MouseListener l) {
        adPanel.addMouseListener(l);
    }

    private static class ProgressPanel extends JPanel {
        public static final int PROGRESS_X_OFFSET = 10;
        public static final int PROGRESS_Y_OFFSET = 25;
        public static final int ADDITIONAL_X_SIZE = 50;

        private final ProgressBar progressBar;
        private final JLabel textLabel;
        private final JLabel additionalLabel;

        public ProgressPanel() {
            setLayout(null);

            progressBar = new ProgressBar();
            progressBar.setLocation(PROGRESS_X_OFFSET, PROGRESS_Y_OFFSET);
            progressBar.setSize(440, 10);
            progressBar.setForeground(Color.blue);
            add(progressBar);

            textLabel = new JLabel();
            textLabel.setLocation(X_TEXT_OFFSET, Y_TEXT_OFFSET);
            textLabel.setSize(X_SIZE - X_TEXT_OFFSET - ADDITIONAL_X_SIZE, 20);
            add(textLabel);

            additionalLabel = new JLabel();
            additionalLabel.setLocation(X_SIZE - ADDITIONAL_X_SIZE, Y_TEXT_OFFSET);
            additionalLabel.setSize(ADDITIONAL_X_SIZE, 20);
            add(additionalLabel);
        }

        public synchronized Dimension getPreferredSize() {
            return new Dimension(X_SIZE, Y_SIZE);
        }

        //Standard progress suite, look ma, a forward... aka stupid OOP "is a"
        // overhead
        public synchronized void setValue(long value) {
            progressBar.setValue(value);
        }

        public synchronized void setMax(long max) {
            progressBar.setMax(max);
        }

        public synchronized void setMin(long min) {
            progressBar.setMin(min);
        }

        public synchronized long getMax() {
            return progressBar.getMax();
        }

        public synchronized long getMin() {
            return progressBar.getMin();
        }

        public synchronized long getValue() {
            return progressBar.getValue();
        }

        public synchronized void setText(String newText) {
            textLabel.setText(newText);
        }

        public synchronized void setAdditionalText(String newText) {
            additionalLabel.setText(newText);
        }

        public synchronized void setBarColor(Color color) {
            progressBar.setForeground(color);
//            progressBar.repaint();
        }

        public synchronized Color getBarColor() {
            return progressBar.getForeground();
        }
    }

    protected static class AdPanel extends JPanel {
        Image ad;

        String labelText = "";

        public synchronized void setAd(Image im) {
            ad = im;
        }

        public void paintComponent(Graphics g) {
            if (ad == null)
                return;

            g.drawImage(ad, 0, 0, X_SIZE, AD_HEIGHT, this);

            if (!labelText.equals("")) {
                final int xPadding = 3;

                FontMetrics metrics = getFontMetrics(getFont());

                int ascent = metrics.getAscent();
                int leading = metrics.getLeading();
                int height = metrics.getHeight();

                g.setColor(new Color(255, 255, 200));
                g.fillRect(0, 0, metrics.stringWidth(labelText) + xPadding + xPadding, height);

                g.setColor(Color.black);
                g.drawString(labelText, xPadding, ascent + leading / 2);
            }
        }

        public synchronized Dimension getPreferredSize() {
            return new Dimension(X_SIZE, AD_HEIGHT);
        }

        public synchronized void addImage(Image newAd) {
            ad = newAd;
            labelText = "";
            repaint();
        }

        public synchronized void setLabelText(String someText) {
            labelText = someText;
            repaint();
        }
    }

}