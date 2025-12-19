package com.myster.progress.ui;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JToolBar;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.general.mclist.ColumnSortable;
import com.general.mclist.JMCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventListener;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.general.mclist.TreeMCList;
import com.general.mclist.TreeMCListTableModel.TreePathString;
import com.general.thread.Cancellable;
import com.general.util.GridBagBuilder;
import com.general.util.IconLoader;
import com.general.util.Util;
import com.myster.net.stream.client.msdownload.MSDownloadControl;
import com.myster.pref.MysterPreferences;
import com.myster.progress.ui.ProgressBannerManager.Banner;
import com.myster.progress.ui.ProgressWindow.AdPanel;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowPrefDataKeeper;
import com.myster.ui.menubar.MysterMenuBar;
import com.myster.util.ContextMenu;

/**
 * A window that manages all downloads in progress using a tree-based MCList.
 * 
 * Layout:
 * - Top: AdPanel (banner image, 468x60)
 * - Bottom: TreeMCList (downloads as containers, sub-downloads as children)
 */
public class ProgressManagerWindow extends MysterFrame {
    private static final int X_SIZE = 468;
    private static final int Y_SIZE = 400; // Initial height
    
    private final AdPanel adPanel;
    private final JMCList<DownloadItem> downloadList;
    private final JToolBar toolbar;
    
    // Actions for toolbar and context menu
    private final Action pauseAction;
    private final Action resumeAction;
    private final Action cancelAction;
    private final Action clearCompletedAction;
    
    // Icons for the tree list
    private static final FlatSVGIcon downloadIcon = IconLoader.loadSvg(ProgressManagerWindow.class, "download-icon");
    private static final FlatSVGIcon connectionIcon = IconLoader.loadSvg(ProgressManagerWindow.class, "connection-icon");
    private static final String WINDOW_KEEPER_KEY = "Progress Manager Window";
    
    private final ProgressBannerManager adManager = new ProgressBannerManager(this);
    private final WindowPrefDataKeeper keeper;
    
    public ProgressManagerWindow(MysterFrameContext context) {
        super(context, "Download Manager");
        
        // Register window location saving
        context.keeper().addFrame(this, (_) -> {
            // No custom data to save, just window location
        }, WINDOW_KEEPER_KEY, com.myster.ui.WindowPrefDataKeeper.SINGLETON_WINDOW);
        
        keeper = context.keeper();
        
        // Create the ad panel (same as ProgressWindow)
        adPanel = new AdPanel();
        
        // Load default image
        Image adImage = IconLoader.loadImage("defaultProgressImage.gif", adPanel);
        if (adImage != null) {
            adPanel.addImage(adImage);
        }
        
        // Create actions before toolbar and context menu
        pauseAction = new AbstractAction("Pause") {
            @Override
            public void actionPerformed(ActionEvent e) {
                DownloadMCListItem selectedItem = getSelectedDownloadItem();
                if (selectedItem != null) {
                    selectedItem.getObject().getControl().pause();
                    updateActionStates(); // Update states after pause
                }
            }
        };
        pauseAction.putValue(Action.SHORT_DESCRIPTION, "Pause download");
        pauseAction.setEnabled(false); // Initially disabled
        
        resumeAction = new AbstractAction("Resume") {
            @Override
            public void actionPerformed(ActionEvent e) {
                DownloadMCListItem selectedItem = getSelectedDownloadItem();
                if (selectedItem != null) {
                    selectedItem.getObject().getControl().resume();
                    updateActionStates(); // Update states after resume
                }
            }
        };
        resumeAction.putValue(Action.SHORT_DESCRIPTION, "Resume download");
        resumeAction.setEnabled(false); // Initially disabled
        
        cancelAction = new AbstractAction("Cancel") {
            @Override
            public void actionPerformed(ActionEvent e) {
                DownloadMCListItem selectedItem = getSelectedDownloadItem();
                if (selectedItem != null && selectedItem.cancellable != null) {
                    selectedItem.cancellable.cancel();
                    updateActionStates(); // Update states after cancel
                }
            }
        };
        cancelAction.putValue(Action.SHORT_DESCRIPTION, "Cancel download");
        cancelAction.setEnabled(false); // Initially disabled
        
        clearCompletedAction = new AbstractAction("Clear Completed") {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearCompletedDownloads();
            }
        };
        clearCompletedAction.putValue(Action.SHORT_DESCRIPTION, "Clear all completed/cancelled downloads");
        clearCompletedAction.setEnabled(true); // Always enabled
        
