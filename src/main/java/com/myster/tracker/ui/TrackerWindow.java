package com.myster.tracker.ui;

import static com.myster.tracker.MysterServer.DOWN;
import static com.myster.tracker.MysterServer.UNTRIED;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import javax.swing.JLabel;
import javax.swing.JPanel;

import com.general.mclist.MCList;
import com.general.mclist.MCListFactory;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableLong;
import com.general.mclist.SortableString;
import com.general.util.Timer;
import com.general.util.Util;
import com.myster.client.datagram.PingResponse;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterIdentity;
import com.myster.tracker.MysterPoolListener;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.tracker.Tracker.ListChangedListener;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowLocationKeeper;
import com.myster.ui.WindowLocationKeeper.WindowLocation;
import com.myster.util.TypeChoice;

public class TrackerWindow extends MysterFrame {
    private static TrackerWindow me;

    private MCList<TrackerMCListItem> list;
    private TypeChoice choice;
    private GridBagLayout gblayout;
    private GridBagConstraints gbconstrains;
    
    private Timer timer = null;
    
    private static Tracker tracker;

    private static MysterFrameContext context;

    public static int initWindowLocations(MysterFrameContext c) {
        WindowLocation[] lastLocs = c.keeper().getLastLocs("Tracker");
        if (lastLocs.length > 0) {
            getInstance().setBounds(lastLocs[0].bounds());
            getInstance().setVisible(lastLocs[0].visible());
        }

        return lastLocs.length;
    }

    public static void init(Tracker tracker, MysterFrameContext c) {
        TrackerWindow.tracker = tracker;
        TrackerWindow.context= c;
    }

