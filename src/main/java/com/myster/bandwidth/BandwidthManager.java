package com.myster.bandwidth;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.general.util.GridBagBuilder;
import com.general.util.MessagePanel;
import com.myster.mml.RobustMML;
import com.myster.pref.MysterPreferences;
import com.myster.pref.ui.PreferencesPanel;

/**
 * The bandwidth manager is a set of static funcitons that allow multiple
 * dowload/upload threads to make sure their total bandwidth utilisation doesn't
 * exeed a preset amount.
 * <p>
 * In general, implementations should feel free to use the ThrottledInputStream
 * and ThrottledOutputStream provided instead of re-inventing the wheel by using
 * this functions directly.
 */

public class BandwidthManager {
    private static SomeStruct data = new SomeStruct();

    /**
     * This function will pause your thread by the amount of time is would take
     * to send the specified number of bytes taking into account all known
     * variables at the time and during the time of its calling.
     */
    public static final int requestBytesIncoming(int maxBytes) {
        if (!data.incommingIsEnabled)
            return maxBytes;

        return data.incommingImpl.requestBytes(maxBytes);
    }

    /**
     * This function will pause your thread by the amount of time is would take
     * to send the specified number of bytes taking into account all known
     * variables at the time and during the time of its calling.
     */
    public static final int requestBytesOutgoing(int maxBytes) {
        if (!data.outgoingIsEnabled)
            return maxBytes;

        return data.outgoingImpl.requestBytes(maxBytes);
    }

    //////////GUI
    /**
     * Please don't use this function.
     */
    public static PreferencesPanel getPrefsPanel() {
        return new BandwithPrefsPanel();
    }

    //////////PREFS
    private static final String OUTGOING_ENABLED = "/Outgoing Enabled";

    private static final String INCOMMING_ENABLED = "/Incomming Enabled";

    private static final String KEY_IN_PREFS = "BANDWIDTH PREFS";

    private static final String OUTGOING_MAX = "/Outgoing Max";

    private static final String INCOMMING_MAX = "/Incomming Max";

    private static final String TRUE_S = "TRUE";

    private static final String FALSE_S = "FALSE";

    public static synchronized boolean isOutgoingEnabled() {
        return data.prefMML.query(OUTGOING_ENABLED).equals(TRUE_S);
    }

    public static synchronized boolean isIncommingEnabled() {
        return data.prefMML.query(INCOMMING_ENABLED).equals(TRUE_S);
    }

    public static synchronized boolean setOutgoingEnabled(boolean enabled) {
        return data.setOutgoingEnabled(enabled);
    }

    public static synchronized boolean setIncommingEnabled(boolean enabled) {
        return data.setIncommingEnabled(enabled);
    }

    public static synchronized int getOutgoingMax() {
        return data.getOutgoingMax();
    }

    public static synchronized int getIncommingMax() {
        return data.getIncommingMax();
    }

    public static synchronized int setOutgoingMax(int max) {
        return data.setOutgoingMax(max);
    }

    public static synchronized int setIncommingMax(int max) {
        return data.setIncommingMax(max);
    }

    private static class SomeStruct { //hack..
        public boolean outgoingIsEnabled = false;

        public Bandwidth outgoingImpl = new BandwidthImpl();

        public boolean incommingIsEnabled = false;

        public Bandwidth incommingImpl = new BandwidthImpl();

        public RobustMML prefMML;

        public SomeStruct() {
            prefMML = (RobustMML) (MysterPreferences.getInstance()
                    .getAsMML(BandwidthManager.KEY_IN_PREFS));

            if (prefMML == null)
                prefMML = new RobustMML();

            outgoingImpl.setRate(getOutgoingMax());
            outgoingIsEnabled = isOutgoingEnabled();

            incommingImpl.setRate(getIncommingMax());
            incommingIsEnabled = isIncommingEnabled();
        }

        public synchronized boolean isOutgoingEnabled() {
            return prefMML.query(OUTGOING_ENABLED).equals(TRUE_S);
        }

        public synchronized boolean isIncommingEnabled() {
            return prefMML.query(INCOMMING_ENABLED).equals(TRUE_S);
        }

        public synchronized boolean setOutgoingEnabled(boolean enabled) {
            outgoingIsEnabled = enabled;
            return setBoolInPrefs(OUTGOING_ENABLED, enabled);
        }

        public synchronized boolean setIncommingEnabled(boolean enabled) {
            incommingIsEnabled = enabled;
            return setBoolInPrefs(INCOMMING_ENABLED, enabled);
        }

        private synchronized boolean setBoolInPrefs(String path, boolean bool) {
            prefMML.put(path, (bool ? TRUE_S : FALSE_S));
            return bool;
        }

        public synchronized int getOutgoingMax() {
            return getIntFromPrefs(OUTGOING_MAX, 10);
        }

