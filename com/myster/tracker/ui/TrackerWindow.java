package com.myster.tracker.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Vector;

import com.general.mclist.MCList;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableLong;
import com.general.mclist.SortableString;
import com.general.util.TimerThread;
import com.myster.tracker.IPListManager;
import com.myster.tracker.IPListManagerSingleton;
import com.myster.tracker.MysterServer;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrame;
import com.myster.util.OpenConnectionHandler;
import com.myster.util.TypeChoice;

public class TrackerWindow extends MysterFrame {
    private static TrackerWindow me;// = new TrackerWindow();

    private MyThread updater;

    private MCList list;

    private TypeChoice choice;

    GridBagLayout gblayout;

    GridBagConstraints gbconstrains;

    private static com.myster.ui.WindowLocationKeeper keeper = new com.myster.ui.WindowLocationKeeper(
            "Tracker");

    public static void initWindowLocations() {
        Rectangle[] rect = com.myster.ui.WindowLocationKeeper.getLastLocs("Tracker");
        if (rect.length > 0) {
            getInstance().setBounds(rect[0]);
            getInstance().setVisible(true);
        }
    }

    private TrackerWindow() {
        keeper.addFrame(this); //never remove

        //Do interface setup:
        gblayout = new GridBagLayout();
        setLayout(gblayout);
        gbconstrains = new GridBagConstraints();
        gbconstrains.fill = GridBagConstraints.BOTH;
        gbconstrains.ipadx = 1;
        gbconstrains.ipady = 1;

        //init objects
        choice = new TypeChoice();

        list = new MCList(7, true, this);

        //add Objects
        addComponent(choice, 0, 0, 1, 1, 99, 0);
        addComponent(list.getPane(), 1, 0, 1, 1, 99, 99);

        //Add Event handlers

        //other stuff

        list.setColumnName(0, "Server Name");
        list.setColumnName(1, "# Files");
        list.setColumnName(2, "Status");
        list.setColumnName(3, "IP");
        list.setColumnName(4, "Ping");
        list.setColumnName(5, "Rank");
        list.setColumnName(6, "Uptime");

        list.setColumnWidth(0, 150);
        list.setColumnWidth(1, 70);
        list.setColumnWidth(2, 70);
        list.setColumnWidth(3, 150);
        list.setColumnWidth(4, 70);
        list.setColumnWidth(5, 70);
        list.setColumnWidth(6, 70);
        //loadList();

        addWindowListener(new MyWindowHandler());
        list.addMCListEventListener(new OpenConnectionHandler());

        choice.addItemListener(new ChoiceListener());

        updater = new MyThread();
        //updater.start();

        setSize(600, 400);
        setTitle("Tracker");
        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                System.out.println("SHOWN!");

                updater.flagToEnd();
                updater = new MyThread();
                updater.start();
            }