    private TrackerWindow(MysterFrameContext c) {
        super(c);
        
        c.keeper().addFrame(this, "Tracker", WindowLocationKeeper.SINGLETON_WINDOW); //never remove

        //Do interface setup:
        gblayout = new GridBagLayout();
        setLayout(gblayout);
        gbconstrains = new GridBagConstraints();
        gbconstrains.fill = GridBagConstraints.BOTH;
        gbconstrains.ipadx = 1;
        gbconstrains.ipady = 1;
        gbconstrains.insets = new Insets(5, 5, 5, 5);

        //init objects
        choice = new TypeChoice(c.tdList(), true);

        list = MCListFactory.buildMCList(7, true, this);

        //add Objects
        addComponent(new JLabel("Tracked servers on network: "), 0, 0, 1, 1, 0, 0);
        addComponent(choice, 0, 1, 1, 1, 1, 0);
        addComponent(new JPanel(), 0, 2, 1, 1, 1, 0);
        addComponent(list.getPane(), 1, 0, 3, 1, 99, 99);

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

        addWindowListener(new MyWindowHandler());
        list.addMCListEventListener(new OpenConnectionHandler(c));

        choice.addItemListener(new ChoiceListener());

        setSize(600, 400);
        setTitle("Tracker");
        
        refreshList = new AtomicBoolean(false);
        tracker.addPoolListener(new MysterPoolListener() {
            @Override
            public void serverRefresh(MysterServer server) {
                refreshList.set(true);
                Util.invokeLater(TrackerWindow.this::resetTimer);
            }

            @Override
            public void serverPing(PingResponse server) {
                refreshList.set(true);
                Util.invokeLater(TrackerWindow.this::resetTimer);          
            }

            @Override
            public void deadServer(MysterIdentity identity) {
                // nothing
            }
        });

        reloadList = new AtomicBoolean(false);
        tracker.addListChangedListener(new ListChangedListener() {
            @Override
            public void serverAddedRemoved(MysterType type) {
                reloadList.set(true);
                Util.invokeLater(() -> {
                    if (type.equals(getMysterType())) {
                        TrackerWindow.this.resetTimer();
                    }
                });
            }

            @Override
            public void lanServerAddedRemoved() {
                reloadList.set(true);
                Util.invokeLater(() -> {
                    if (getMysterType() == null) {
                        TrackerWindow.this.resetTimer();
                    }
                });
            }
        });

        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                refreshTheList();
            }
        });
    }
    
    private void resetTimer() {
        if (!list.getPane().isShowing()) {
            return;
        }
        
        if (timer != null) {
            return;
        }
        
        timer = new Timer(this::checkForRefresh, 1000);
    }

    public void show() {
        loadTheList();
        super.show();
    }

    /**
     * Singleton
     */
    public static synchronized TrackerWindow getInstance() {
        if (me == null) {
            me = new TrackerWindow(context);
        }

        return me;
    }

    /**
     * Makes grid bag layout less nasty.
     */
    public void addComponent(Component c,
                             int row,
                             int column,
                             int width,
                             int height,
                             int weightx,
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
    Optional<MysterType> getMysterType() {
        return choice.getType();
    }

    List<TrackerMCListItem> itemsinlist;

    private final AtomicBoolean refreshList;
    private final AtomicBoolean reloadList;

    /**
     * Remakes the MCList. This routine is called every few minutes to update the tracker window
     * with the status of the tracker.
     */
    private void loadTheList() {
        int currentIndex = list.getSelectedIndex();
        list.clearAll();
        itemsinlist = new ArrayList<>();
        List<MysterServer> servers = choice.isLan() ? tracker.getAllLan() : tracker.getAll(getMysterType().get());
        TrackerMCListItem[] m = new TrackerMCListItem[servers.size()];

        for (int i = 0; i < servers.size(); i++) {
            m[i] = new TrackerMCListItem((servers.get(i)), getMysterType());
            itemsinlist.add(m[i]);
        }
        list.addItem(m);
        list.select(currentIndex); //not a problem if out of bounds..
        
        cancelTimer();
    }

    /**
     * Refreshes the list information with new information from the tracker.
     */
    private void checkForRefresh() {
        boolean refresh = refreshList.get();
        boolean reload = reloadList.get();
        
        refreshList.set(false);
        reloadList.set(false);

        if (reload) {
            loadTheList();
        } else if (refresh) {
            refreshTheList();
        }

        cancelTimer();
    }

    private void refreshTheList() {
        for (int i = 0; i < itemsinlist.size(); i++) {
            (itemsinlist.get(i)).refresh();
        }
        list.repaint();
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancelTimer();
            timer = null;
        }
    }

    private class ChoiceListener implements ItemListener {
        public ChoiceListener() {
        }

        public void itemStateChanged(ItemEvent e) {
            loadTheList();
        }
    }

    private class MyWindowHandler extends WindowAdapter {
        public void windowClosing(WindowEvent e) {
            setVisible(false);
        }
    }

    static class TrackerMCListItem extends MCListItemInterface<TrackerMCListItem> {
        private final MysterServer server;
        private final Optional<MysterType> type;

        private final Sortable<?>[] sortables = new Sortable<?>[7];

        public TrackerMCListItem(MysterServer s, Optional<MysterType> t) {
            server = s;
            type = t;
            refresh();
        }

        public Sortable<?> getValueOfColumn(int i) {
            return sortables[i];

        }

        public void refresh() {
            sortables[0] = new SortableString(server.getServerName());
            sortables[1] = type.isPresent() ? new SortableLong(server.getNumberOfFiles(type.get()))
                    : new SortableString("");
            sortables[2] = new SortableStatus(server.getStatus(), server.isUntried());
            sortables[3] = new SortableString("" + String.join(", ",
                                                               Stream.of(server.getAddresses())
                                                                       .map(Object::toString)
                                                                       .toArray(String[]::new)));
            sortables[4] = new SortablePing(server.getPingTime());
            sortables[5] =
                    type.isPresent() ? new SortableRank(((long) (100 * server.getRank(type.get()))))
                            : new SortableString("");
            sortables[6] = new SortableUptime((server.getStatus() ? server.getUptime() : -2));
        }

        public TrackerMCListItem getObject() {
            return this;
        }
        
        public MysterAddress getBestAddress() {
            return server.getBestAddress().orElse(server.getAddresses()[0]);
        }

        public String toString() {
            return "" + Arrays.asList(server.getAddresses());
        }

        private static class SortablePing extends SortableLong {
            public static final int UNKNOWN_SORTABLE = 100000;

            public static final int DOWN_SORTABLE = 100001;

            public SortablePing(long c) {
                super(c);
                if (c == UNTRIED) {
                    number = UNKNOWN_SORTABLE;
                } else if (c == DOWN) {
                    number = DOWN_SORTABLE;
                } else {
                    number = c;
                }
            }

            public String toString() {
                switch ((int) number) {
                case UNKNOWN_SORTABLE:
                    return "-";
                case DOWN_SORTABLE:
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
                    return "low";
                else
                    return super.toString();
            }
        }

        private static class SortableStatus implements Sortable<Boolean[]> {
            boolean status, isUntried;

            public SortableStatus(boolean status, boolean isUntried) {
                this.isUntried = isUntried;
                this.status = status;
            }

            public boolean isLessThan(Sortable<?> temp) {
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

            public boolean isGreaterThan(Sortable<?> temp) {
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

            public boolean equals(Object m) {
                SortableStatus other = (SortableStatus) m;
                return (other.status == status && other.isUntried == isUntried);
            }

            public Boolean[] getValue() {
                return new Boolean[] {status, isUntried};
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
                return com.general.util.Util.getLongAsTime(number);
            }
        }
    }
}