        public synchronized int getIncommingMax() {
            return getIntFromPrefs(INCOMMING_MAX, 10);
        }

        private synchronized int getIntFromPrefs(String path, int defaultNum) {
            try {
                return Integer.parseInt(prefMML.query(path));
            } catch (NumberFormatException ex) {
                return defaultNum;
            }
        }

        public synchronized int setOutgoingMax(int p_max) {
            int max = Math.max(2, p_max);

            prefMML.put(OUTGOING_MAX, "" + max);
            MysterPreferences.getInstance().put(KEY_IN_PREFS, prefMML);
            outgoingImpl.setRate(max);

            return max;
        }

        public synchronized int setIncommingMax(int p_max) {
            int max = Math.max(2, p_max);

            prefMML.put(INCOMMING_MAX, "" + max);
            MysterPreferences.getInstance().put(KEY_IN_PREFS, prefMML);
            incommingImpl.setRate(max);

            return max;
        }
    }
}

/**
 * Represents a thread being blocked.
 */

class BlockedThread {
    private final double rate;

    private final List<BlockedThread> threads;

    private double bytesLeft;

    private Thread thread;

    private static volatile double WAIT_LATENCY = 100;

    BlockedThread(int bytesLeft, List<BlockedThread> threads, double rate) {
        this.bytesLeft = bytesLeft;
        thread = Thread.currentThread();
        this.threads = threads;
        this.rate = rate;
    }

    public synchronized void reSleep() {
        if (thread != null) {
            notifyAll();
        }
    }

    public synchronized void sleepNow() {
        for (;;) {
            double thisRate;
            int sleepAmount;
            long startTime;
            thisRate = (threads.size()) / rate;
            sleepAmount = (int) (bytesLeft * thisRate);
            startTime = System.currentTimeMillis();

            if (sleepAmount <= 0) {
                thread = null;
                return;
            }

            //sleepAmount=1;

            //below is voodoo.
            double waitLatency = WAIT_LATENCY;

            if (sleepAmount > waitLatency) {
                sleepAmount -= waitLatency;
            } else {
                int randomNumber = (int) (Math.random() * waitLatency);
                if (randomNumber < (waitLatency - sleepAmount)) {
                    thread = null;
                    return;
                } else {
                    sleepAmount = 1;
                }
            }

            try {
                if (sleepAmount == 1) {
                    long sstartTime = System.currentTimeMillis();
                    wait(sleepAmount);
                    WAIT_LATENCY = System.currentTimeMillis() - sstartTime + 1
                            - sleepAmount; //the +1 is important
                } else {
                    wait(sleepAmount);
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            } //unexpected error!

            double timeSlept = (System.currentTimeMillis() - startTime);
            bytesLeft -= (timeSlept / thisRate); //avoid divide by 0
        }
    }
}

/**
 * Prefs stuff. It's GUI so blah.
 */
class BandwithPrefsPanel extends PreferencesPanel {
    private final Component explanationPanel = MessagePanel.createNew(
            "Using the bandwidth preference pannel you can set the maximum rate "
                    + "at which Myster will send and recieve data. This setting is useful "
                    + "if you want to run a Myster server while using the internet, but find "
                    + "that it slows down your internet connection.");

    private final JCheckBox enableOutgoing;
    private final JCheckBox enableIncomming;

    private final JLabel outgoingSpeedLabel;
    private final JLabel incommingSpeedLabel;

    private final JTextField incommingBytesField;
    private final JTextField outgoingBytesField;

    private final JLabel outgoingUnitsLabel;
    private final JLabel incommingUnitsLabel;

    public BandwithPrefsPanel() {
        setLayout(new GridBagLayout());
        var params = new GridBagBuilder().withInsets(new Insets(0, 0, 5, 5));
        
        add(explanationPanel, params.withInsets(new Insets(0, 0, 20, 5)).withSize(3, 1).withFill(GridBagConstraints.HORIZONTAL));

        int nextOff = STD_YSIZE / 3;
        nextOff += 10;

        enableOutgoing = new JCheckBox("Enable Outgoing Throttling");
        enableOutgoing.setLocation(10, nextOff);
        enableOutgoing.setSize(200, 20);
        enableOutgoing.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                boolean state = false;
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    state = true;
                }

