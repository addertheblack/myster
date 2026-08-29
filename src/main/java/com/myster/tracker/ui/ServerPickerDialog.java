package com.myster.tracker.ui;

import static com.myster.tracker.ui.ServerPickerModel.filter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.general.mclist.GenericMCListItem;
import com.general.mclist.JMCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventListener;
import com.general.mclist.MCListFactory;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.general.thread.AsyncContext;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.GridBagBuilder;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterServer;

/**
 * Document-modal chooser that returns a caller-eligible {@link MysterServer}.
 *
 * <p>The dialog takes a snapshot of known servers, debounces and filters that snapshot off the
 * EDT, and also recognizes strict address input. Direct input follows a cancellable delay, DNS,
 * and server-stats chain with inline status. Every edit and dialog disposal cancels the prior UI
 * operation; an identity token additionally prevents late completion from changing Swing state.
 * Enter confirms the selected row even while the table owns keyboard focus. Up and Down move the
 * selected row while focus remains in the search field. {@link #showAndWait()} returns empty for
 * Cancel, Escape, or window close.
 */
public final class ServerPickerDialog extends JDialog {
    private static final String CONFIRM_SELECTION_ACTION = "confirm-server-selection";
    private static final String PREVIOUS_SELECTION_ACTION = "select-previous-server";
    private static final String NEXT_SELECTION_ACTION = "select-next-server";

    private final Predicate<MysterServer> eligibility;
    private final String ineligibleText;
    private final String confirmationText;
    private final ServerAddressLookup addressLookup;

    private List<ServerPickerModel.Row> allRows;
    private JMCList<MysterServer> serverList;
    private JTextField searchField;
    private JButton confirmButton;
    private JLabel statusLabel;
    private JProgressBar progress;

    private Optional<MysterServer> result = Optional.empty();
    private PromiseFuture<Void> activeQuery;
    private Object activeToken;
    private boolean shown;

    /**
     * Creates a single-use picker with caller-owned presentation and eligibility policy.
     *
     * @param parent owning window, or null for a global menu action
     * @param source known-server snapshot and explicit resolver
     * @param title dialog title
     * @param confirmationText confirmation-button label
     * @param eligibility caller-specific selectable-server rule
     * @param ineligibleText friendly explanation for a directly resolved ineligible server
     */
    public ServerPickerDialog(Window parent,
                              KnownServerSource source,
                              String title,
                              String confirmationText,
                              Predicate<MysterServer> eligibility,
                              String ineligibleText) {
        super(parent, title, ModalityType.DOCUMENT_MODAL);
        Objects.requireNonNull(source, "source");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.ineligibleText = Objects.requireNonNull(ineligibleText, "ineligibleText");
        this.confirmationText = Objects.requireNonNull(confirmationText, "confirmationText");
        this.addressLookup = new ServerAddressLookup(source);
        this.allRows = ServerPickerModel.snapshot(source, eligibility);
    }

    /**
     * Builds and displays this single-use modal chooser.
     *
     * @return selected server, or empty when dismissed
     * @throws IllegalStateException if called more than once
     */
    public Optional<MysterServer> showAndWait() {
        if (shown) {
            throw new IllegalStateException("ServerPickerDialog is single-use");
        }
        shown = true;
        result = Optional.empty();
        buildUi();
        renderRows(allRows);
        pack();
        setMinimumSize(new Dimension(520, 360));
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return result;
    }