            public void componentHidden(ComponentEvent e) {
                System.out.println("HIDDEN!");
                updater.flagToEnd();
            }
        });
    }

    public void show() {
        loadList();
        super.show();
    }

    /**
     * Singleton
     */
    public static synchronized TrackerWindow getInstance() {
        if (me == null) {
            me = new TrackerWindow();
        }

        return me;
    }

    /**
     * Makes grid bag layout less nasty.
     */
    public void addComponent(Component c, int row, int column, int width, int height, int weightx,
            int weighty) {
        gbconstrains.gridx = column;
        gbconstrains.gridy = row;

        gbconstrains.gridwidth = width;
        gbconstrains.gridheight = height;

        gbconstrains.weightx = weightx;
        gbconstrains.weighty = weighty;

        gblayout.setConstraints(c, gbconstrains);

        add(c);

    }

    /**
     * Returns the selected type.
     */

    public synchronized MysterType getType() {
        return choice.getType();
    }

    Vector itemsinlist;

    /**
     * Remakes the MCList. This routine is called every few minutes to update the tracker window
     * with the status of the tracker.
     */
    private synchronized void loadList() {
        int currentIndex = list.getSelectedIndex();
        list.clearAll();
        itemsinlist = new Vector(IPListManager.LISTSIZE);
        IPListManager manager = IPListManagerSingleton.getIPListManager();
        Vector vector = manager.getAll(getType());
        TrackerMCListItem[] m = new TrackerMCListItem[vector.size()];

        for (int i = 0; i < vector.size(); i++) {
            m[i] = new TrackerMCListItem((MysterServer) (vector.elementAt(i)), getType());
            itemsinlist.addElement(m[i]);
        }
        list.addItem(m);
        list.select(currentIndex); //not a problem if out of bounds..
    }

    /**
     * Refreshes the list information with new information form the tracker.
     */
    private synchronized void refreshTheList() {
        for (int i = 0; i < itemsinlist.size(); i++) {
            ((TrackerMCListItem) (itemsinlist.elementAt(i))).refresh();
        }
        list.repaint();
    }

    /**
     * This thread is responsible for keeping the tracker window updated. It does so by polling the
     * IPListManager repeataly. Every once in a while it reloads the information completely.
     */
    private class MyThread extends TimerThread {
        private long counter = 0;

        public MyThread() {
            super(5000);
        }

        public void run() {
            counter++;
            if (counter % 6 == 5) {
                loadList();
                counter = 0;
            } else {
                refreshTheList();
            }
        }
    }

    private class ChoiceListener implements ItemListener {
        public ChoiceListener() {
        }

        public void itemStateChanged(ItemEvent e) {
            loadList();
        }
    }

    private class MyWindowHandler extends WindowAdapter {
        public void windowClosing(WindowEvent e) {
            hide();
        }
    }

    private static class TrackerMCListItem extends MCListItemInterface {
        MysterServer server;

        Sortable sortables[] = new Sortable[7];

        IPListManager manager = IPListManagerSingleton.getIPListManager();

        MysterType type;

        public TrackerMCListItem(MysterServer s, MysterType t) {
            server = s;
            type = t;
            refresh();
        }

        public Sortable getValueOfColumn(int i) {
            return sortables[i];

        }

        public void refresh() {
            if (manager.getQuickServerStats(server.getAddress()) == null) {
                sortables[0] = new SortableString("" + server.getAddress());
                sortables[1] = new SortableLong(0);
                sortables[2] = new SortableStatus(false, true);
                sortables[3] = new SortableString("" + server.getAddress());
                sortables[4] = new SortablePing(-1);
                sortables[5] = new SortableRank(-1);
                sortables[6] = new SortableUptime(-1);
            } else {
                sortables[0] = new SortableString(server.getServerIdentity());
                sortables[1] = new SortableLong(server.getNumberOfFiles(type));
                sortables[2] = new SortableStatus(server.getStatus(), server.isUntried());
                sortables[3] = new SortableString("" + server.getAddress());
                sortables[4] = new SortablePing(server.getPingTime());
                sortables[5] = new SortableRank(((long) (100 * server.getRank(type))));
                sortables[6] = new SortableUptime((server.getStatus() ? server.getUptime() : -2));
            }
        }

        public Object getObject() {
            return this;
        }

        public String toString() {
            return "" + server.getAddress();
        }

        private static class SortablePing extends SortableLong {
            public static final int UNKNOWN = 1000000;

            public static final int DOWN = 1000001;

            public SortablePing(long c) {
                super(c);
                if (c == -1) {
                    number = UNKNOWN;
                } else if (c == -2) {
                    number = DOWN;
                } else {
                    number = c;
                }
            }

            public String toString() {
                switch ((int) number) {
                case UNKNOWN:
                    return "-";
                case DOWN:
                    return "Timeout";
                default:
                    return number + "ms";
                }
            }
        }

        private static class SortableRank extends SortableLong {
            public SortableRank(long c) {
                super(c);
            }

            public String toString() {
                if (number < -1000)
                    return "-inf";
                else
                    return super.toString();
            }
        }

        private static class SortableStatus implements Sortable {
            boolean status, isUntried;

            public SortableStatus(boolean status, boolean isUntried) {
                this.isUntried = isUntried;
                this.status = status;
            }

            public boolean isLessThan(Sortable temp) {
                SortableStatus other = (SortableStatus) (temp);

                if (isUntried) {
                    if (other.isUntried) {
                        return (!status && other.status);
                    } else {
                        return true;
                    }
                } else {
                    if (other.isUntried) {
                        return false;
                    } else {
                        return (!status && other.status);
                    }
                }
            }

            public boolean isGreaterThan(Sortable temp) {
                SortableStatus other = (SortableStatus) (temp);

                if (isUntried) {
                    if (other.isUntried) {
                        return (status && !other.status);
                    } else {
                        return false;
                    }
                } else {
                    if (other.isUntried) {
                        return true;
                    } else {
                        return (status && !other.status);
                    }
                }
            }

            public boolean equals(Sortable m) {
                SortableStatus other = (SortableStatus) m;
                return (other.status == status && other.isUntried == isUntried);
            }

            public Object getValue() {
                return null;//caution
            }

            public String toString() {
                return (isUntried ? "-" : (status ? "up" : "down"));
            }
        }

        //Immutable
        private static class SortableUptime extends SortableLong {
            public SortableUptime(long time) {
                super(time);
            }

            public String toString() {
                return com.myster.Myster.getUptimeAsString(number);
            }
        }
    }

}