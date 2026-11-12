/*
 *
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 *
 * This code is under GPL
 *
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.search.ui;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.Dimension;
import java.awt.CardLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.general.mclist.JMCList;
import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.general.mclist.MCListFactory;
import com.general.mclist.MCListItemInterface;
import com.general.thread.Cancellable;
import com.general.thread.Invoker;
import com.general.util.AnswerDialog;
import com.general.util.GridBagBuilder;
import com.general.util.IconLoader;
import com.general.util.MessageField;
import com.general.util.Util;
import com.myster.link.ui.FileLinkCopyActionUtils;
import com.myster.search.MysterFileStub;
import com.myster.tracker.MysterIdentity;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.net.client.MysterProtocol;
import com.myster.net.stream.client.msdownload.DownloadStartException;
import com.myster.search.HashCrawlerManager;
import com.myster.search.SearchEngine;
import com.myster.search.ServerSearch;
import com.myster.search.SearchResult;
import com.myster.search.SearchResultListener;
import com.myster.tracker.BookmarkMysterServerList;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.type.MetadataTypeId;
import com.myster.type.TypeDescriptionList;
import com.myster.ui.DownloadStartErrorDialog;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.DownloadDirectoryChooser;
import com.myster.util.ContextMenu;
import com.myster.util.Sayable;
import com.myster.util.TypeChoice;

/**
 * A panel containing a complete search interface: search field, type selector,
 * search button, results list, and status message. Used as a tab within
 * {@link SearchWindow}.
 * <p>
 * Each tab owns one cancellable file or server search. File callbacks use
 * {@link SearchResultListener}; server rows have their own typed table and actions.
 * Selected mode/type edits prepare the next search and never reinterpret displayed results.
 * Displayed rows retain their search's type, columns and thumbnail profile. Search implementations
 * deliver callbacks on the EDT and suppress them after cancellation, including queued deliveries.
 * Stop preserves displayed demand; replacement and disposal cancel it. Rendering and acquisition
 * are composed by {@link SearchThumbnailSupport}, outside the widget.
 */
public class SearchTab extends JPanel implements SearchResultListener, Sayable {
    private static final Logger log = Logger.getLogger(SearchTab.class.getName());

    private static final int XDEFAULT = 640;
    private static final int YDEFAULT = 400;

    private final JButton searchButton;
    private final JMCList<SearchResult> fileList;
    private final JTextField textEntry;
    private final TypeChoice choice;
    private final MessageField msg;
    private final TypeDescriptionList tdList;
    private final MysterFrameContext context;
    private final Consumer<SearchTab> onStateChange;

    private final MysterProtocol protocol;
    private final HashCrawlerManager hashManager;
    private final Tracker tracker;
    private final SearchThumbnailSupport thumbnails;

    private final JComboBox<SearchMode> modeChoice = new JComboBox<>(SearchMode.values());
    private final JPanel resultPanes = new JPanel(new CardLayout());
    private final ServerSearchResults serverResults;
    private SearchMode resultsMode = SearchMode.FILES;
    private boolean thumbnailActive;
    private Cancellable activeSearch;
    private MysterType resultsType;
    private FileTypeColumnHandler resultsColumnHandler;
    private boolean disposed;
    private FileTypeColumnHandler metaDateHandler;
    private int resultCount = 0;
    private String lastSearchString = "";
    private String lastSearchTypeName = "New Search";