        // Load SVG icons for actions with 16x16 size for toolbar
        FlatSVGIcon pauseIcon = IconLoader.loadSvg(ProgressManagerWindow.class, "pause-icon", 16);
        FlatSVGIcon resumeIcon = IconLoader.loadSvg(ProgressManagerWindow.class, "resume-icon", 16);
        FlatSVGIcon cancelIcon = IconLoader.loadSvg(ProgressManagerWindow.class, "cancel-icon", 16);
        FlatSVGIcon clearIcon = IconLoader.loadSvg(ProgressManagerWindow.class, "clear-icon", 16);
        
        var adaptiveColor = IconLoader.adaptiveColor();
        pauseIcon.setColorFilter(adaptiveColor);
        resumeIcon.setColorFilter(adaptiveColor);
        cancelIcon.setColorFilter(adaptiveColor);
        clearIcon.setColorFilter(adaptiveColor);
        
        pauseAction.putValue(Action.SMALL_ICON, pauseIcon);
        resumeAction.putValue(Action.SMALL_ICON, resumeIcon);
        cancelAction.putValue(Action.SMALL_ICON, cancelIcon);
        clearCompletedAction.putValue(Action.SMALL_ICON, clearIcon);
        
        // Create toolbar
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(pauseAction);
        toolbar.add(resumeAction);
        toolbar.addSeparator();
        toolbar.add(cancelAction);
        toolbar.addSeparator();
        toolbar.add(clearCompletedAction);
        
        // Create the TreeMCList for downloads
        // Columns: Name, Progress, Speed, Status (can be refined later)
        downloadList = TreeMCList.create(
            new String[]{"Download", "Progress", "Speed", "Status"},
            new TreePathString(new String[] {}),
            downloadIcon,  // folder/container icon
            connectionIcon // file/item icon
        );

        // Add selection listener to update action states
        downloadList.addMCListEventListener(new MCListEventListener() {

            @Override
            public void unselectItem(MCListEvent e) {
                updateActionStates();
            }

            @Override
            public void selectItem(MCListEvent e) {
                updateActionStates();
            }

            @Override
            public void doubleClick(MCListEvent e) {}
        });

        
        // Add context menu
        setupContextMenu();
        
        // Set up layout
        Container c = getContentPane();
        c.setLayout(new GridBagLayout());
        
        var builder = new GridBagBuilder()
            .withFill(GridBagConstraints.BOTH)
            .withInsets(new Insets(0, 0, 0, 0));
        
        // Ad panel: top-left anchored, can extend to the right
        c.add(adPanel, builder
            .withGridLoc(0, 0)
            .withSize(1, 1)
            .withWeight(1, 0)
            .withFill(GridBagConstraints.HORIZONTAL)
            .withAnchor(GridBagConstraints.NORTHWEST));
        
        // Toolbar: below ad panel
        c.add(toolbar, builder
            .withGridLoc(0, 1)
            .withSize(1, 1)
            .withWeight(1, 0)
            .withFill(GridBagConstraints.HORIZONTAL));
        
        // Download list: below toolbar, can extend down and right
        c.add(downloadList.getPane(), builder
            .withGridLoc(0, 2)
            .withSize(1, 1)
            .withWeight(1, 1)
            .withFill(GridBagConstraints.BOTH));
        
        downloadList.setColumnWidth(0, 200);
        downloadList.setColumnWidth(1, 200);
        downloadList.setColumnWidth(2, 100);
        downloadList.setColumnWidth(3, 150);
        
