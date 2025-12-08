package com.myster.progress.ui;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.general.mclist.ColumnSortable;
import com.general.mclist.JMCList;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.general.mclist.TreeMCList;
import com.general.mclist.TreeMCListTableModel.TreePathString;
import com.general.util.GridBagBuilder;
import com.general.util.IconLoader;
import com.general.util.Util;
import com.myster.pref.MysterPreferences;
import com.myster.progress.ui.ProgressBannerManager.Banner;
import com.myster.progress.ui.ProgressWindow.AdPanel;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowPrefDataKeeper;
import com.myster.ui.menubar.MysterMenuBar;

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
    
    // Icons for the tree list
    private static final FlatSVGIcon downloadIcon = IconLoader.loadSvg(ProgressManagerWindow.class, "download-icon");
    private static final FlatSVGIcon connectionIcon = IconLoader.loadSvg(ProgressManagerWindow.class, "connection-icon");
    private static final String WINDOW_KEEPER_KEY = "Progress Manager Window";
    
    private final ProgressBannerManager adManager = new ProgressBannerManager(this);
    private final WindowPrefDataKeeper keeper;
    
    
    public ProgressManagerWindow(MysterFrameContext context) {
        super(context, "Download Manager");
        
        // Register window location saving
        context.keeper().addFrame(this, (p) -> {
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
        
        // Create the TreeMCList for downloads
        // Columns: Name, Progress, Speed, Status (can be refined later)
        downloadList = TreeMCList.create(
            new String[]{"Download", "Progress", "Speed", "Status"},
            new TreePathString(new String[] {}),
            downloadIcon,  // folder/container icon
            connectionIcon // file/item icon
        );
        
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
        
        // Download list: below ad panel, can extend down and right
        c.add(downloadList.getPane(), builder
            .withGridLoc(0, 1)
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
        var download1Item = new DownloadMCListItem("BigMovie.mp4", rootPath, true);
        download1Item.getObject().setTotal(1024 * 1024 * 1024); // 1GB
        download1Item.getObject().setProgress(512 * 1024 * 1024); // 512MB
        download1Item.getObject().setSpeed(2 * 1024 * 1024); // 2MB/s
        download1Item.getObject().setStatus("Downloading");
        
        var download1Path = new com.general.mclist.TreeMCListTableModel.TreePathString(new String[] {"BigMovie.mp4"});
        var conn1_1Item = new DownloadMCListItem("192.168.1.100", download1Path, false);
        conn1_1Item.getObject().setTotal(400 * 1024 * 1024);
        conn1_1Item.getObject().setProgress(250 * 1024 * 1024);
        conn1_1Item.getObject().setSpeed(800 * 1024);
        conn1_1Item.getObject().setStatus("Active");
        
        var conn1_2Item = new DownloadMCListItem("10.0.0.5", download1Path, false);
        conn1_2Item.getObject().setTotal(400 * 1024 * 1024);
        conn1_2Item.getObject().setProgress(200 * 1024 * 1024);
        conn1_2Item.getObject().setSpeed(700 * 1024);
        conn1_2Item.getObject().setStatus("Active");
        
        var conn1_3Item = new DownloadMCListItem("172.16.0.20", download1Path, false);
        conn1_3Item.getObject().setTotal(224 * 1024 * 1024);
        conn1_3Item.getObject().setProgress(62 * 1024 * 1024);
        conn1_3Item.getObject().setSpeed(500 * 1024);
        conn1_3Item.getObject().setStatus("Active");
        
        // Download 2: A music album with 2 connections
        var download2Item = new DownloadMCListItem("Album.zip", rootPath, true);
        download2Item.getObject().setTotal(150 * 1024 * 1024); // 150MB
        download2Item.getObject().setProgress(100 * 1024 * 1024); // 100MB
        download2Item.getObject().setSpeed(1 * 1024 * 1024); // 1MB/s
        download2Item.getObject().setStatus("Downloading");
        
        var download2Path = new com.general.mclist.TreeMCListTableModel.TreePathString(new String[] {"Album.zip"});
        var conn2_1Item = new DownloadMCListItem("Connection 1: 192.168.1.50", download2Path, false);
        conn2_1Item.getObject().setTotal(75 * 1024 * 1024);
        conn2_1Item.getObject().setProgress(55 * 1024 * 1024);
        conn2_1Item.getObject().setSpeed(600 * 1024);
        conn2_1Item.getObject().setStatus("Active");
        
        var conn2_2Item = new DownloadMCListItem("Connection 2: 10.0.0.8", download2Path, false);
        conn2_2Item.getObject().setTotal(75 * 1024 * 1024);
        conn2_2Item.getObject().setProgress(45 * 1024 * 1024);
        conn2_2Item.getObject().setSpeed(400 * 1024);
        conn2_2Item.getObject().setStatus("Active");
        
        // Download 3: A document (waiting for connection)
        var download3Item = new DownloadMCListItem("Document.pdf", rootPath, true);
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
        public DownloadMCListItem(String name, com.general.mclist.TreeMCListTableModel.TreePath parent, boolean isContainer) {
            super(parent, 
                  new DownloadDelegate(name),
                  isContainer ? java.util.Optional.of(new TreePathString(appendToPath(parent, name))) : java.util.Optional.empty());
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
        
        public DownloadItem(String name) {
            this.name = name;
            this.progress = 0;
            this.total = 0;
            this.speed = 0;
            this.status = "Waiting";
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