    /**
     * Creates a new search tab.
     *
     * @param context the frame context for accessing shared resources
     * @param protocol the protocol for network operations
     * @param hashManager the hash crawler manager
     * @param tracker the tracker for server management
     * @param onStateChange callback invoked when tab state changes (e.g., search starts)
     */
    public SearchTab(MysterFrameContext context,
                     MysterProtocol protocol,
                     HashCrawlerManager hashManager,
                     Tracker tracker,
                     Consumer<SearchTab> onStateChange) {
        this.context = context;
        this.protocol = protocol;
        this.hashManager = hashManager;
        this.tracker = tracker;
        this.onStateChange = onStateChange;
        this.tdList = context.tdList();

        // Do interface setup:
        setLayout(new GridBagLayout());
        var gbc = new GridBagBuilder()
            .withFill(GridBagConstraints.BOTH)
            .withInsets(new Insets(5, 5, 5, 5))
            .withIpad(1, 1);

        searchButton = new JButton("Search") {
            public Dimension getPreferredSize() {
                return new Dimension(Math.max(75, super.getPreferredSize().width), super
                        .getPreferredSize().height);
            }

            public Dimension getMinimumSize() {
                return getPreferredSize();
            }
        };
        searchButton.setIcon(IconLoader.loadSvg(getClass(), "search"));

        textEntry = new JTextField("", 1);
        textEntry.setEditable(true);

        choice = new TypeChoice(context.tdList(), false);

        fileList = MCListFactory.buildMCList(1, true, this);
        fileList.getPane().setSize(XDEFAULT, YDEFAULT);
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        msg = new MessageField("Idle...");
        modeChoice.setToolTipText("Search for files or servers within the selected type");
        modeChoice.addActionListener(_ -> onStateChange.accept(this));
        serverResults = new ServerSearchResults(context, tracker);
        resultPanes.add(fileList.getPane(), SearchMode.FILES.name());
        resultPanes.add(serverResults, SearchMode.SERVERS.name());

        add(textEntry, gbc.withGridLoc(0, 0).withSize(1, 1).withWeight(1, 0).withFill(GridBagConstraints.HORIZONTAL));
        add(modeChoice, gbc.withGridLoc(1, 0).withSize(1, 1).withWeight(0, 0).withFill(GridBagConstraints.HORIZONTAL));
        add(choice, gbc.withGridLoc(2, 0).withSize(1, 1).withWeight(0, 0).withFill(GridBagConstraints.HORIZONTAL));
        add(searchButton, gbc.withGridLoc(3, 0).withSize(1, 1).withWeight(0, 0).withFill(GridBagConstraints.HORIZONTAL));
        add(resultPanes, gbc.withGridLoc(0, 1).withSize(4, 1).withWeight(1, 1).withFill(GridBagConstraints.BOTH));
        add(msg, gbc.withGridLoc(0, 2).withSize(4, 1).withWeight(1, 0).withFill(GridBagConstraints.HORIZONTAL));

        searchButton.addActionListener(new SearchButtonHandler());

        textEntry.addActionListener(e -> startSearch());

        fileList.addMCListEventListener(new MCListEventAdapter() {
            public synchronized void doubleClick(MCListEvent a) {
                MCList<SearchResult> list = (MCList<SearchResult>) a.getParent();
                downloadRows(new int[] { list.getSelectedIndex() }, false);
            }
        });

        fileList.setColumnName(0, "Search Results appear here");
        fileList.setColumnWidth(0, 400);

        addPopUpMenus();

        textEntry.setSelectionStart(0);
        textEntry.setSelectionEnd(textEntry.getText().length());
        thumbnails = new SearchThumbnailSupport(fileList, protocol.getStream());
    }

    private void addPopUpMenus() {
        JMenuItem openContainingFolder = ContextMenu.createMenuItem(fileList, "Reveal on Server", _ -> {
            var moop = fileList.getSelectedIndex();

            var item = fileList.getMCListItem(moop);
            var downloadItem = item.getObject();

            MysterServer server = downloadItem.getServer();
            context.clientWindowProvider()
                   .openWindow(downloadItem.getHostAddress(),
                               Optional.ofNullable(server)
                                       .map(MysterServer::getIdentity)
                                       .flatMap(MysterIdentity::extractPublicKey)
                                       .map(PublicKeyIdentity::new),
                               Optional.of(getMysterType()),
                               Optional.of(downloadItem.getName()));
        });

        JMenuItem downloadMenuItem = ContextMenu.createDownloadItem(fileList, _ -> {
            downloadRows(fileList.getSelectedRows(), false);
        });
        JMenuItem downloadToMenuItem = ContextMenu.createDownloadToItem(fileList, _ -> {
            downloadRows(fileList.getSelectedRows(), true);
        });
        JMenuItem bookmarkMenuItem = ContextMenu.createBookmarkServerItem(fileList, _ -> {
            int[] indexes = fileList.getSelectedRows();

            for (int i : indexes) {
                MysterServer server = tracker.getQuickServerStats(fileList.getMCListItem(i)
                        .getObject()
                        .getHostAddress());

                tracker.addBookmark(new BookmarkMysterServerList.Bookmark(server.getIdentity()));
            }
        });

        var popup = ContextMenu.addPopUpMenu(fileList, ()->{}, openContainingFolder, null, downloadMenuItem, downloadToMenuItem, null, bookmarkMenuItem);
        popup.add(FileLinkCopyActionUtils.remoteItem(
                () -> SwingUtilities.getWindowAncestor(this), context.linkService(), () -> {
                    if (fileList.getSelectedIndexes().length != 1) return Optional.empty();
                    SearchResult result = fileList.getMCListItem(fileList.getSelectedIndex()).getObject();
                    return Optional.of(new FileLinkCopyActionUtils.RemoteFile(new MysterFileStub(
                            result.getHostAddress(), getMysterType(), result.getName()),
                                                                              Optional.ofNullable(result.getServer()).map(MysterServer::getIdentity)
                                    .flatMap(MysterIdentity::extractPublicKey)));
                }));
    }