        setSize(X_SIZE, Y_SIZE);
        setResizable(true);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        pack();
    }

    private void setupContextMenu() {
        ContextMenu.addPopUpMenu(downloadList,
                                 () -> {},
                                 new JMenuItem(pauseAction),
                                 new JMenuItem(resumeAction),
                                 null,
                                 new JMenuItem(cancelAction),
                                 null,
                                 new JMenuItem(clearCompletedAction));
    }

    /**
     * Update the enabled state of actions based on current selection.
     */
    private void updateActionStates() {
        DownloadMCListItem selectedItem = getSelectedDownloadItem();
        if (selectedItem != null && selectedItem.isContainer()) {
            MSDownloadControl control = selectedItem.getObject().getControl();
            boolean isActive = control.isActive();
            boolean isPaused = control.isPaused();
            
            // Pause/Resume only enabled if download is active and is a container (not a segment)
            pauseAction.setEnabled(isActive && !isPaused);
            resumeAction.setEnabled(isActive && isPaused);
            
            // Cancel enabled if download is active and we have a cancellable
            cancelAction.setEnabled(isActive && selectedItem.cancellable != null);
        } else {
            // Disable all actions if no selection or selection is not a container
            pauseAction.setEnabled(false);
            resumeAction.setEnabled(false);
            cancelAction.setEnabled(false);
        }
    }
    
    /**
     * Clear all completed/cancelled downloads from the list.
     * Removes all items where isActive() returns false.
     */
    private void clearCompletedDownloads() {
        java.util.List<Integer> indexesToRemove = new java.util.ArrayList<>();
        
        // Collect indices of inactive downloads
        for (int i = 0; i < downloadList.length(); i++) {
            var item = downloadList.getMCListItem(i);
            if (item instanceof DownloadMCListItem downloadItem) {
                if (!downloadItem.getObject().getControl().isActive()) {
                    indexesToRemove.add(i);
                }
            }
        }
        
        // Remove in reverse order to maintain correct indices
        for (int i = indexesToRemove.size() - 1; i >= 0; i--) {
            downloadList.removeItem(indexesToRemove.get(i));
        }
        
        updateActionStates();
    }
    
    private DownloadMCListItem getSelectedDownloadItem() {
        int selectedIndex = downloadList.getSelectedIndex();
        if (selectedIndex >= 0) {
            var item = downloadList.getMCListItem(selectedIndex);
            if (item instanceof DownloadMCListItem downloadItem) {
                return downloadItem;
            }
        }
        return null;
    }
    
    public AdPanel getAdPanel() {
        return adPanel;
    }
    
    public JMCList<DownloadItem> getDownloadList() {
        return downloadList;
    }
    
    public void addNewBannerToQueue(Banner b) {
        adManager.addNewBannerToQueue(b);
    }
    
    /**
     * Populate the window with fake download data for testing/demonstration.
     */
    public void populateWithFakeData() {
        var rootPath = new com.general.mclist.TreeMCListTableModel.TreePathString(new String[] {});
        
        // Download 1: A movie file with 3 connections
        var download1Item = new DownloadMCListItem("BigMovie.mp4", rootPath, () -> {}, true);
        download1Item.getObject().setTotal(1024 * 1024 * 1024); // 1GB
        download1Item.getObject().setProgress(512 * 1024 * 1024); // 512MB
        download1Item.getObject().setSpeed(2 * 1024 * 1024); // 2MB/s
        download1Item.getObject().setStatus("Downloading");
        
        var download1Path = new com.general.mclist.TreeMCListTableModel.TreePathString(new String[] {"BigMovie.mp4"});
        var conn1_1Item = new DownloadMCListItem("192.168.1.100", download1Path, null, false);
        conn1_1Item.getObject().setTotal(400 * 1024 * 1024);
        conn1_1Item.getObject().setProgress(250 * 1024 * 1024);
        conn1_1Item.getObject().setSpeed(800 * 1024);
        conn1_1Item.getObject().setStatus("Active");
        
        var conn1_2Item = new DownloadMCListItem("10.0.0.5", download1Path, null, false);
        conn1_2Item.getObject().setTotal(400 * 1024 * 1024);
        conn1_2Item.getObject().setProgress(200 * 1024 * 1024);
        conn1_2Item.getObject().setSpeed(700 * 1024);
        conn1_2Item.getObject().setStatus("Active");
        
        var conn1_3Item = new DownloadMCListItem("172.16.0.20", download1Path, null, false);
        conn1_3Item.getObject().setTotal(224 * 1024 * 1024);
        conn1_3Item.getObject().setProgress(62 * 1024 * 1024);
        conn1_3Item.getObject().setSpeed(500 * 1024);
        conn1_3Item.getObject().setStatus("Active");
        
        // Download 2: A music album with 2 connections
        var download2Item = new DownloadMCListItem("Album.zip", rootPath, null, true);
        download2Item.getObject().setTotal(150 * 1024 * 1024); // 150MB
        download2Item.getObject().setProgress(100 * 1024 * 1024); // 100MB
        download2Item.getObject().setSpeed(1 * 1024 * 1024); // 1MB/s
        download2Item.getObject().setStatus("Downloading");
        
        var download2Path = new com.general.mclist.TreeMCListTableModel.TreePathString(new String[] {"Album.zip"});
        var conn2_1Item = new DownloadMCListItem("Connection 1: 192.168.1.50", download2Path, null, false);
        conn2_1Item.getObject().setTotal(75 * 1024 * 1024);
        conn2_1Item.getObject().setProgress(55 * 1024 * 1024);
        conn2_1Item.getObject().setSpeed(600 * 1024);
        conn2_1Item.getObject().setStatus("Active");
        
        var conn2_2Item = new DownloadMCListItem("Connection 2: 10.0.0.8", download2Path, null, false);
        conn2_2Item.getObject().setTotal(75 * 1024 * 1024);
        conn2_2Item.getObject().setProgress(45 * 1024 * 1024);
        conn2_2Item.getObject().setSpeed(400 * 1024);
        conn2_2Item.getObject().setStatus("Active");
        
        // Download 3: A document (waiting for connection)
        var download3Item = new DownloadMCListItem("Document.pdf", rootPath, null, true);
        download3Item.getObject().setTotal(5 * 1024 * 1024); // 5MB
        download3Item.getObject().setProgress(0);
        download3Item.getObject().setSpeed(0);
        download3Item.getObject().setStatus("Waiting for sources...");
        
        // Add all items to the list
        downloadList.addItem(download1Item);
        downloadList.addItem(conn1_1Item);
        downloadList.addItem(conn1_2Item);
        downloadList.addItem(conn1_3Item);
        downloadList.addItem(download2Item);
        downloadList.addItem(conn2_1Item);
        downloadList.addItem(conn2_2Item);
        downloadList.addItem(download3Item);
    }
    
    /**
     * Test main method to display the window with fake data.
     */
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            // Create mock objects for testing
            var mockWindowManager = new com.myster.ui.WindowManager() {
                @Override
                public void updateMenu() {
                    // Do nothing for test
                }
            };
            
            var mockKeeper = new com.myster.ui.WindowPrefDataKeeper(MysterPreferences.getInstance()) {
                @Override
                public <T> java.util.List<PrefData<T>> getLastLocs(String key, 
                        java.util.function.Function<java.util.prefs.Preferences, T> extractor) {
                    return java.util.Collections.emptyList();
                }
            };
            
            var context = new MysterFrameContext(new MysterMenuBar(), mockWindowManager, null, mockKeeper, null, null);
            
            ProgressManagerWindow window = new ProgressManagerWindow(context);
            window.populateWithFakeData();
            window.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
            window.setVisible(true);
        });
    }
    
    /**
     * TreeMCListItem wrapper for DownloadItem that provides column data.
     */
    public static class DownloadMCListItem extends com.general.mclist.TreeMCListTableModel.TreeMCListItem<DownloadItem> {
        private Cancellable cancellable;

        public DownloadMCListItem(String name, com.general.mclist.TreeMCListTableModel.TreePath parent, Cancellable cancellable, boolean isContainer) {
            super(parent, 
                  new DownloadDelegate(name),
                  isContainer ? java.util.Optional.of(new TreePathString(appendToPath(parent, name))) : java.util.Optional.empty());
            this.cancellable = cancellable;
        }
        
        private static String[] appendToPath(com.general.mclist.TreeMCListTableModel.TreePath parent, String name) {
            if (parent instanceof TreePathString tps) {
                String[] parentPath = getPathArray(tps);
                String[] newPath = new String[parentPath.length + 1];
                System.arraycopy(parentPath, 0, newPath, 0, parentPath.length);
                newPath[parentPath.length] = name;
                return newPath;
            }
            return new String[] { name };
        }
        
        private static String[] getPathArray(TreePathString path) {
            try {
                var field = TreePathString.class.getDeclaredField("path");
                field.setAccessible(true);
                
                return (String[]) field.get(path);
            } catch (Exception e) {
                return new String[] {};
            }
        }

        public void setCancellable(Cancellable c) {
            cancellable = c;
        }
    }
    
    /**
     * ColumnSortable delegate for DownloadItem.
     */
    private static class DownloadDelegate implements ColumnSortable<DownloadItem> {
        private final DownloadItem item;
        
        public DownloadDelegate(String name) {
            this.item = new DownloadItem(name);
        }
        
        @Override
        public DownloadItem getObject() {
            return item;
        }
        
        @Override
        public Sortable<?> getValueOfColumn(int column) {
            return switch (column) {
                case 0 -> new SortableString(item.getName());
                case 1 -> new SortableString(formatProgress(item));
                case 2 -> new SortableString(formatSpeed(item));
                case 3 -> new SortableString(item.getStatus());
                default -> new SortableString("");
            };
        }
        
        private String formatProgress(DownloadItem item) {
            if (item.getTotal() == 0) {
                return "0%";
            }
            long percent = (item.getProgress() * 100) / item.getTotal();
            String progressStr = Util.getStringFromBytes(item.getProgress());
            String totalStr = Util.getStringFromBytes(item.getTotal());
            return percent + "% (" + progressStr + " / " + totalStr + ")";
        }
        
        private String formatSpeed(DownloadItem item) {
            if (item.getSpeed() <= 0) {
                return "";
            }
            return Util.getStringFromBytes(item.getSpeed()) + "/s";
        }
    }
    
    /**
     * Represents a download item (either a main download or a sub-download).
     * This will be expanded later to track actual download state.
     */
    public static class DownloadItem {
        private String name;
        private long progress;
        private long total;
        private int speed; // bytes per second
        private String status;
        private MSDownloadControl control;
        
        public DownloadItem(String name) {
            this.name = name;
            this.progress = 0;
            this.total = 0;
            this.speed = 0;
            this.status = "Waiting";
            this.control = new MSDownloadControl() {
                private boolean paused = false;
                private boolean active = true;
                
                @Override
                public void resume() {
                    paused = false;
                }
                
                @Override
                public void pause() {
                    paused = true;
                }
                
                @Override
                public void cancel() {
                    active = false;
                }
                
                @Override
                public boolean isPaused() {
                    return paused;
                }
                
                @Override
                public boolean isActive() {
                    return active;
                }
            };
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public long getProgress() {
            return progress;
        }
        
        public void setProgress(long progress) {
            this.progress = progress;
        }
        
        public long getTotal() {
            return total;
        }
        
        public void setTotal(long total) {
            this.total = total;
        }
        
        public int getSpeed() {
            return speed;
        }
        
        public void setSpeed(int speed) {
            this.speed = speed;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }

        public void setControl(MSDownloadControl control) {
           this.control = control;
        }
        
        public MSDownloadControl getControl() {
            return this.control;
        }
    }

    public int initWindowLocations() {
        if (!Util.isEventDispatchThread()) {
            throw new IllegalStateException("initWindowLocations() must be called on the EDT");
        }
        
        
        var lastLocs = keeper.getLastLocs("Progress Manager Window", (p) -> {
            return null; // No custom data needed for this window, just location
        });
        
        if (!lastLocs.isEmpty()) {
            var prefData = lastLocs.get(0); // Only one progress manager window
            setBounds(prefData.location().bounds());
            setVisible(true);
            return 1;
        }
        
        return 0;
        
    }
}
