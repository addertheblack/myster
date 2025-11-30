package com.myster.tracker.ui;

import static com.myster.tracker.MysterServer.DOWN;
import static com.myster.tracker.MysterServer.UNTRIED;

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
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import com.general.mclist.AbstractMCListItemInterface;
import com.general.mclist.JMCList;
import com.general.mclist.MCListFactory;
import com.general.mclist.Sortable;
import com.general.mclist.SortableLong;
import com.general.mclist.SortableString;
import com.general.util.GridBagBuilder;
import com.general.util.Timer;
import com.general.util.Util;
import com.myster.net.MysterAddress;
import com.myster.net.datagram.client.PingResponse;
import com.myster.tracker.BookmarkMysterServerList;
import com.myster.tracker.MysterIdentity;
import com.myster.tracker.MysterPoolListener;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.tracker.Tracker.ListChangedListener;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowPrefDataKeeper;
import com.myster.ui.WindowPrefDataKeeper.PrefData;
import com.myster.util.ContextMenu;
import com.myster.util.TypeChoice;

public class TrackerWindow extends MysterFrame {
    private static final String TYPE = "Type";

    private static final String BOOKMARK = "Bookmark";

    private static final String LAN = "LAN";

    private static final String SELECTED_ITEM = "Selected Item";

    private static final String SELECTED_TYPE = "Selected Type";

    private static TrackerWindow me;

    private final JMCList<MysterServer> list;
    private final TypeChoice choice;
    private Timer timer = null;
    
    private static Tracker tracker;

    private static MysterFrameContext context;

    private record TrackerWindowData(String selectedItem, String selectedType) {}
    
