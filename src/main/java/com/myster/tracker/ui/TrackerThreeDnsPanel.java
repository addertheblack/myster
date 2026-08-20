package com.myster.tracker.ui;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.general.mclist.AbstractMCListItemInterface;
import com.general.mclist.JMCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.general.mclist.MCListFactory;
import com.general.mclist.Sortable;
import com.general.mclist.SortableLong;
import com.general.mclist.SortableString;
import com.general.util.IconLoader;
import com.myster.cid.ServerCid;
import com.myster.client.ui.ClientWindow;
import com.myster.threedns.ThreeDnsFingerEntry;
import com.myster.threedns.ThreeDnsTargetSlotSnapshot;
import com.myster.tracker.BookmarkMysterServerList;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.ui.MysterFrameContext;
import com.myster.util.ContextMenu;

import static com.myster.tracker.MysterServer.DOWN;
import static com.myster.tracker.MysterServer.UNTRIED;

/**
 * Dedicated table for TrackerWindow's 3DNS view.
 * <p>
 * 3DNS is a target-slot inspection view rather than a plain server list, so it
 * owns a separate MCList row type and columns instead of adding 3DNS branches to
 * TrackerWindow's generic {@code MysterServer} table.
 *
 * TODO: Resolve duplicate code in TrackerWindow - building the contextual menu & bookmark rendered
 */
public class TrackerThreeDnsPanel extends JPanel {
    private static final int COLUMN_BOOKMARK = 0;
    private static final int COLUMN_OFFSET = 1;
    private static final int COLUMN_SIDE = 2;
    private static final int COLUMN_TARGET_CID = 3;
    private static final int COLUMN_SERVER_CID = 4;
    private static final int COLUMN_SERVER_NAME = 5;
    private static final int COLUMN_ADDRESS = 6;
    private static final int COLUMN_STATUS = 7;
    private static final int COLUMN_PING = 8;
    private static final int COLUMN_UPTIME = 9;
    private static final int COLUMN_COUNT = 10;

    private final Tracker tracker;
    private final MysterFrameContext context;
    private final JMCList<ThreeDnsTrackerRow> list;
    private final List<ThreeDnsMCListItem> itemsInList = new ArrayList<>();

    public TrackerThreeDnsPanel(Tracker tracker, MysterFrameContext context) {
        super(new BorderLayout());
        this.tracker = tracker;
        this.context = context;
        this.list = MCListFactory.buildMCList(COLUMN_COUNT, true, this);

        configureColumns();
        configureBookmarkRenderer();
        configureActions();

        add(list.getPane(), BorderLayout.CENTER);
    }

    public void load() {
        int currentIndex = list.getSelectedIndex();
        list.clearAll();
        itemsInList.clear();

        List<ThreeDnsMCListItem> items = new ArrayList<>();
        for (ThreeDnsTargetSlotSnapshot slot : tracker.getThreeDnsTargetSlots()) {
            for (ThreeDnsFingerEntry entry : slot.left()) {
                items.add(new ThreeDnsMCListItem(row(slot, entry), tracker));
            }
            for (ThreeDnsFingerEntry entry : slot.right()) {
                items.add(new ThreeDnsMCListItem(row(slot, entry), tracker));
            }
        }

        @SuppressWarnings("unchecked")
        ThreeDnsMCListItem[] itemArray = items.toArray(ThreeDnsMCListItem[]::new);
        list.addItem(itemArray);
        itemsInList.addAll(items);
        list.select(currentIndex);
    }

    public void refresh() {
        for (ThreeDnsMCListItem item : itemsInList) {
            item.refresh();
        }
        list.repaint();
    }

    private ThreeDnsTrackerRow row(ThreeDnsTargetSlotSnapshot slot, ThreeDnsFingerEntry entry) {
        return new ThreeDnsTrackerRow(entry.server(),
                                      slot.targetCid(),
                                      entry.serverCid(),
                                      slot.bitIndex(),
                                      entry.side(),
                                      entry.address(),
                                      entry.updateTimeMs());
    }

