/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.search.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicButtonUI;

import com.general.util.StandardWindowBehavior;
import com.myster.net.client.MysterProtocol;
import com.myster.search.HashCrawlerManager;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowPrefDataKeeper;
import com.myster.ui.WindowPrefDataKeeper.PrefData;

/**
 * A window containing multiple search tabs. Each tab provides an independent
 * search interface with its own search field, type selector, results list, and
 * status. Users can add new tabs, close existing tabs, and have multiple
 * SearchWindow instances open simultaneously.
 * <p>
 * Keyboard shortcuts:
 * <ul>
 *   <li>Ctrl+N - Add new tab to this window (or create window if none exists)</li>
 *   <li>Ctrl+Shift+N - Create new search window</li>
 * </ul>
 */
public class SearchWindow extends MysterFrame {
    private static final Logger log = Logger.getLogger(SearchWindow.class.getName());
    
    private static final int XDEFAULT = 640;
    private static final int YDEFAULT = 400;
    
    private static final String PREF_LOCATION_KEY = "Search Window";
    private static final String TAB_COUNT_KEY = "TabCount";
    private static final String SELECTED_TAB_KEY = "SelectedTab";
    private static final String TAB_SEARCH_PREFIX = "TabSearch_";
    private static final String TAB_TYPE_PREFIX = "TabType_";

    private static int windowCounter = 0;

    private static HashCrawlerManager hashManager;
    private static MysterProtocol protocol;
    private static Tracker tracker;

    /** Tracks all active search windows for recreation when all are closed */
    private static final List<SearchWindow> activeWindows = new ArrayList<>();

    private final JTabbedPane tabbedPane;
    private final MysterFrameContext context;
    private Runnable savePrefs;

    /**
     * Initializes static dependencies for all search windows.
     *
     * @param protocol the protocol for network operations
     * @param hashManager the hash crawler manager
     * @param tracker the tracker for server management
     */
    public static void init(MysterProtocol protocol, HashCrawlerManager hashManager, Tracker tracker) {
        SearchWindow.protocol = protocol;
        SearchWindow.hashManager = hashManager;
        SearchWindow.tracker = tracker;
    }

    /**
     * Returns the list of currently active search windows.
     *
     * @return unmodifiable list of active windows
     */
    public static List<SearchWindow> getActiveWindows() {
        return List.copyOf(activeWindows);
    }

    /**
     * Creates a new search window with one empty tab.
     *
     * @param c the frame context
     */
    public SearchWindow(MysterFrameContext c) {
        this(c, null);
    }

