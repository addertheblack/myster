/*
 *
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 *
 * This code is under GPL
 *
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.search.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import com.general.mclist.JMCList;
import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.general.mclist.MCListFactory;
import com.general.mclist.MCListItemInterface;
import com.general.util.IconLoader;
import com.general.util.MessageField;
import com.general.util.Util;
import com.myster.client.ui.ClientWindow;
import com.myster.net.client.MysterProtocol;
import com.myster.search.HashCrawlerManager;
import com.myster.search.SearchEngine;
import com.myster.search.SearchResult;
import com.myster.search.SearchResultListener;
import com.myster.tracker.BookmarkMysterServerList;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.type.TypeDescriptionList;
import com.myster.ui.MysterFrameContext;
import com.myster.util.ContextMenu;
import com.myster.util.Sayable;
import com.myster.util.TypeChoice;

/**
 * A panel containing a complete search interface: search field, type selector,
 * search button, results list, and status message. Used as a tab within
 * {@link SearchWindow}.
 * <p>
 * Each SearchTab manages its own {@link SearchEngine} instance and implements
 * {@link SearchResultListener} to receive search results independently.
 */
public class SearchTab extends JPanel implements SearchResultListener, Sayable {
    private static final Logger log = Logger.getLogger(SearchTab.class.getName());

    private static final int XDEFAULT = 640;
    private static final int YDEFAULT = 400;

    private final GridBagLayout gblayout;
    private final GridBagConstraints gbconstrains;

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

    private SearchEngine searchEngine;
    private ClientHandleObject metaDateHandler;
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

        setBackground(new Color(240, 240, 240));

        // Do interface setup:
        gblayout = new GridBagLayout();
        setLayout(gblayout);
        gbconstrains = new GridBagConstraints();
        gbconstrains.fill = GridBagConstraints.BOTH;
        gbconstrains.insets = new Insets(5, 5, 5, 5);
        gbconstrains.ipadx = 1;
        gbconstrains.ipady = 1;

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

        addComponent(textEntry, 0, 0, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL);
        addComponent(choice, 0, 1, 1, 1, 0, 0, GridBagConstraints.HORIZONTAL);
        addComponent(searchButton, 0, 2, 1, 1, 0, 0, GridBagConstraints.HORIZONTAL);
        addComponent(fileList.getPane(), 1, 0, 4, 1, 1, 1, GridBagConstraints.BOTH);
        addComponent(msg, 2, 0, 4, 1, 1, 0, GridBagConstraints.HORIZONTAL);

        searchButton.addActionListener(new SearchButtonHandler());

        textEntry.addActionListener(e -> startSearch());

        fileList.addMCListEventListener(new MCListEventAdapter() {
            public synchronized void doubleClick(MCListEvent a) {
                MCList<SearchResult> list = (MCList<SearchResult>) a.getParent();
                downloadFile(list.getItem(list.getSelectedIndex()));
            }
        });

        fileList.setColumnName(0, "Search Results appear here");
        fileList.setColumnWidth(0, 400);

        addPopUpMenus();

