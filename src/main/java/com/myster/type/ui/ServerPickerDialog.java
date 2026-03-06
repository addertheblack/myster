package com.myster.type.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import com.general.mclist.GenericMCListItem;
import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventListener;
import com.general.mclist.MCListFactory;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.general.util.GridBagBuilder;
import com.myster.identity.Cid128;
import com.myster.tracker.MysterServer;

import static com.general.util.Util.filter;

/**
 * Modal dialog for picking a Myster server from the known server pool.
 *
 * <p>Used by {@link TypeEditorPanel}'s Members tab to resolve a server selection to a
 * {@link Cid128} for use in an {@link com.myster.access.AddMemberOp}.
 *
 * <p>Only servers with a {@link PublicKeyIdentity} are listed — a public key is required
 * to derive the {@link Cid128} via {@link Util#generateCid}.
 *
 * <p>Call {@link #showAndWait()} to display the dialog modally. Returns a {@link PickedServer}
 * on confirmation, or {@code null} if the user cancelled.
 */
public class ServerPickerDialog extends JDialog {
    /**
     * The result of a successful server selection. Contains the {@link Cid128} derived from
     * the server's RSA public key and a human-readable display name.
     */
    public record PickedServer(Cid128 cid, String displayName) {}

    private final TypeEditorServerSource pool;

    private MCList<MysterServer> serverList;
    private JTextField filterField;
    private JButton addButton;

    /** All servers with a PublicKeyIdentity, built once on open. */
    private final List<ServerPickerItem> allItems = new ArrayList<>();

    private PickedServer result = null;

    /**
     * Creates the dialog. The UI is not built until {@link #showAndWait()} is called.
     *
     * <p>Uses {@link ModalityType#DOCUMENT_MODAL} so only the owning preferences window is
     * blocked — the rest of the application (client windows, search, etc.) remains interactive.
     *
     * @param parent the owning window (typically the preferences dialog)
     * @param pool   the server pool to source servers from
     */
    public ServerPickerDialog(Window parent, TypeEditorServerSource pool) {
        super(parent, "Add Member — Pick a Server", ModalityType.DOCUMENT_MODAL);
        this.pool = pool;
    }

    /**
     * Builds the UI, populates the list from the pool, and blocks until the user confirms
     * or cancels.
     *
     * @return the chosen server, or {@code null} if the dialog was cancelled
     */
    public PickedServer showAndWait() {
        buildUi();
        populateAll();
        applyFilter();
        pack();
        setMinimumSize(new java.awt.Dimension(480, 320));
        setLocationRelativeTo(getOwner());
        setVisible(true); // blocks until dispose()
        return result;
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Filter row
        JPanel filterPanel = new JPanel(new GridBagLayout());
        var gbc = new GridBagBuilder().withInsets(new Insets(0, 0, 0, 4));
        filterPanel.add(new JLabel("Filter by name or address:"),
                gbc.withGridLoc(0, 0).withWeight(0, 0));
        filterField = new JTextField(24);
        filterPanel.add(filterField,
                gbc.withGridLoc(1, 0).withWeight(1.0, 0).withFill(GridBagConstraints.HORIZONTAL));
        add(filterPanel, BorderLayout.NORTH);

        // Server list
        serverList = MCListFactory.buildMCList(3, true, this);
        serverList.setColumnName(0, "Server Name");
        serverList.setColumnName(1, "Address");
        serverList.setColumnName(2, "Status");
        serverList.setColumnWidth(0, 200);
        serverList.setColumnWidth(1, 130);
        serverList.setColumnWidth(2, 70);
        serverList.sortBy(-1);
        add(serverList.getPane(), BorderLayout.CENTER);

        // Buttons
        addButton = new JButton("Add Selected");
        addButton.setEnabled(false);
        JButton cancelButton = new JButton("Cancel");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.add(cancelButton);
        buttonPanel.add(addButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Wire events
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { applyFilter(); }
            public void removeUpdate(DocumentEvent e)  { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        serverList.addMCListEventListener(new MCListEventListener() {
            public void selectItem(MCListEvent e)   { addButton.setEnabled(serverList.getSelectedIndex() >= 0); }
            public void unselectItem(MCListEvent e) { addButton.setEnabled(false); }
            public void doubleClick(MCListEvent e)  { confirmSelection(); }
        });

        addButton.addActionListener(e -> confirmSelection());
        cancelButton.addActionListener(e -> dispose());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void populateAll() {
        allItems.clear();
        pool.forEachServer((server, cid) -> allItems.add(new ServerPickerItem(server, cid)));
    }

    private void applyFilter() {
        String term = filterField.getText().trim().toLowerCase();
        serverList.clearAll();

        List<ServerPickerItem> filtered = filter(allItems,
                item -> term.isEmpty()
                        || item.server.getServerName().toLowerCase().contains(term)
                        || item.addressString().toLowerCase().contains(term));

        if (filtered.isEmpty()) {
            // Show a placeholder row
            serverList.addItem(new PlaceholderItem());
            addButton.setEnabled(false);
        } else {
            serverList.addItem(filtered.toArray(new GenericMCListItem[0]));
        }
    }

    private void confirmSelection() {
        int idx = serverList.getSelectedIndex();
        if (idx < 0) return;
        var raw = serverList.getMCListItem(idx);
        if (!(raw instanceof ServerPickerItem picked)) return;
        result = new PickedServer(picked.cid, picked.server.getServerName());
        dispose();
    }

    // ── MCList items ──────────────────────────────────────────────────────────

    private static class ServerPickerItem extends GenericMCListItem<MysterServer> {
        final MysterServer server;
        final Cid128 cid;

        ServerPickerItem(MysterServer server, Cid128 cid) {
            super(new Sortable[0], server);
            this.server = server;
            this.cid = cid;
        }

        String addressString() {
            return server.getBestAddress()
                    .map(Object::toString)
                    .orElse("—");
        }

        String statusString() {
            if (server.isUntried()) return "Untried";
            int ping = server.getPingTime();
            return ping == MysterServer.DOWN ? "Down" : "Up";
        }

        @Override
        public Sortable<?> getValueOfColumn(int col) {
            return switch (col) {
                case 0 -> new SortableString(server.getServerName());
                case 1 -> new SortableString(addressString());
                case 2 -> new SortableString(statusString());
                default -> new SortableString("");
            };
        }
    }

    /** Placeholder row shown when the pool is empty or no servers match the filter. */
    private static class PlaceholderItem extends GenericMCListItem<MysterServer> {
        PlaceholderItem() { super(new Sortable[0], null); }

        @Override
        public Sortable<?> getValueOfColumn(int col) {
            return col == 0 ? new SortableString("No servers found") : new SortableString("");
        }
    }
}