    /**
     * Creates a new search window, optionally restoring tabs from saved data.
     *
     * @param c the frame context
     * @param savedData optional saved window data for restoration, or null for new window
     */
    public SearchWindow(MysterFrameContext c, SearchWindowData savedData) {
        super(c, "Search " + (++windowCounter));
        this.context = c;

        activeWindows.add(this);

        tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        addWindowListener(new StandardWindowBehavior());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Stop all searches when window closes
                for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                    SearchTab tab = (SearchTab) tabbedPane.getComponentAt(i);
                    tab.stopSearch();
                }
            }
        });

        // Register with preferences keeper
        savePrefs = c.keeper().addFrame(this, (p) -> {
            p.putInt(TAB_COUNT_KEY, tabbedPane.getTabCount());
            p.putInt(SELECTED_TAB_KEY, tabbedPane.getSelectedIndex());

            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                SearchTab tab = (SearchTab) tabbedPane.getComponentAt(i);
                p.put(TAB_SEARCH_PREFIX + i, tab.getSearchString());
                MysterType type = tab.getMysterType();
                if (type != null) {
                    p.put(TAB_TYPE_PREFIX + i, type.toHexString());
                } else {
                    p.remove(TAB_TYPE_PREFIX + i);
                }
            }
        }, PREF_LOCATION_KEY, WindowPrefDataKeeper.MULTIPLE_WINDOWS);

        // Restore tabs from saved data or create one new tab
        if (savedData != null && !savedData.tabs().isEmpty()) {
            for (TabData tabData : savedData.tabs()) {
                addNewTab(tabData.searchString(), tabData.type());
            }
            if (savedData.selectedTabIndex() >= 0 && savedData.selectedTabIndex() < tabbedPane.getTabCount()) {
                tabbedPane.setSelectedIndex(savedData.selectedTabIndex());
            }
        } else {
            addNewTab();
        }

        setResizable(true);
        pack();
        setSize(XDEFAULT, YDEFAULT);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        // When window is made visible, ensure focus is on the search field of the current tab
        if (visible && tabbedPane.getTabCount() > 0) {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex >= 0) {
                SearchTab tab = (SearchTab) tabbedPane.getComponentAt(selectedIndex);
                // Use invokeLater to ensure the window is fully displayed first
                com.general.util.Util.invokeLater(() -> tab.focusSearchField());
            }
        }
    }

    public void addNewTab() {
        addNewTab("", Optional.empty());
    }

    /**
     * @param searchString initial search string
     * @param type initial type selection
     */
    public void addNewTab(String searchString, Optional<MysterType> type) {
        SearchTab tab = new SearchTab(context, protocol, hashManager, tracker, this::onTabStateChange);

        // Set initial values if provided
        if (searchString != null && !searchString.isEmpty()) {
            tab.setSearchString(searchString);
        }
        type.ifPresent(tab::setMysterType);

        // Restore the search state for proper tab title (when restoring from preferences)
        if (searchString != null && !searchString.isEmpty()) {
            tab.restoreSearchState(searchString);
        }

        int index = tabbedPane.getTabCount();
        tabbedPane.addTab(tab.getTabTitle(), tab);
        tabbedPane.setTabComponentAt(index, createTabComponent(tab));
        tabbedPane.setSelectedIndex(index);

        // Focus the search field in the new tab
        tab.focusSearchField();

        // Set as default button when tab is selected
        getRootPane().setDefaultButton(tab.getSearchButton());

        // Update title
        updateWindowTitle();

        // Save preferences
        if (savePrefs != null) {
            savePrefs.run();
        }
    }

    /**
     * Creates a custom tab component with a close button.
     */
    private JPanel createTabComponent(SearchTab tab) {
        JPanel tabComponent = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabComponent.setOpaque(false);

        JLabel titleLabel = new JLabel(tab.getTabTitle());
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

        JButton closeButton = new JButton("×");
        closeButton.setPreferredSize(new Dimension(17, 17));
        closeButton.setToolTipText("Close this tab");
        closeButton.setUI(new BasicButtonUI());
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusable(false);
        closeButton.setBorder(BorderFactory.createEtchedBorder());
        closeButton.setBorderPainted(false);

        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setBorderPainted(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setBorderPainted(false);
            }
        });

        closeButton.addActionListener(e -> {
            int index = tabbedPane.indexOfComponent(tab);
            if (index >= 0) {
                removeTab(index);
            }
        });

        tabComponent.add(titleLabel);
        tabComponent.add(closeButton);

        return tabComponent;
    }

    /**
     * Removes the tab at the given index.
     */
    private void removeTab(int index) {
        if (index < 0 || index >= tabbedPane.getTabCount()) {
            return;
        }

        SearchTab tab = (SearchTab) tabbedPane.getComponentAt(index);
        tab.dispose();
        tabbedPane.removeTabAt(index);

        // If no tabs left, close the window
        if (tabbedPane.getTabCount() == 0) {
            dispose();
        } else {
            updateWindowTitle();
            if (savePrefs != null) {
                savePrefs.run();
            }
        }
    }

    /**
     * Called when a tab's state changes (e.g., search starts).
     */
    private void onTabStateChange(SearchTab tab) {
        // Update tab title
        int index = tabbedPane.indexOfComponent(tab);
        if (index >= 0) {
            Component tabComponent = tabbedPane.getTabComponentAt(index);
            if (tabComponent instanceof JPanel panel && panel.getComponentCount() > 0) {
                Component first = panel.getComponent(0);
                if (first instanceof JLabel label) {
                    label.setText(tab.getTabTitle());
                }
            }
            tabbedPane.setTitleAt(index, tab.getTabTitle());
        }

        updateWindowTitle();

        if (savePrefs != null) {
            savePrefs.run();
        }
    }

    /**
     * Updates the window title based on current tab.
     */
    private void updateWindowTitle() {
        int selectedIndex = tabbedPane.getSelectedIndex();
        if (selectedIndex >= 0) {
            SearchTab tab = (SearchTab) tabbedPane.getComponentAt(selectedIndex);
            String search = tab.getSearchString();
            if (search != null && !search.trim().isEmpty()) {
                setTitle("Search for \"" + search + "\"");
                return;
            }
        }
        setTitle("Search " + windowCounter);
    }

    @Override
    public void closeWindowEvent() {
        // Ctrl+W should close the current tab, not the whole window
        // (unless it's the last tab, in which case the window closes)
        int selectedIndex = tabbedPane.getSelectedIndex();
        if (selectedIndex >= 0) {
            removeTab(selectedIndex);
        } else {
            // No tabs, close the window
            super.closeWindowEvent();
        }
    }

    @Override
    public void dispose() {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            SearchTab tab = (SearchTab) tabbedPane.getComponentAt(i);
            tab.dispose();
        }

        activeWindows.remove(this);
        super.dispose();
    }

    /**
     * Restores search windows from saved preferences.
     *
     * @param c the frame context
     * @return number of windows restored
     */
    public static int initWindowLocations(MysterFrameContext c) {
        List<PrefData<SearchWindowData>> lastLocs = c.keeper().getLastLocs(PREF_LOCATION_KEY, (p) -> {
            int tabCount = p.getInt(TAB_COUNT_KEY, 1);
            int selectedTab = p.getInt(SELECTED_TAB_KEY, 0);

            List<TabData> tabs = new ArrayList<>();
            for (int i = 0; i < tabCount; i++) {
                String search = p.get(TAB_SEARCH_PREFIX + i, "");
                Optional<MysterType> type = getTypeFromPrefs(p, TAB_TYPE_PREFIX + i);
                tabs.add(new TabData(search, type));
            }

            return new SearchWindowData(tabs, selectedTab);
        });

        for (PrefData<SearchWindowData> prefData : lastLocs) {
            SearchWindow window = new SearchWindow(c, prefData.data());
            window.setBounds(prefData.location().bounds());
            window.setVisible(true);
        }

        return lastLocs.size();
    }

    private static Optional<MysterType> getTypeFromPrefs(java.util.prefs.Preferences p, String key) {
        String s = p.get(key, null);
        if (s == null) {
            return Optional.empty();
        }
        try {
            byte[] bytes = com.general.util.Util.fromHexString(s);
            return Optional.of(new MysterType(bytes));
        } catch (Exception ex) {
            log.fine("Could not parse type from prefs: " + s);
            return Optional.empty();
        }
    }

    /**
     * Data for restoring a search window.
     */
    public record SearchWindowData(List<TabData> tabs, int selectedTabIndex) {}

    /**
     * Data for restoring a single tab.
     */
    public record TabData(String searchString, Optional<MysterType> type) {}
}