    private void downloadRows(int[] indexes, boolean alwaysAskForDirectory) {
        if (indexes.length == 0) {
            return;
        }

        Optional<Path> baseDirectory = resolveDownloadDirectory(alwaysAskForDirectory);
        if (baseDirectory.isEmpty()) {
            return;
        }

        for (int index : indexes) {
            try {
                fileList.getMCListItem(index)
                        .getObject()
                        .downloadTo(baseDirectory.get(),
                                    DownloadStartErrorDialog.handler(getParentFrame()));
            } catch (DownloadStartException exception) {
                DownloadStartErrorDialog.showOnEdt(getParentFrame(), exception);
            }
        }
    }

    private Optional<Path> resolveDownloadDirectory(boolean alwaysAskForDirectory) {
        return DownloadDirectoryChooser
                .chooseWritableDownloadDirectory(getParentFrame(),
                                                 "Select a folder to save the file in",
                                                 alwaysAskForDirectory,
                                                 context.fileManager(),
                                                 getMysterType());
    }

    private Frame getParentFrame() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof Frame frame) {
            return frame;
        }

        return AnswerDialog.getCenteredFrame();
    }


    @Override
    public void searchStart() {
        searchButton.setText("Stop");
        msg.say("Searching for " + resultsMode.toString().toLowerCase(java.util.Locale.ROOT) + "...");
        if (resultsMode == SearchMode.FILES) {
            fileList.clearAll();
            recolumnize();
        } else {
            serverResults.clear(resultsType);
        }
        resultCount = 0;
        onStateChange.accept(this);
    }

    @Override
    public void searchOver() {
        activeSearch = null;
        showFinished("Search done.");
    }

    /**
     * Starts a search using the current query, type and mode, replacing the displayed generation.
     */
    public void startSearch() {
        if (disposed || choice.getSelectedTypeDescription().isEmpty()) {
            return;
        }
        stopSearch();

        var description = choice.getSelectedTypeDescription().orElseThrow();
        resultsType = description.getType();
        resultsMode = getSearchMode();
        fileList.clearAll();
        serverResults.clear(resultsType);
        resultCount = 0;
        thumbnails.setActive(false);
        thumbnails.beginGeneration(resultsType, resultsMode == SearchMode.FILES
                ? description.getMetadataTypeId() : MetadataTypeId.GENERIC);
        thumbnails.setActive(thumbnailActive && resultsMode == SearchMode.FILES);
        ((CardLayout) resultPanes.getLayout()).show(resultPanes, resultsMode.name());
        if (resultsMode == SearchMode.FILES) {
            resultsColumnHandler = ClientInfoFactoryUtils.getHandler(tdList, resultsType);
        }

        // Capture the search parameters for tab title
        lastSearchString = getSearchString();
        try {
            lastSearchTypeName = choice.getSelectedDescription();
        } catch (Exception e) {
            lastSearchTypeName = "Search";
        }

        if (resultsMode == SearchMode.FILES) {
            SearchEngine engine = new SearchEngine(protocol, hashManager, context, tracker,
                    this, this,
                    resultsType, lastSearchString);
            activeSearch = engine::flagToEnd;
            engine.run();
        } else {
            searchStart();
            var future = new ServerSearch(protocol, tracker, context.knownServerSource())
                    .search(resultsType, lastSearchString,
                            batch -> {
                                serverResults.addResults(batch);
                                resultCount = serverResults.sizeOfResults();
                                onStateChange.accept(this);
                            }, this);
            activeSearch = future;
            future.withInvoker(Invoker.EDT)
                    .addResultListener(_ -> searchOver())
                    .addExceptionListener(error -> {
                        log.log(java.util.logging.Level.WARNING, "Server search failed", error);
                        activeSearch = null;
                        showFinished("Search failed.");
                    });
        }
        onStateChange.accept(this);
    }

    /**
     * Stops the current search if one is running.
     */
    public void stopSearch() {
        if (activeSearch == null) {
            return;
        }
        Cancellable previous = activeSearch;
        activeSearch = null;
        previous.cancel();
        showFinished("Search stopped.");
    }

    private void showFinished(String status) {
        String noun = resultsMode == SearchMode.SERVERS ? "server" : "file";
        msg.say(status + " " + resultCount + " " + noun + (resultCount == 1 ? "" : "s") + " found...");
        searchButton.setText("Search");
    }

    private void recolumnize() {
        metaDateHandler = new SearchColumnDecorator(resultsColumnHandler);
        int max = metaDateHandler.getColumnCount();
        fileList.setNumberOfColumns(max);

        for (int i = 0; i < max; i++) {
            fileList.setColumnName(i, metaDateHandler.getHeader(i));
            fileList.setColumnWidth(i, metaDateHandler.getHeaderSize(i));
        }
        thumbnails.columnsRebuilt();
    }

    @Override
    public boolean addSearchResults(SearchResult[] resultArray) {
        @SuppressWarnings("unchecked")
        MCListItemInterface<SearchResult>[] m = java.util.Arrays.stream(resultArray)
            .map(metaDateHandler::getSearchItem)
            .toArray(MCListItemInterface[]::new);
        fileList.addItem(m);
        resultCount = fileList.length();
        onStateChange.accept(this);
        return true;
    }

    @Override
    public void searchStats(SearchResult s) {
        Util.invokeNowOrLater(() -> {
            fileList.getPane().repaint();
            thumbnails.resultsChanged();
        });
    }

    /** Window activity affects thumbnail demand, independently of search discovery. */
    void setThumbnailActive(boolean active) {
        thumbnailActive = active;
        thumbnails.setActive(active && resultsMode == SearchMode.FILES);
    }

    /** Current input mode; changes apply only when the next search starts. */
    public SearchMode getSearchMode() {
        return (SearchMode) modeChoice.getSelectedItem();
    }

    public void setSearchMode(SearchMode mode) {
        modeChoice.setSelectedItem(java.util.Objects.requireNonNull(mode));
    }

    /**
     * Returns the search string entered by the user.
     *
     * @return the search string entered by the user
     */
    public String getSearchString() {
        return textEntry.getText();
    }

    /**
     * Sets the search string in the text field.
     *
     * @param searchString the search string to set
     */
    public void setSearchString(String searchString) {
        if (searchString != null) {
            textEntry.setText(searchString);
            textEntry.setSelectionStart(0);
            textEntry.setSelectionEnd(searchString.length());
        }
    }

    /**
     * Returns the currently selected file type.
     *
     * @return the selected MysterType
     */
    public MysterType getMysterType() {
        return choice.getType().get();
    }

    /**
     * Sets the selected file type.
     *
     * @param type the type to select
     */
    public void setMysterType(MysterType type) {
        if (type != null) {
            choice.setType(type);
        }
    }

    /**
     * Restores the last search state (for preferences restoration).
     * This updates the tab title and empty result pane without performing a search.
     *
     * @param searchString the search string that was used
     */
    public void restoreSearchState(String searchString) {
        lastSearchString = searchString != null ? searchString : "";
        resultsMode = getSearchMode();
        ((CardLayout) resultPanes.getLayout()).show(resultPanes, resultsMode.name());
        thumbnails.setActive(thumbnailActive && resultsMode == SearchMode.FILES);
        try {
            lastSearchTypeName = choice.getSelectedDescription();
        } catch (Exception e) {
            lastSearchTypeName = "Search";
        }
    }

    /**
     * Returns a title suitable for display in a tab header.
     *
     * @return the search string with result count if results exist, type name if search is empty, or "New Search"
     */
    public String getTabTitle() {
        String displayText;

        if (lastSearchString == null || lastSearchString.trim().isEmpty()) {
            // No search string - use the type name from when search was performed
            displayText = lastSearchTypeName;
        } else {
            // Truncate long search strings for tab display
            displayText = lastSearchString;
            if (lastSearchString.length() > 20) {
                displayText = lastSearchString.substring(0, 17) + "...";
            }
        }

        if (resultsMode == SearchMode.SERVERS) {
            displayText = "Servers: " + displayText;
        }

        // Add result count if we have results
        if (resultCount > 0) {
            return displayText + " (" + resultCount + ")";
        }

        return displayText;
    }

    /**
     * Terminal, idempotent cleanup; cancelling the search suppresses its queued callbacks.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        stopSearch();
        thumbnails.close();
        choice.dispose();
    }

    /**
     * Requests focus on the search text field.
     */
    public void focusSearchField() {
        textEntry.requestFocusInWindow();
    }

    /**
     * Returns the search button for setting as default button.
     *
     * @return the search button
     */
    public JButton getSearchButton() {
        return searchButton;
    }

    @Override
    public void say(String s) {
        log.fine(s);
        msg.say(s);
    }

    /**
     * Handles search button clicks, toggling between search and stop.
     */
    private class SearchButtonHandler implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent event) {
            if (!searchButton.isEnabled()) {
                return;
            }

            if (searchButton.getText().equals("Stop")) {
                stopSearch();
            } else {
                startSearch();
            }
        }
    }
}