    @Override
    public void dispose() {
        cancelActiveQuery();
        super.dispose();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel north = new JPanel(new BorderLayout(0, 6));
        JPanel searchPanel = new JPanel(new GridBagLayout());
        var gbc = new GridBagBuilder().withInsets(new Insets(0, 0, 0, 4));
        searchPanel.add(new JLabel("Search or enter a server address:"),
                gbc.withGridLoc(0, 0).withWeight(0, 0));
        searchField = new JTextField(24);
        searchPanel.add(searchField,
                gbc.withGridLoc(1, 0).withWeight(1.0, 0)
                        .withFill(GridBagConstraints.HORIZONTAL));
        north.add(searchPanel, BorderLayout.NORTH);

        JPanel statusPanel = new JPanel(new BorderLayout(6, 0));
        progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(70, 8));
        progress.setVisible(false);
        statusLabel = new JLabel(" ");
        statusPanel.add(progress, BorderLayout.WEST);
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        north.add(statusPanel, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        serverList = MCListFactory.buildMCList(3, true, this);
        serverList.setColumnName(0, "Server Name");
        serverList.setColumnName(1, "Address");
        serverList.setColumnName(2, "Status");
        serverList.setColumnWidth(0, 220);
        serverList.setColumnWidth(1, 150);
        serverList.setColumnWidth(2, 70);
        serverList.sortBy(-1);
        add(serverList.getPane(), BorderLayout.CENTER);

        confirmButton = new JButton(confirmationText);
        confirmButton.setEnabled(false);
        JButton cancelButton = new JButton("Cancel");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancelButton);
        buttons.add(confirmButton);
        add(buttons, BorderLayout.SOUTH);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { inputChanged(); }
            @Override public void removeUpdate(DocumentEvent event) { inputChanged(); }
            @Override public void changedUpdate(DocumentEvent event) { inputChanged(); }
        });
        serverList.addMCListEventListener(new MCListEventListener() {
            @Override public void selectItem(MCListEvent event) { updateConfirmationState(); }
            @Override public void unselectItem(MCListEvent event) { updateConfirmationState(); }
            @Override public void doubleClick(MCListEvent event) { confirmSelection(); }
        });
        confirmButton.addActionListener(_ -> confirmSelection());
        cancelButton.addActionListener(_ -> cancelAndDispose());
        getRootPane().setDefaultButton(confirmButton);
        bindEnterAction(serverList, confirmButton::doClick);
        bindVerticalSelectionActions(searchField,
                () -> moveSelection(-1),
                () -> moveSelection(1));
        bindEscape();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    static void bindEnterAction(JComponent component, Runnable action) {
        bindFocusedKeyAction(component, KeyEvent.VK_ENTER, CONFIRM_SELECTION_ACTION, action);
    }

    static void bindVerticalSelectionActions(JComponent component,
                                             Runnable selectPrevious,
                                             Runnable selectNext) {
        bindFocusedKeyAction(component, KeyEvent.VK_UP, PREVIOUS_SELECTION_ACTION, selectPrevious);
        bindFocusedKeyAction(component, KeyEvent.VK_DOWN, NEXT_SELECTION_ACTION, selectNext);
    }

    private static void bindFocusedKeyAction(JComponent component,
                                             int keyCode,
                                             String actionName,
                                             Runnable action) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(action, "action");
        component.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(keyCode, 0), actionName);
        component.getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void moveSelection(int direction) {
        int selected = serverList.getSelectedIndex();
        int target = nextSelectionIndex(selected, serverList.length(), direction);
        if (target < 0 || target == selected
                || !(serverList.getMCListItem(target) instanceof ServerPickerItem)) {
            return;
        }
        serverList.selectAndScrollToItem(serverList.getMCListItem(target));
        updateConfirmationState();
    }

    static int nextSelectionIndex(int selected, int rowCount, int direction) {
        if (rowCount <= 0) {
            return -1;
        }
        if (selected < 0 || selected >= rowCount) {
            return 0;
        }
        int step = Integer.signum(direction);
        return Math.max(0, Math.min(rowCount - 1, selected + step));
    }

    private void inputChanged() {
        cancelActiveQuery();
        String term = searchField.getText().trim();
        Optional<ServerAddressCandidate> candidate = ServerAddressCandidate.parse(term);
        Object token = new Object();
        activeToken = token;
        showStatus(term.isEmpty() ? " " : candidate.isPresent()
                ? "Waiting to check " + candidate.get().addressText() + "…"
                : "Waiting to search…", !term.isEmpty());

        activeQuery = PromiseFuture.newPromiseFuture(context -> {
            startFiltering(context, token, term, candidate.isPresent());
            candidate.ifPresent(value -> startAddressLookup(context, token, value, term));
        });
    }

    private void startFiltering(AsyncContext<Void> owner,
                                Object token,
                                String term,
                                boolean addressCandidate) {
        List<ServerPickerModel.Row> snapshot = List.copyOf(allRows);
        PromiseFuture<List<ServerPickerModel.Row>> filtering =
                PromiseFutures.delay(ServerAddressLookup.DEFAULT_DELAY)
                        .mapAsyncInline(_ -> {
                            if (!addressCandidate) {
                                Invoker.EDT.invoke(() -> {
                                    if (isCurrent(token)) {
                                        showStatus("Searching…", true);
                                    }
                                });
                            }
                            return PromiseFutures.execute(() -> filter(snapshot, term));
                        });
        owner.trackForCancellation(filtering);
        filtering.withInvoker(Invoker.EDT)
                .addResultListener(rows -> {
                    if (isCurrent(token)) {
                        renderRows(rows);
                        if (!addressCandidate) {
                            showStatus(" ", false);
                        }
                    }
                })
                .addExceptionListener(_ -> {
                    if (isCurrent(token) && !addressCandidate) {
                        showStatus("Could not search known servers.", false);
                    }
                });
    }

    private void startAddressLookup(AsyncContext<Void> owner,
                                    Object token,
                                    ServerAddressCandidate candidate,
                                    String term) {
        PromiseFuture<MysterServer> lookup = addressLookup.start(candidate,
                update -> addressStageChanged(token, candidate, update));
        owner.trackForCancellation(lookup);
        lookup.withInvoker(Invoker.EDT)
                .addResultListener(server -> resolvedServer(owner, token, term, server))
                .addExceptionListener(exception -> {
                    if (!isCurrent(token)) {
                        return;
                    }
                    String message = exception instanceof UnknownHostException
                            ? "Could not resolve " + candidate.addressText() + "."
                            : "No Myster response from " + candidate.addressText() + ".";
                    showStatus(message, false);
                });
    }

    private void addressStageChanged(Object token,
                                     ServerAddressCandidate candidate,
                                     ServerAddressLookup.StageUpdate update) {
        if (!isCurrent(token)) {
            return;
        }
        String message = switch (update.stage()) {
            case WAITING -> "Waiting to check " + candidate.addressText() + "…";
            case RESOLVING -> "Resolving " + candidate.host() + "…";
            case CONTACTING -> "Contacting "
                    + update.resolvedAddress().map(MysterAddress::toString)
                            .orElse(candidate.addressText()) + "…";
        };
        showStatus(message, true);
    }

    private void resolvedServer(AsyncContext<Void> owner,
                                Object token,
                                String term,
                                MysterServer server) {
        if (!isCurrent(token)) {
            return;
        }
        if (!eligibility.test(server)) {
            showStatus(ineligibleText, false);
            return;
        }

        allRows = ServerPickerModel.upsert(allRows, server);
        List<ServerPickerModel.Row> snapshot = allRows;
        PromiseFuture<List<ServerPickerModel.Row>> filtering =
                PromiseFutures.execute(() -> ServerPickerModel.filterIncluding(snapshot, term,
                        server));
        owner.trackForCancellation(filtering);
        filtering.withInvoker(Invoker.EDT).addResultListener(rows -> {
            if (!isCurrent(token)) {
                return;
            }
            renderRows(rows);
            selectServer(server);
            showStatus("Responding: " + ServerPickerModel.row(server).displayName(), false);
        });
    }

    private void renderRows(List<ServerPickerModel.Row> rows) {
        serverList.clearAll();
        confirmButton.setEnabled(false);
        if (rows.isEmpty()) {
            serverList.addItem(new PlaceholderItem());
            return;
        }
        GenericMCListItem<MysterServer>[] items = rows.stream()
                .map(ServerPickerItem::new)
                .toArray(GenericMCListItem[]::new);
        serverList.addItem(items);
        serverList.select(0);
        updateConfirmationState();
    }

    private void selectServer(MysterServer server) {
        for (int i = 0; i < serverList.length(); i++) {
            if (serverList.getMCListItem(i) instanceof ServerPickerItem item
                    && item.row.server().getIdentity().equals(server.getIdentity())) {
                serverList.select(i);
                updateConfirmationState();
                return;
            }
        }
    }

    private void updateConfirmationState() {
        int selected = serverList.getSelectedIndex();
        confirmButton.setEnabled(selected >= 0
                && serverList.getMCListItem(selected) instanceof ServerPickerItem);
    }

    private void confirmSelection() {
        int selected = serverList.getSelectedIndex();
        if (selected < 0
                || !(serverList.getMCListItem(selected) instanceof ServerPickerItem item)) {
            return;
        }
        result = Optional.of(item.row.server());
        dispose();
    }

    private void cancelAndDispose() {
        result = Optional.empty();
        dispose();
    }

    private void cancelActiveQuery() {
        activeToken = null;
        if (activeQuery != null) {
            activeQuery.cancel();
            activeQuery = null;
        }
    }

    private boolean isCurrent(Object token) {
        return token == activeToken && isDisplayable();
    }

    private void showStatus(String text, boolean busy) {
        statusLabel.setText(text);
        progress.setVisible(busy);
    }

    private void bindEscape() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel-picker");
        actionMap.put("cancel-picker", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                cancelAndDispose();
            }
        });
    }

    private static final class ServerPickerItem extends GenericMCListItem<MysterServer> {
        private final ServerPickerModel.Row row;

        private ServerPickerItem(ServerPickerModel.Row row) {
            super(new Sortable[0], row.server());
            this.row = row;
        }

        @Override
        public Sortable<?> getValueOfColumn(int column) {
            return switch (column) {
                case 0 -> new SortableString(row.displayName());
                case 1 -> new SortableString(row.address());
                case 2 -> new SortableString(row.status());
                default -> new SortableString("");
            };
        }
    }

    private static final class PlaceholderItem extends GenericMCListItem<MysterServer> {
        private PlaceholderItem() {
            super(new Sortable[0], null);
        }

        @Override
        public Sortable<?> getValueOfColumn(int column) {
            return new SortableString(column == 0 ? "No servers found" : "");
        }
    }
}