        textEntry.setSelectionStart(0);
        textEntry.setSelectionEnd(textEntry.getText().length());
    }

    private void addPopUpMenus() {
        JMenuItem openContainingFolder = ContextMenu.createMenuItem(fileList, "Reveal on Server", _ -> {
            var moop = fileList.getSelectedIndex();

            var item = fileList.getMCListItem(moop);
            var downloadItem = item.getObject();

            ClientWindow w = context.clientWindowProvider()
                    .getOrCreateWindow(new ClientWindow.ClientWindowData(Optional
                                             .of(downloadItem.getHostAddress().toString()),
                                                                       Optional.of(getMysterType()),
                                                                       Optional.of(item.getObject().getName())));
            w.show();
        });

        JMenuItem downloadMenuItem = ContextMenu.createDownloadItem(fileList, _ -> {
            int[] indexes = fileList.getSelectedRows();

            for (int i : indexes) {
                fileList.getMCListItem(i).getObject().download();
            }
        });
        JMenuItem downloadToMenuItem = ContextMenu.createDownloadToItem(fileList, _ -> {
            int[] indexes = fileList.getSelectedRows();

            for (int i : indexes) {
                fileList.getMCListItem(i).getObject().downloadTo();
            }
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

        ContextMenu.addPopUpMenu(fileList, ()->{}, openContainingFolder, null, downloadMenuItem, downloadToMenuItem, null, bookmarkMenuItem);
    }

    private void addComponent(Component component, int row, int column, int width, int height,
            int weightx, int weighty, int fill) {
        gbconstrains.gridx = column;
        gbconstrains.gridy = row;

        gbconstrains.gridwidth = width;
        gbconstrains.gridheight = height;

        gbconstrains.weightx = weightx;
        gbconstrains.weighty = weighty;

        gbconstrains.fill = fill;

        gblayout.setConstraints(component, gbconstrains);

        add(component);
    }

    @Override
    public void searchStart() {
        searchButton.setText("Stop");
        msg.say("Clearing File List...");
        fileList.clearAll();
        resultCount = 0;
        recolumnize();
        onStateChange.accept(this);
    }

    @Override
    public void searchOver() {
        msg.say("Search done. " + fileList.length() + " file" + (fileList.length() == 1 ? "" : "s")
                + " found...");
        searchButton.setText("Search");
    }

    /**
     * Starts a search using the current search string and type.
     */
    public void startSearch() {
        if (searchEngine != null) {
            stopSearch();
        }

        // Capture the search parameters for tab title
        lastSearchString = getSearchString();
        try {
            lastSearchTypeName = choice.getSelectedDescription();
        } catch (Exception e) {
            lastSearchTypeName = "Search";
        }

        searchEngine = new SearchEngine(protocol,
                                        hashManager,
                                        context,
                                        tracker,
                                        this,
                                        this,
                                        getMysterType(),
                                        lastSearchString);
        searchEngine.run();
        onStateChange.accept(this);
    }

    /**
     * Stops the current search if one is running.
     */
    public void stopSearch() {
        if (searchEngine == null) {
            return;
        }

        searchEngine.flagToEnd();
        searchEngine = null;
    }

    private void recolumnize() {
        metaDateHandler = ClientInfoFactoryUtilities.getHandler(tdList, getMysterType());
        int max = metaDateHandler.getNumberOfColumns();
        fileList.setNumberOfColumns(max);

        for (int i = 0; i < max; i++) {
            fileList.setColumnName(i, metaDateHandler.getHeader(i));
            fileList.setColumnWidth(i, metaDateHandler.getHeaderSize(i));
        }
    }

    @Override
    public boolean addSearchResults(SearchResult[] resultArray) {
        @SuppressWarnings("unchecked")
        MCListItemInterface<SearchResult>[] m = java.util.Arrays.stream(resultArray)
            .map(metaDateHandler::getMCListItem)
            .toArray(MCListItemInterface[]::new);
        fileList.addItem(m);
        resultCount = fileList.length();
        onStateChange.accept(this);
        return true;
    }

    @Override
    public void searchStats(SearchResult s) {
        Util.invokeNowOrLater(() -> fileList.getPane().repaint());
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
     * This updates the tab title without performing a search.
     *
     * @param searchString the search string that was used
     */
    public void restoreSearchState(String searchString) {
        lastSearchString = searchString != null ? searchString : "";
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

        // Add result count if we have results
        if (resultCount > 0) {
            return displayText + " (" + resultCount + ")";
        }

        return displayText;
    }

    /**
     * Cleans up resources when this tab is closed.
     */
    public void dispose() {
        stopSearch();
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

    private static void downloadFile(Object s) {
        ((SearchResult) (s)).download();
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