    public static int initWindowLocations(MysterFrameContext c) {
        List<PrefData<TrackerWindowData>> lastLocs = c.keeper()
                .getLastLocs("Tracker",
                             (p) -> new TrackerWindowData(p.get(SELECTED_ITEM, ""),
                                                          p.get(SELECTED_TYPE, "")));
        if (lastLocs.size() > 0) {
            var location = lastLocs.get(0).location();
            getInstance().setBounds(location.bounds());
            getInstance().setVisible(location.visible());
            
            
            var data = lastLocs.get(0).data();
            TypeChoice choice = getInstance().choice;
            if (data.selectedType().equals(LAN)) {
                choice.selectLan();
            } else if (data.selectedType().equals(BOOKMARK)) {
                choice.selectBookmarks();
            } else if (data.selectedItem() != "") {
                for (int i = 0; !choice.getType(i).isEmpty(); i++) {
                    if (choice.getType(i).map(t -> t.toHexString()).orElse("").equals(data.selectedItem())) {
                        choice.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }

        return lastLocs.size();
    }

    public static void init(Tracker tracker, MysterFrameContext c) {
        TrackerWindow.tracker = tracker;
        TrackerWindow.context= c;
    }

    private TrackerWindow(MysterFrameContext c) {
        super(c);
        //init objects
        choice = new TypeChoice(c.tdList(), true);
        
        var windowDataSaver = c.keeper().addFrame(this, (p) -> {
            var selectedType =  choice.isLan() ? LAN
                            : choice.isBookmark() ? BOOKMARK 
                            : TYPE;
                            
            p.put(SELECTED_TYPE, selectedType);
            p.put(SELECTED_ITEM, getMysterType().map(t -> t.toHexString()).orElse(""));
        }, "Tracker", WindowPrefDataKeeper.SINGLETON_WINDOW); //never remove

        //Do interface setup:
        setLayout(new GridBagLayout());
        var builder = new GridBagBuilder()
            .withFill(GridBagConstraints.BOTH)
            .withIpad(1, 1)
            .withInsets(new Insets(5, 5, 5, 5));


        list = MCListFactory.buildMCList(8, true, this);

        //add Objects
        add(new JLabel("Tracked servers on network: "), builder.withGridLoc(0, 0).withSize(1, 1).withWeight(0, 0));
        add(choice, builder.withGridLoc(1, 0).withSize(1, 1).withWeight(1, 0));
        add(new JPanel(), builder.withGridLoc(2, 0).withSize(1, 1).withWeight(1, 0));
        add(list.getPane(), builder.withGridLoc(0, 1).withSize(3, 1).withWeight(99, 99));

        //Add Event handlers

        //other stuff
        list.setColumnName(0, ""); // Bookmark column - no header text
        list.setColumnName(1, "Server Name");
        list.setColumnName(2, "# Files");
        list.setColumnName(3, "Status");
        list.setColumnName(4, "IP");
        list.setColumnName(5, "Ping");
        list.setColumnName(6, "Rank");
        list.setColumnName(7, "Uptime");

        list.setColumnWidth(0, 30); // Small width for bookmark icon
        list.setColumnWidth(1, 150);
        list.setColumnWidth(2, 70);
        list.setColumnWidth(3, 70);
        list.setColumnWidth(4, 150);
        list.setColumnWidth(5, 70);
        list.setColumnWidth(6, 70);
        list.setColumnWidth(7, 70);

        // Set up custom renderer for bookmark column
        list.getTableHeader().getColumnModel().getColumn(0).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            private final com.formdev.flatlaf.extras.FlatSVGIcon bookmarkIcon = 
                com.general.util.IconLoader.loadSvg(com.general.util.IconLoader.class, "bookmark-svgrepo-com");
            
            @Override
            public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table,
                                                                     Object value,
                                                                     boolean isSelected,
                                                                     boolean hasFocus,
                                                                     int row,
                                                                     int column) {
                javax.swing.JLabel label = (javax.swing.JLabel) super.getTableCellRendererComponent(
                    table, "", isSelected, hasFocus, row, column);
                
                // Check if this server is bookmarked
                if (value instanceof TrackerMCListItem.SortableBookmark) {
                    TrackerMCListItem.SortableBookmark sortable = (TrackerMCListItem.SortableBookmark) value;
                    if (sortable.getValue()) {
                        // Scale icon to fit row height
                        bookmarkIcon.setColorFilter(new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(
                            color -> label.getForeground()));
                        label.setIcon(bookmarkIcon.derive(table.getRowHeight() - 4, table.getRowHeight() - 4));
                        label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                    } else {
                        label.setIcon(null);
                    }
                }
                
                return label;
            }
        });

        addWindowListener(new MyWindowHandler());
        list.addMCListEventListener(new OpenConnectionHandler(c));

        // Create menu items manually (not using ContextMenu helpers) so we can
        // control enabled state
        JMenuItem bookmarkMenuItem = new JMenuItem("Bookmark Server");
        bookmarkMenuItem.addActionListener(e -> {
            int index = list.getSelectedRow();
            if (index == -1) {
                return;
            }

            MysterServer server = list.getMCListItem(index).getObject();
            tracker.addBookmark(new BookmarkMysterServerList.Bookmark(server.getIdentity()));
        });

        JMenuItem removeBookmarkMenuItem = new JMenuItem("Remove Bookmark");
        removeBookmarkMenuItem.addActionListener(e -> {
            int index = list.getSelectedRow();
            if (index == -1) {
                return;
            }

            tracker.removeBookmark(list.getMCListItem(index).getObject().getIdentity());
        });
        
        // Update menu item states based on selection and bookmark status
        Runnable updateMenuStates = () -> {
            int selectedRow = list.getSelectedRow();
            if (selectedRow == -1) {
                bookmarkMenuItem.setEnabled(false);
                removeBookmarkMenuItem.setEnabled(false);
                return;
            }
            
            MysterServer server = list.getMCListItem(selectedRow).getObject();
            boolean isBookmarked = tracker.getBookmark(server.getIdentity()).isPresent();
            
            bookmarkMenuItem.setEnabled(!isBookmarked);
            removeBookmarkMenuItem.setEnabled(isBookmarked);
        };
        
        ContextMenu.addPopUpMenu(list, updateMenuStates, bookmarkMenuItem, removeBookmarkMenuItem);



        choice.addItemListener(new ChoiceListener());
        choice.addItemListener(_ -> windowDataSaver.run());
        choice.addItemListener(_ -> updateMenuStates.run());

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
                Util.invokeLater(() -> {
                    if (type.equals(getMysterType())) {
                        reloadList.set(true);
                        TrackerWindow.this.resetTimer();
                    }
                });
            }