                setOutgoingEnable(state);
            }
        });
        add(enableOutgoing, params.withGridLoc(0, 1).withSize(3, 1).withAnchor(GridBagConstraints.WEST));

        nextOff += 25;

        outgoingSpeedLabel = new JLabel("Limit speed to: ");
        outgoingSpeedLabel.setLocation(15, nextOff);
        outgoingSpeedLabel.setSize(150, 20);
        add(outgoingSpeedLabel, params.withGridLoc(0, 2).withInsets(new Insets(0, 15, 5, 5)));

        outgoingBytesField = new JTextField("10", 7);
        outgoingBytesField.setLocation(15 + 150, nextOff);
        outgoingBytesField.setSize(50, 20);
        add(outgoingBytesField, params.withGridLoc(1, 2));

        outgoingUnitsLabel = new JLabel("Kilo BYTES / second");
        add(outgoingUnitsLabel, params.withGridLoc(2, 2).withAnchor(GridBagConstraints.WEST));


        enableIncomming = new JCheckBox("Enable Incomming Throttling");
        enableIncomming.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                boolean state = false;
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    state = true;
                }

                setIncommingEnable(state);
            }
        });
        //enableIncomming.setEnabled(false);
        add(enableIncomming, params.withGridLoc(0, 3).withSize(3, 1).withAnchor(GridBagConstraints.WEST));

        nextOff += 25;

        incommingSpeedLabel = new JLabel("Limit speed to: ");
        incommingSpeedLabel.setLocation(15, nextOff);
        incommingSpeedLabel.setSize(150, 20);
        incommingSpeedLabel.setEnabled(false);
        add(incommingSpeedLabel, params.withGridLoc(0, 4).withInsets(new Insets(0, 15, 5, 5)));

        incommingBytesField = new JTextField("10", 7);
        incommingBytesField.setLocation(15 + 150, nextOff);
        incommingBytesField.setSize(50, 20);
        incommingBytesField.setEnabled(false);
        add(incommingBytesField, params.withGridLoc(1, 4));

        incommingUnitsLabel = new JLabel("Kilo BYTES / second");
        incommingUnitsLabel.setLocation(15 + 50 + 150, nextOff);
        incommingUnitsLabel.setSize(200, 20);
        incommingUnitsLabel.setEnabled(false);
        add(incommingUnitsLabel, params.withGridLoc(2, 4).withAnchor(GridBagConstraints.WEST));
        
        add(new JPanel(), params.withGridLoc(0, 5).withSize(3, 1).withWeight(1, 1));
    }

    public void save() { //save changes
        BandwidthManager.setOutgoingEnabled(enableOutgoing.isSelected());
        try {
            BandwidthManager.setOutgoingMax(Integer.parseInt(outgoingBytesField
                    .getText()));
        } catch (NumberFormatException ex) {
            // nothing
        }

        BandwidthManager.setIncommingEnabled(enableIncomming.isSelected());
        try {
            BandwidthManager.setIncommingMax(Integer
                    .parseInt(incommingBytesField.getText()));
        } catch (NumberFormatException ex) {
            // nothing
        }
    }

    public void reset() { //discard changes and reset values to their defaults.
        setOutgoingEnable(BandwidthManager.isOutgoingEnabled());
        outgoingBytesField.setText("" + BandwidthManager.getOutgoingMax());

        setIncommingEnable(BandwidthManager.isIncommingEnabled());
        incommingBytesField.setText("" + BandwidthManager.getIncommingMax());
    }

    public String getKey() {
        return "Bandwidth";
    }//gets the key structure for the place in the pref panel

    public java.awt.Dimension getPreferredSize() {
        return new java.awt.Dimension(STD_XSIZE, STD_YSIZE);
    }

    private void setOutgoingEnable(boolean bool) {
        enableOutgoing.setSelected(bool);

        outgoingSpeedLabel.setEnabled(bool);
        outgoingBytesField.setEnabled(bool);
        outgoingUnitsLabel.setEnabled(bool);
    }

    private void setIncommingEnable(boolean bool) {
        enableIncomming.setSelected(bool);

        incommingSpeedLabel.setEnabled(bool);
        incommingBytesField.setEnabled(bool);
        incommingUnitsLabel.setEnabled(bool);
    }
}

class BandwidthImpl implements Bandwidth {
    List<BlockedThread> transfers = new ArrayList<>();

    double rate = 10;

    public synchronized void reSleepAll() {
        for (int i = 0; i < transfers.size(); i++) {
            BlockedThread t = transfers.get(i);
            t.reSleep();
        }
    }

    //NOTE: I RE-WROTE THE BELOW 03/01/04 It works well but I need to get rid
    // of the VECTOR <-
    public final int requestBytes(int maxBytes) {
        BlockedThread b = new BlockedThread(maxBytes, transfers, rate);

        synchronized (this) {
            transfers.add(b);
            reSleepAll();
        }

        b.sleepNow();

        synchronized (this) {
            transfers.remove(b);
            reSleepAll();
        }
        return maxBytes;
    }

    public final synchronized void setRate(double rate) {
        this.rate = (rate * 1024) / 1000; //rate was in k/s but not now.
    }
}

interface Bandwidth {
    public int requestBytes(int maxBytes);

    public void setRate(double rate);
}

/*
 * class AlreadyImplementedException extends Exception { public
 * AlreadyImplementedException(String s) { super(s); } }
 */

