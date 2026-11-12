package com.myster.search.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import java.util.Optional;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;

import com.general.mclist.GenericMCListItem;
import com.general.mclist.JMCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.general.mclist.MCListFactory;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableLong;
import com.general.mclist.SortableString;
import com.myster.search.ServerSearchResult;
import com.myster.tracker.BookmarkMysterServerList.Bookmark;
import com.myster.tracker.MysterServer;
import com.myster.tracker.MysterIdentity;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;
import com.myster.util.ContextMenu;

/** Server-only rows/actions. The displayed type is retained independently of search input edits. */
final class ServerSearchResults extends JPanel {
    private final JMCList<ServerSearchResult> list;
    private final MysterFrameContext context;
    private final Tracker tracker;
    private MysterType type;

    ServerSearchResults(MysterFrameContext context, Tracker tracker) {
        super(new BorderLayout());
        this.context = context;
        this.tracker = tracker;
        list = MCListFactory.buildMCList(5, true, this);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        String[] names = {"Server", "Description", "Address", "Files", "Ping"};
        int[] widths = {150, 200, 150, 70, 70};
        for (int i = 0; i < names.length; i++) {
            list.setColumnName(i, names[i]);
            list.setColumnWidth(i, widths[i]);
        }
        list.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                             boolean focus, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focus, row, column);
                setToolTipText(value == null ? null : value.toString());
                return this;
            }
        });
        list.addMCListEventListener(new MCListEventAdapter() {
            @Override
            public void doubleClick(MCListEvent event) {
                openSelected();
            }
        });
        ContextMenu.addPopUpMenu(list, () -> {},
                ContextMenu.createMenuItem(list, "Open Server", _ -> openSelected()),
                ContextMenu.createBookmarkServerItem(list, _ -> bookmarkSelected()));
        add(list.getPane(), BorderLayout.CENTER);
    }

    void clear(MysterType type) {
        this.type = type;
        list.clearAll();
    }

    void addResults(List<ServerSearchResult> results) {
        @SuppressWarnings("unchecked")
        MCListItemInterface<ServerSearchResult>[] items = results.stream().map(result ->
            new GenericMCListItem<>(new Sortable<?>[] {
                    new SortableString(result.displayName()), new SortableString(result.description()),
                    new SortableString(result.address().toString()), new SortableLong(result.files()),
                    new CachedPing(result.ping())
            }, result)).toArray(MCListItemInterface[]::new);
        list.addItem(items);
    }

    int sizeOfResults() {
        return list.length();
    }

    private void openSelected() {
        int selected = list.getSelectedIndex();
        if (type == null || selected < 0 || selected >= list.length()) {
            return;
        }
        ServerSearchResult result = list.getMCListItem(selected).getObject();
        context.clientWindowProvider().openWindow(result.address(),
                MysterIdentity.extractPublicKey(result.identity()).map(PublicKeyIdentity::new),
                Optional.of(type), Optional.empty());
    }

    private void bookmarkSelected() {
        for (int index : list.getSelectedIndexes()) {
            if (index >= 0 && index < list.length()) {
                tracker.addBookmark(new Bookmark(list.getMCListItem(index).getObject().identity()));
            }
        }
    }

    /** Cached ping presentation must not trigger the active ping used by file search rows. */
    private static final class CachedPing extends SortableLong {
        private final int ping;

        CachedPing(int ping) {
            super(ping == MysterServer.UNTRIED ? Long.MAX_VALUE - 1
                    : ping == MysterServer.DOWN ? Long.MAX_VALUE : ping);
            this.ping = ping;
        }

        @Override
        public String toString() {
            return switch (ping) {
                case MysterServer.UNTRIED -> "-";
                case MysterServer.DOWN -> "Down";
                default -> ping + "ms";
            };
        }
    }
}