            @Override
            public void lanServerAddedRemoved() {
                Util.invokeLater(() -> {
                    if (choice.isLan()) {
                        reloadList.set(true);
                        TrackerWindow.this.resetTimer();
                    }
                });
            }

            @Override
            public void bookmarkServerAddedRemoved() {
                Util.invokeLater(() -> {
                    // Always refresh to update bookmark icons, not just when viewing bookmarks
                    refreshList.set(true);
                    
                    
                    if (choice.isBookmark()) {
                        reloadList.set(true); // only apply this for bookmarks type
                    }
                    
                    // instant update for the icon.. tiny delay for the delete..
                    // note that this handler applied whatever TYPE is selected!
                    checkForRefresh(); 
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
        
        timer = new Timer(this::checkForRefresh, 500);
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
     * Returns the selected type.
     */
    Optional<MysterType> getMysterType() {
        return choice.getType();
    }

    private List<TrackerMCListItem> itemsinlist;

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
        List<MysterServer> servers = extractServers();
        TrackerMCListItem[] m = new TrackerMCListItem[servers.size()];

        for (int i = 0; i < servers.size(); i++) {
            m[i] = new TrackerMCListItem((servers.get(i)), getMysterType());
            itemsinlist.add(m[i]);
        }
        list.addItem(m);
        list.select(currentIndex); //not a problem if out of bounds..
        
        cancelTimer();
    }

    private List<MysterServer> extractServers() {
        if (choice.isLan()) {
            return tracker.getAllLan();
        } else if (choice.isBookmark()) {
            return tracker.getAllBookmarks();
        } else {
            return tracker.getAll(getMysterType().get());
        }
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

    static class TrackerMCListItem extends AbstractMCListItemInterface<MysterServer> {
        private final MysterServer server;
        private final Optional<MysterType> type;

        private final Sortable<?>[] sortables = new Sortable<?>[8];

        public TrackerMCListItem(MysterServer s, Optional<MysterType> t) {
            server = s;
            type = t;
            refresh();
        }

        public Sortable<?> getValueOfColumn(int i) {
            return sortables[i];

        }

        public void refresh() {
            // Column 0: Bookmark status
            sortables[0] = new SortableBookmark(tracker.getBookmark(server.getIdentity()).isPresent());
            // Column 1-7: Existing data shifted by one
            sortables[1] = new SortableString(server.getServerName());
            sortables[2] = type.isPresent() ? new SortableLong(server.getNumberOfFiles(type.get()))
                    : new SortableString("");
            sortables[3] = new SortableStatus(server.getStatus(), server.isUntried());
            sortables[4] = new SortableString("" + String.join(", ",
                                                               Stream.of(server.getAddresses())
                                                                       .map(Object::toString)
                                                                       .toArray(String[]::new)));
            sortables[5] = new SortablePing(server.getPingTime());
            sortables[6] =
                    type.isPresent() ? new SortableRank(((long) (100 * server.getRank(type.get()))))
                            : new SortableString("");
            sortables[7] = new SortableUptime((server.getStatus() ? server.getUptime() : -2));
        }

        public MysterServer getObject() {
            return server;
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

            public boolean isLessThan(Sortable<Boolean[]> temp) {
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

            public boolean isGreaterThan(Sortable<Boolean[]> temp) {
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

        //Immutable
        private static class SortableBookmark implements Sortable<Boolean> {
            private final boolean isBookmarked;

            public SortableBookmark(boolean isBookmarked) {
                this.isBookmarked = isBookmarked;
            }

            @Override
            public boolean isLessThan(Sortable<Boolean> other) {
                if (other instanceof SortableBookmark) {
                    return !isBookmarked && ((SortableBookmark) other).isBookmarked;
                }
                return false;
            }

            @Override
            public boolean isGreaterThan(Sortable<Boolean> other) {
                if (other instanceof SortableBookmark) {
                    return isBookmarked && !((SortableBookmark) other).isBookmarked;
                }
                return false;
            }

            @Override
            public Boolean getValue() {
                return isBookmarked;
            }

            @Override
            public String toString() {
                return ""; // No text display, icon only
            }
        }
    }
}