    private void configureColumns() {
        list.setColumnName(COLUMN_BOOKMARK, "");
        list.setColumnName(COLUMN_OFFSET, "Offset");
        list.setColumnName(COLUMN_SIDE, "Side");
        list.setColumnName(COLUMN_TARGET_CID, "Target CID");
        list.setColumnName(COLUMN_SERVER_CID, "Server CID");
        list.setColumnName(COLUMN_SERVER_NAME, "Server Name");
        list.setColumnName(COLUMN_ADDRESS, "Address");
        list.setColumnName(COLUMN_STATUS, "Status");
        list.setColumnName(COLUMN_PING, "Ping");
        list.setColumnName(COLUMN_UPTIME, "Uptime");

        list.setColumnWidth(COLUMN_BOOKMARK, 30);
        list.setColumnWidth(COLUMN_OFFSET, 90);
        list.setColumnWidth(COLUMN_SIDE, 60);
        list.setColumnWidth(COLUMN_TARGET_CID, 120);
        list.setColumnWidth(COLUMN_SERVER_CID, 120);
        list.setColumnWidth(COLUMN_SERVER_NAME, 150);
        list.setColumnWidth(COLUMN_ADDRESS, 150);
        list.setColumnWidth(COLUMN_STATUS, 70);
        list.setColumnWidth(COLUMN_PING, 70);
        list.setColumnWidth(COLUMN_UPTIME, 70);
    }

    private void configureBookmarkRenderer() {
        list.getTableHeader().getColumnModel().getColumn(COLUMN_BOOKMARK)
                .setCellRenderer(new DefaultTableCellRenderer() {
                    private final FlatSVGIcon bookmarkIcon =
                            IconLoader.loadSvg(IconLoader.class, "bookmark-svgrepo-com");

                    @Override
                    public Component getTableCellRendererComponent(JTable table,
                                                                    Object value,
                                                                    boolean isSelected,
                                                                    boolean hasFocus,
                                                                    int row,
                                                                    int column) {
                        JLabel label = (JLabel) super.getTableCellRendererComponent(
                                table, "", isSelected, hasFocus, row, column);
                        if (value instanceof ThreeDnsMCListItem.SortableBookmark sortable
                                && sortable.getValue()) {
                            bookmarkIcon.setColorFilter(new FlatSVGIcon.ColorFilter(
                                    color -> label.getForeground()));
                            label.setIcon(bookmarkIcon.derive(table.getRowHeight() - 4,
                                                              table.getRowHeight() - 4));
                            label.setHorizontalAlignment(JLabel.CENTER);
                        } else {
                            label.setIcon(null);
                        }
                        return label;
                    }
                });
    }

    private void configureActions() {
        list.addMCListEventListener(new MCListEventAdapter() {
            @Override
            public void doubleClick(MCListEvent event) {
                selectedRow().ifPresent(row -> {
                    var data = new ClientWindow.ClientWindowData(
                            Optional.of(row.retainedAddress().toString()),
                            Optional.empty(),
                            Optional.empty());
                    context.clientWindowProvider().getOrCreateWindow(data).show();
                });
            }
        });

        JMenuItem bookmarkMenuItem = new JMenuItem("Bookmark Server");
        bookmarkMenuItem.addActionListener(e -> selectedRow()
                .ifPresent(row -> tracker.addBookmark(
                        new BookmarkMysterServerList.Bookmark(row.server().getIdentity()))));

        JMenuItem removeBookmarkMenuItem = new JMenuItem("Remove Bookmark");
        removeBookmarkMenuItem.addActionListener(e -> selectedRow()
                .ifPresent(row -> tracker.removeBookmark(row.server().getIdentity())));

        Runnable updateMenuStates = () -> {
            var selectedRow = selectedRow();
            if (selectedRow.isEmpty()) {
                bookmarkMenuItem.setEnabled(false);
                removeBookmarkMenuItem.setEnabled(false);
                return;
            }

            boolean isBookmarked = tracker.getBookmark(selectedRow.get().server().getIdentity()).isPresent();
            bookmarkMenuItem.setEnabled(!isBookmarked);
            removeBookmarkMenuItem.setEnabled(isBookmarked);
        };

        ContextMenu.addPopUpMenu(list, updateMenuStates, bookmarkMenuItem, removeBookmarkMenuItem);
    }

    private Optional<ThreeDnsTrackerRow> selectedRow() {
        int selectedRow = list.getSelectedRow();
        if (selectedRow == -1) {
            return Optional.empty();
        }
        return Optional.of(list.getMCListItem(selectedRow).getObject());
    }

    private static final class ThreeDnsMCListItem extends AbstractMCListItemInterface<ThreeDnsTrackerRow> {
        private final ThreeDnsTrackerRow row;
        private final Tracker tracker;
        private final Sortable<?>[] sortables = new Sortable<?>[COLUMN_COUNT];

        private ThreeDnsMCListItem(ThreeDnsTrackerRow row, Tracker tracker) {
            this.row = row;
            this.tracker = tracker;
            refresh();
        }

        @Override
        public Sortable<?> getValueOfColumn(int i) {
            return sortables[i];
        }

        @Override
        public ThreeDnsTrackerRow getObject() {
            return row;
        }

        private void refresh() {
            MysterServer server = row.server();
            sortables[COLUMN_BOOKMARK] =
                    new SortableBookmark(tracker.getBookmark(server.getIdentity()).isPresent());
            sortables[COLUMN_OFFSET] = new SortableOffset(row.bitIndex());
            sortables[COLUMN_SIDE] = new SortableString(row.side().name());
            sortables[COLUMN_TARGET_CID] = new SortableCid(row.targetCid());
            sortables[COLUMN_SERVER_CID] = new SortableCid(row.serverCid());
            sortables[COLUMN_SERVER_NAME] = new SortableString(safeString(server.getServerName()));
            sortables[COLUMN_ADDRESS] = new SortableString(row.retainedAddress().toString());
            sortables[COLUMN_STATUS] = new SortableStatus(server.isUp(), server.isUntried());
            sortables[COLUMN_PING] = new SortablePing(server.getPingTime());
            sortables[COLUMN_UPTIME] = new SortableUptime(server.isUp() ? server.getUptime() : -2);
        }

        private static String safeString(String value) {
            return value == null ? "" : value;
        }

        private static final class SortableOffset extends SortableLong {
            private SortableOffset(int bitIndex) {
                super(bitIndex);
            }

            @Override
            public String toString() {
                return "<html>+2<sup>" + number + "</sup></html>";
            }
        }

        private static final class SortableCid implements Sortable<ServerCid> {
            private final ServerCid cid;
            private final String hex;

            private SortableCid(ServerCid cid) {
                this.cid = cid;
                this.hex = cid.asHex();
            }

            @Override
            public boolean isLessThan(Sortable<ServerCid> other) {
                return cid.compareTo(other.getValue()) < 0;
            }

            @Override
            public boolean isGreaterThan(Sortable<ServerCid> other) {
                return cid.compareTo(other.getValue()) > 0;
            }

            @Override
            public ServerCid getValue() {
                return cid;
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof SortableCid sortableCid && cid.equals(sortableCid.cid);
            }

            @Override
            public String toString() {
                return hex;
            }
        }

        private static final class SortablePing extends SortableLong {
            private static final int UNKNOWN_SORTABLE = 100000;
            private static final int DOWN_SORTABLE = 100001;

            private SortablePing(long pingTime) {
                super(pingTime);
                if (pingTime == UNTRIED) {
                    number = UNKNOWN_SORTABLE;
                } else if (pingTime == DOWN) {
                    number = DOWN_SORTABLE;
                }
            }

            @Override
            public String toString() {
                return switch ((int) number) {
                    case UNKNOWN_SORTABLE -> "-";
                    case DOWN_SORTABLE -> "Timeout";
                    default -> number + "ms";
                };
            }
        }

        private static final class SortableStatus implements Sortable<Boolean[]> {
            private final boolean status;
            private final boolean isUntried;

            private SortableStatus(boolean status, boolean isUntried) {
                this.status = status;
                this.isUntried = isUntried;
            }

            @Override
            public boolean isLessThan(Sortable<Boolean[]> temp) {
                SortableStatus other = (SortableStatus) temp;
                if (isUntried) {
                    return other.isUntried ? (!status && other.status) : true;
                }
                return other.isUntried ? false : (!status && other.status);
            }

            @Override
            public boolean isGreaterThan(Sortable<Boolean[]> temp) {
                SortableStatus other = (SortableStatus) temp;
                if (isUntried) {
                    return other.isUntried ? (status && !other.status) : false;
                }
                return other.isUntried ? true : (status && !other.status);
            }

            @Override
            public Boolean[] getValue() {
                return new Boolean[] { status, isUntried };
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof SortableStatus sortableStatus
                        && sortableStatus.status == status
                        && sortableStatus.isUntried == isUntried;
            }

            @Override
            public String toString() {
                return isUntried ? "-" : (status ? "up" : "down");
            }
        }

        private static final class SortableUptime extends SortableLong {
            private SortableUptime(long time) {
                super(time);
            }

            @Override
            public String toString() {
                return com.general.util.Util.getLongAsTime(number);
            }
        }

        private static final class SortableBookmark implements Sortable<Boolean> {
            private final boolean isBookmarked;

            private SortableBookmark(boolean isBookmarked) {
                this.isBookmarked = isBookmarked;
            }

            @Override
            public boolean isLessThan(Sortable<Boolean> other) {
                return other instanceof SortableBookmark sortableBookmark
                        && !isBookmarked
                        && sortableBookmark.isBookmarked;
            }

            @Override
            public boolean isGreaterThan(Sortable<Boolean> other) {
                return other instanceof SortableBookmark sortableBookmark
                        && isBookmarked
                        && !sortableBookmark.isBookmarked;
            }

            @Override
            public Boolean getValue() {
                return isBookmarked;
            }

            @Override
            public String toString() {
                return "";
            }
        }
    }
}
