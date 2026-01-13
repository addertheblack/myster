/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.client.ui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import com.general.mclist.ColumnSortable;
import com.general.mclist.GenericMCListItem;
import com.general.mclist.JMCList;
import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.general.mclist.MCListFactory;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableByte;
import com.general.mclist.SortableString;
import com.general.mclist.TreeMCList;
import com.general.mclist.TreeMCListTableModel;
import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.general.mclist.TreeMCListTableModel.TreePath;
import com.general.mclist.TreeMCListTableModel.TreePathString;
import com.general.util.IconLoader;
import com.general.util.MessageField;
import com.general.util.MessagePanel;
import com.general.util.StandardWindowBehavior;
import com.general.util.Util;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.server.ServerPreferences;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.net.stream.client.msdownload.MultiSourceUtilities.DefaultDialogProvider;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowPrefDataKeeper;
import com.myster.ui.WindowPrefDataKeeper.PrefData;
import com.myster.util.ContextMenu;
import com.myster.util.Sayable;

public class ClientWindow extends MysterFrame implements Sayable {
    private static final String ENTER_AN_IP_HERE = "Enter a server address";
    private static final String WINDOW_KEEPER_KEY = "Myster's Client Windows";
    private static final String CLIENT_WINDOW_TITLE_PREFIX = "Direct Connection ";
    
    private static final int XDEFAULT = 640;
    private static final int YDEFAULT = 400;
    private static final int SBXDEFAULT = 72; //send button X default
    private static final int GYDEFAULT = 50; //Generic Y default
    

    private static int counter = 0;

    // Instance fields for dependencies (no longer static)
    private final MysterProtocol protocol;
    private final HashCrawlerManager hashManager;
    private final Tracker tracker;
    private final ServerPreferences serverPreferences;
    private final TypeDescriptionList typeDescriptionList;

    private JButton connect;
    private JButton toggleStatsButton;
    private JTextField ipTextField;
    private MCList<MysterType> fileTypeList;
    private JMCList<String> fileList;
    private JTextArea statsPanel;
    private JSplitPane splitPane;
    private String currentip;
    private MessageField msg;
    private TypeListerThread connectToThread;
    private FileListerThread fileListThread;
    private FileInfoListerThread fileInfoListerThread;
    
    private boolean hasBeenShown = false;
    
    private final MysterFrameContext context;
    private Runnable savePrefs;
    
    private Optional<ClientWindowData> initiaData = Optional.empty();

    public record ClientWindowData(Optional<String> ip, Optional<MysterType> type, Optional<String> fileName) {}
    
    private static Optional<MysterType> getFromPrefs(Preferences p) {
        String s = p.get(TYPE_KEY, null);
        
        if (s == null) {
            return Optional.empty();
        }
        try {
            byte[] mm = Util.fromHexString(s);

            return Optional.of(new MysterType(mm));
        } catch (Exception ex) {
            ex.printStackTrace();
            return Optional.empty();
        }

    }

    public static int initWindowLocations(MysterFrameContext c) {
        List<PrefData<ClientWindowData>> lastLocs = c.keeper().getLastLocs(WINDOW_KEEPER_KEY, (p) -> {
            return new ClientWindowData(Optional.ofNullable(p.get(IP_KEY, null)), getFromPrefs(p), Optional.empty());
        });

        for (PrefData<ClientWindowData> prefData : lastLocs) {
            ClientWindow window = c.clientWindowProvider().getOrCreateWindow(prefData.data());
            window.setBounds(prefData.location().bounds());
            window.show();
        }
        
        return lastLocs.size();
    }

    // Constructor for creating a new window with no initial data
    public ClientWindow(MysterFrameContext c,
                        MysterProtocol protocol,
                        HashCrawlerManager hashManager,
                        Tracker tracker,
                        ServerPreferences serverPreferences,
                        TypeDescriptionList typeDescriptionList) {
        super(c, "Direct Connection " + (++counter));
        
        this.context = c;
        this.protocol = protocol;
        this.hashManager = hashManager;
        this.tracker = tracker;
        this.serverPreferences = serverPreferences;
        this.typeDescriptionList = typeDescriptionList;

        init();
    }

    // Constructor for creating a window with initial data
    public ClientWindow(MysterFrameContext c,
                        ClientWindowData initialData,
                        MysterProtocol protocol,
                        HashCrawlerManager hashManager,
                        Tracker tracker,
                        ServerPreferences serverPreferences,
                        TypeDescriptionList typeDescriptionList) {
        super(c, "Direct Connection " + (++counter));
        
        this.context = c;
        this.protocol = protocol;
        this.hashManager = hashManager;
        this.tracker = tracker;
        this.serverPreferences = serverPreferences;
        this.typeDescriptionList = typeDescriptionList;

        init();
        this.initiaData = Optional.of(initialData);
        initialData.ip.ifPresent(ip -> {
            ipTextField.setText(ip);
            
            if (initialData.type().isEmpty()) {
                this.initiaData = Optional.empty();
            }
        });
        ipTextField.setForeground(javax.swing.UIManager.getColor("TextField.foreground"));
    }
    
    private void recursivelyStartDownloads(TreeMCListTableModel<String> model, TreeMCListItem<String> item, Optional<Path> baseDirectory, Path relativePath) {
        if (item.isContainer()) {
            TreePath myPathOrFail = item.getMyPathOrFail();
            for (TreeMCListItem<String> i : model.getChildrenAtPath(myPathOrFail)) {
                recursivelyStartDownloads(model, i, baseDirectory, relativePath.resolve(Path.of(item.getObject())));
            }
        } else {
            try {
                protocol.getStream()
                        .downloadFile(new MSDownloadParams(context,
                                                           hashManager,
                                                           new MysterFileStub(MysterAddress
                                                                   .createMysterAddress(currentip),
                                                                              getCurrentType(),
                                                                              item.getObject()),
                                                           baseDirectory,
                                                           relativePath));
            } catch (UnknownHostException e1) {
                e1.printStackTrace();
            }
        }
    }
    
    /**
     * Filters a list of selected items to remove any item whose ancestor is also in the list.
     * This prevents duplicate downloads when both a folder and its contents are selected.
     * 
     * Optimized O(n log n) algorithm: sorts by depth first, then checks each item against
     * already-claimed ancestor paths. This is much faster than the naive O(n²) approach.
     * 
     * @param selectedItems the list of selected items
     * @return a list containing only top-level items (no descendants of other selected items)
     */
    private List<TreeMCListItem<String>> filterToTopLevelItems(List<TreeMCListItem<String>> selectedItems) {
        if (selectedItems.size() <= 1) {
            return new java.util.ArrayList<>(selectedItems);
        }

        // Sort by path depth (shallowest first)
        // This ensures parent folders are always processed before their descendants
        List<TreeMCListItem<String>> sorted = new java.util.ArrayList<>(selectedItems);
        sorted.sort((a, b) -> {
            TreePath pathA = a.isContainer() ? a.getMyPathOrFail() : a.getParent();
            TreePath pathB = b.isContainer() ? b.getMyPathOrFail() : b.getParent();
            return Integer.compare(pathA.getIndentLevel(), pathB.getIndentLevel());
        });

        // Track paths we've already claimed (will download)
        java.util.Set<TreePath> claimedPaths = new java.util.HashSet<>();
        List<TreeMCListItem<String>> result = new java.util.ArrayList<>();

        for (TreeMCListItem<String> item : sorted) {
            TreePath itemPath = item.isContainer() ? item.getMyPathOrFail() : item.getParent();
            
            // Check if any already-claimed path is an ancestor of this item
            boolean hasClaimedAncestor = claimedPaths.stream()
                .anyMatch(claimed -> claimed.isAncestorOfOrSame(itemPath));
            
            if (!hasClaimedAncestor) {
                result.add(item);
                // Only add container paths to claimed set (files can't be ancestors)
                if (item.isContainer()) {
                    claimedPaths.add(item.getMyPathOrFail());
                }
            }
        }

        return result;
    }

    private static final String IP_KEY = "Ip Key";
    private static final String TYPE_KEY = "Type Key";
    
    private void init() {
        savePrefs = context.keeper().addFrame(this, (p) -> {
            if (getCurrentIP()!= null) {
                p.put(IP_KEY, getCurrentIP());
            } else {
                p.remove(IP_KEY);
            }
            
            
            if (getCurrentType()!= null) {
                p.put(TYPE_KEY, getCurrentType().toHexString());
            } else {
                p.remove(TYPE_KEY);
            }
        }, WINDOW_KEEPER_KEY, WindowPrefDataKeeper.MULTIPLE_WINDOWS);
        
        setLayout(new GridBagLayout());
        var builder = new com.general.util.GridBagBuilder()
            .withFill(GridBagConstraints.BOTH)
            .withInsets(new Insets(5, 5, 5, 5));

        statsPanel = MessagePanel.createNew("");
        statsPanel.setMinimumSize(new Dimension(150, 1));
        statsPanel.setPreferredSize(new Dimension(200, 1));
        statsPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));

        connect = new JButton("Connect");
        connect.setIcon(IconLoader.loadSvg(ClientWindow.class, "connect-button"));
        connect.setSize(SBXDEFAULT, GYDEFAULT);
        getRootPane().setDefaultButton(connect);

        // Create toggle button for stats panel
        toggleStatsButton = new JButton("≡");
        toggleStatsButton.setToolTipText("Show/Hide Stats Panel");
        toggleStatsButton.setPreferredSize(new Dimension(30, connect.getPreferredSize().height));
        toggleStatsButton.setFocusable(false);

        ipTextField = new JTextField(ENTER_AN_IP_HERE) {
            @Override
            public String getText() {
                String text = super.getText();
                if (text.equals(ENTER_AN_IP_HERE)) {
                    return "";
                }
                return text;
            }
        };
        ipTextField.setEditable(true);
        ipTextField.setToolTipText(ENTER_AN_IP_HERE);
        
        ipTextField.setForeground(javax.swing.UIManager.getColor("TextField.inactiveForeground"));
        
        
        ipTextField.addFocusListener(new FocusListener() {
            @Override
            public void focusLost(FocusEvent e) {
                if (ipTextField.getText().equals("")) {
                    ipTextField.setText(ENTER_AN_IP_HERE);
                    ipTextField.setForeground(javax.swing.UIManager.getColor("TextField.inactiveForeground"));
                }
            }
            
            @Override
            public void focusGained(FocusEvent e) {
                // basically if getText().equals("") it means we're displaying the ENTER_AN_IP_HERE.. so don't get confused
                if (ipTextField.getText().equals("")) {
                    ipTextField.setForeground(javax.swing.UIManager.getColor("TextField.foreground"));
                    ipTextField.setText("");
                }
            }
        });

        fileTypeList = MCListFactory.buildMCList(1, true, this);
        fileTypeList.sortBy(-1);
        fileTypeList.setColumnName(0, "Type");
        
        fileList = TreeMCList.create(new String[]{"Name", "Size"}, new TreePathString(new String[] {}));
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JMenuItem downloadMenuItem = ContextMenu.createDownloadItem(fileList, _ -> {
            List<TreeMCListItem<String>> itemsToDownload = extractItemsFromSelection();
            
            if (itemsToDownload.isEmpty()) {
                return;
            }
            
            // Get the base directory (ask only once for all downloads)
            String pathFromType = context.fileManager().getPathFromType(getCurrentType());
            Optional<Path> baseDir = pathFromType == null ? Optional.empty()
                    : Optional.of(Path.of(pathFromType));
            
            if (baseDir.isEmpty()) {
                var p = new DefaultDialogProvider().askForFolder("Select a folder to save the file in");
                if (p == null) {
                    return;
                }
                
                baseDir = Optional.of(p);
            }
            
            // Download each top-level item
            TreeMCListTableModel<String> model = (TreeMCListTableModel<String>) fileList.getModel();
            for (TreeMCListItem<String> treeItem : itemsToDownload) {
                recursivelyStartDownloads(model, treeItem, baseDir, Path.of(""));
            }
        });
        JMenuItem downloadToMenuItem = ContextMenu.createDownloadToItem(fileList, _ -> {
            List<TreeMCListItem<String>> itemsToDownload = extractItemsFromSelection();
            
            if (itemsToDownload.isEmpty()) {
                return;
            }
            
            // Ask user for base directory via dialog (always ask, don't use preferences)
            var p = new DefaultDialogProvider().askForFolder("Select a folder to save the file in");
            if (p == null) {
                return; // User cancelled
            }
            
            Optional<Path> baseDir = Optional.of(p);
            
            // Download each top-level item
            TreeMCListTableModel<String> model = (TreeMCListTableModel<String>) fileList.getModel();
            for (TreeMCListItem<String> treeItem : itemsToDownload) {
                recursivelyStartDownloads(model, treeItem, baseDir, Path.of(""));
            }
        });
        JMenuItem bookmarkMenuItem = ContextMenu.createBookmarkServerItem(fileList, e -> {
            int index = fileList.getSelectedRow();
            if (index == -1) {
                return;
            }
        });
        
        // OPEN FILE ON DISK!
        ContextMenu.addPopUpMenu(fileList, ()->{}, downloadMenuItem, downloadToMenuItem, null, bookmarkMenuItem);
        
        fileList.sortBy(0);
        
        msg = new MessageField("Idle...");

        // Create the left side panel for the split pane (file lists)
        JPanel leftPanel = new JPanel(new GridBagLayout());
        var leftBuilder = new com.general.util.GridBagBuilder()
            .withFill(GridBagConstraints.BOTH)
            .withInsets(new Insets(0, 0, 0, 0));
        
        leftPanel.add(fileTypeList.getPane(), leftBuilder.withGridLoc(0, 0).withSize(1, 1).withWeight(0, 1));
        leftPanel.add(fileList.getPane(), leftBuilder.withGridLoc(1, 0).withSize(1, 1).withWeight(1, 1).withInsets(new Insets(0, 10, 0, 0)));

        fileList.getPane().setMinimumSize(new Dimension(1, 1));
        fileList.getPane().setPreferredSize(new Dimension(1, 1));
        fileList.getPane().setMaximumSize(new Dimension(1,1));
        
        // Create the split pane with file lists on left, stats on right
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, statsPanel);
        splitPane.setResizeWeight(1.0); // Give all extra space to left side
        statsPanel.setVisible(false); // hide by default will collapse the right side
        
        // Set minimum size for left side to prevent it from collapsing
        leftPanel.setMinimumSize(new Dimension(400, 1));
        
        // Start with stats panel collapsed (divider all the way to the right)
        splitPane.setDividerLocation(1.0);
        
        // Add components to the frame: top row (connect/IP/toggle), middle (split pane), bottom (message)
        add(ipTextField, builder.withGridLoc(0, 0).withSize(1, 1).withWeight(1, 0));
        add(connect, builder.withGridLoc(1, 0).withSize(1, 1).withWeight(0, 0));
        add(toggleStatsButton, builder.withGridLoc(2, 0).withSize(1, 1).withWeight(0, 0));
        add(splitPane, builder.withGridLoc(0, 1).withSize(3, 1).withWeight(1, 1));
        add(msg, builder.withGridLoc(0, 2).withSize(3, 1).withWeight(1, 0));
        
        pack();

        Dimension preferredSize = connect.getPreferredSize();
        preferredSize.width *= 2;
        fileTypeList.getPane().setPreferredSize(preferredSize);

        setResizable(true);
        setSize(XDEFAULT, YDEFAULT);

        //filelisting.addActionListener(???);
        final ActionListener connectButtonEvent = new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                startConnect();
            }
        };
        connect.addActionListener(connectButtonEvent);
        ipTextField.addActionListener(connectButtonEvent);
        
        // Toggle stats panel visibility
        toggleStatsButton.addActionListener(_ -> {
            boolean isVisible = statsPanel.isVisible();
            statsPanel.setVisible(!isVisible);
            if (!isVisible) {
                // Show the stats panel - set divider to 75% position
                splitPane.setDividerLocation(0.75);
            } else {
                // Hide the stats panel - move divider all the way right
                splitPane.setDividerLocation(1.0);
            }
        });

        fileTypeList.addMCListEventListener(new MCListEventAdapter(){
            public void selectItem(MCListEvent e) {
                startFileList();
                
                savePrefs.run();
            }

            public void unselectItem(MCListEvent e) {
                stopFileListing();
                
                savePrefs.run();
            }
        });
        fileList.addMCListEventListener(new FileListAction(protocol, hashManager, getMysterFrameContext(), this));
        fileList.addMCListEventListener(new MCListEventAdapter(){
            public void selectItem(MCListEvent e) {
                startStats();
            }

            public void unselectItem(MCListEvent e) {
                stopStats();
            }
        });
        fileList.setColumnWidth(0, 300);

        addWindowListener(new StandardWindowBehavior());
    }

    private List<TreeMCListItem<String>> extractItemsFromSelection() {
        int[] selectedIndices = fileList.getSelectedIndexes();
        if (selectedIndices.length == 0) {
            return List.of();
        }

        // Collect all selected items
        List<TreeMCListItem<String>> selectedItems = new java.util.ArrayList<>();
        for (int index : selectedIndices) {
            MCListItemInterface<String> m = fileList.getMCListItem(index);
            if (m instanceof TreeMCListItem<String> treeItem) {
                selectedItems.add(treeItem);
            } else {
                throw new IllegalStateException("Must be a TreeMCListItem<String> but was " + m.getClass().getName());
            }
        }
        
        // Filter out items whose ancestors are also selected (keep only top-level items)
        return filterToTopLevelItems(selectedItems);
    }
    
    public void show() {
        super.show();
        if (!ipTextField.getText().equals("") && !hasBeenShown) {
            Util.invokeLater(() -> {
                connect.doClick();
            });
            hasBeenShown = true;
        }
    }
        
    public void dispose() {
        super.dispose();
        stopConnect();
    }

    public void addItemToTypeList(MysterType t) {
        fileTypeList.addItem(new GenericMCListItem<MysterType>(new Sortable[] {
                                     new SortableString(typeDescriptionList.get(t)
                                             .map(TypeDescription::getDescription)
                                             .orElse(t.toString())),
                                     new SortableString(t.toString()) }, t));

        initiaData.ifPresent(data -> {
            if (t.equals(data.type().orElse(null))) {
                fileTypeList.select(fileTypeList.length()-1);
                
                if (data.fileName().isEmpty()) {
                    initiaData = Optional.empty();
                }
            }
        });
    }

    // this containers map is cleared when the filelist is cleared
    final HashMap<TreePath, FolderMCListItem<String>> containers = new HashMap<TreePath, FolderMCListItem<String>>();
    
    @SuppressWarnings("boxing")
    public void addItemsToFileList(FileRecord[] files) {
        @SuppressWarnings("unchecked")
        TreeMCListItem<String>[] items = new TreeMCListItem[files.length];

        var localContainers = new HashMap<TreePath, FolderMCListItem<String>>();
        for (int i = 0; i < files.length; i++) {
            var parentPath = extractParentPath(extractPath(files[i]));
            
            var path = new TreePathString(parentPath);

            mkdir(localContainers, parentPath);

            final var file = files[i];
            
            items[i] = new TreeMCListItem<String>(path,
                                                  extractFileRecordElement(file),
                                                  Optional.empty());
        }
        
        // Figure out which folders are ones that we need to add and discard the ones we already have
        List<FolderMCListItem<String>> newContainersToAdd = Util.filter(localContainers.values(), z -> !containers.containsKey(z.getMyPathOrFail()));
        
        // now add the new ones to the containers map
        for (FolderMCListItem<String> treeMCListItem : newContainersToAdd) {
            if (containers.containsKey(treeMCListItem.getMyPathOrFail())) {
                continue;
            }
            containers.put(treeMCListItem.getMyPathOrFail(), treeMCListItem);
        }

        // now we've figured out which folder objects we need and added those to the containers
        // calculate the folder sizes by adding all the file's sizes to their nested folders
        for (int i = 0; i < files.length; i++) {
            addSizeToParentFolders(containers,
                                   extractParentPath(extractPath(files[i])),
                                   files[i].metaData().getLong("/size").orElse(0l));
        }

        fileList.addItem(items);
        fileList.addItem(newContainersToAdd.toArray(new TreeMCListItem[] {}));
    }

    private static void mkdir(HashMap<TreePath, FolderMCListItem<String>> knownContainers, String[] p) {
        loopBackThroughParents(p, (current, parent) -> {
            if (knownContainers.containsKey(new TreePathString(current))) {
                return; // we've already done this one so skip
            }

            knownContainers
                    .put(new TreePathString(current),
                         new FolderMCListItem<String>(new TreePathString(parent),
                                                      extractDirElement(current[current.length - 1]),
                                                      Optional.of(new TreePathString(current))));
        });
    }
    
    
    private static void addSizeToParentFolders(HashMap<TreePath, FolderMCListItem<String>> knownContainers, String[] p, long sizeOfFile) {
        loopBackThroughParents(p, (current, _) ->  knownContainers.get(new TreePathString(current)).addSize(sizeOfFile) );
    }
    
    private static void loopBackThroughParents(String[] p, BiConsumer<String[], String[]> c) {
        for (String[] currentPath = p; currentPath.length > 0;) {
            var parent = extractParentPath(currentPath);
            
            c.accept(currentPath, parent);

            currentPath = parent;
        }
    }

    private ColumnSortable<String> extractFileRecordElement(final FileRecord file) {
        return new ColumnSortable<String>() {
            public Sortable getValueOfColumn(int column) {
                return new Sortable[] { new SortableString(file.file()), new SortableByte(file.metaData()
                        .getLong("/size")
                        .orElse((long) 0)) }[column];
            }

            public String getObject() {
                return file.file();
            }
        };
    }
    
    private static ColumnSortable<String> extractDirElement(final String name) {
        return new ColumnSortable<String>() {
            public Sortable getValueOfColumn(int column) {
                return new Sortable[] { new SortableString(name), new SortableByte(-2) }[column];
            }

            public String getObject() {
                return name;
            }
        };
    }
    
    private static String[] extractPath(FileRecord file) {
        var path = file.metaData().getStringArray("/path").orElse(new String[] {});
        for (String element : path) {
            if (element == null) {
                return new String[] {};
            } else if (element.equals("")) {
                return new String[] {};
            }
        }
        
        return path;
    }
    
    private static String[] extractParentPath(String[] path) {
        if (path.length <= 1) {
            return new String[] {};
        }
        
        return  Arrays.copyOfRange(path, 0, path.length - 1);
    }
    

    public void refreshIP(final MysterAddress address) {
        MysterServer server = tracker.getQuickServerStats(address);

        String fallbackWindowName = address.getInetAddress().isLoopbackAddress() ? "myself" : currentip;
        String windowName =
                (server == null ? fallbackWindowName : "\"" + server.getServerName() + "\" ("
                        + fallbackWindowName + ")");
        setTitle(CLIENT_WINDOW_TITLE_PREFIX + "to " + windowName);
    }

    //To be in an interface??
    public String getCurrentIP() {
        return currentip;
    }

    public MysterType getCurrentType() {
        int selectedIndex = fileTypeList.getSelectedIndex();

        if (selectedIndex != -1)
            return (fileTypeList.getItem(selectedIndex));

        return null;
    }

    public String getCurrentFile() {
        int selectedIndex = fileList.getSelectedIndex();

        if (selectedIndex == -1)
            return "";

        return fileList.getItem(selectedIndex);
    }
    
    public boolean isDir() {
        int rowIndex = fileList.getSelectedIndex();
        if (rowIndex == -1) {
            return false;
        }
        
        var row = (TreeMCListItem<String>)fileList.getMCListItem(rowIndex);
        
        return row.isContainer();
    }

    public void say(String s) {
        Util.invokeNowOrLater( () -> msg.say(s));
    }

    public MessageField getMessageField() {
        return msg;
    }

    public void showFileStats(Map<String, String> k) {
        var builder = new StringBuilder();
        for (Entry<String, String> entry : k.entrySet()) {
            if (entry.getKey().equals("size")) { // hack to
                try {
                    builder.append(entry.getKey() + " : " + com.general.util.Util
                            .getStringFromBytes(Long.parseLong(entry.getValue())) + "\n");
                } catch (NumberFormatException _) {
                    builder.append(entry.getKey() + " : " + entry.getValue() + "\n");
                }
            } else {
                builder.append(entry.getKey() + " : " + entry.getValue() + "\n");
            }
        }
        statsPanel.setText(builder.toString());
    }

    private void stopConnect() {
        if (connectToThread != null) {
            connectToThread.flagToEnd();
        }
        fileTypeList.clearAll();
        stopFileListing();
    }

    private void stopFileListing() {
        if (fileListThread != null) {
            fileListThread.flagToEnd();
        }
        fileList.clearAll();
        stopStats();
        containers.clear();
    }

    private void stopStats() {
        if (fileInfoListerThread != null) {
            fileInfoListerThread.flagToEnd();
        }
        statsPanel.setText("");
    }

    public void startConnect() {
        stopConnect();
        currentip = ipTextField.getText();
        
        if (currentip.isBlank()) {
            currentip = "127.0.0.1:" + serverPreferences.getServerPort();
        }
        
        connectToThread =
                new TypeListerThread(protocol, new TypeListerThread.TypeListener() {
                    public void addItemToTypeList(MysterType s) {
                        ClientWindow.this.addItemToTypeList(s);
                    }

                    public void refreshIP(MysterAddress address) {
                        ClientWindow.this.refreshIP(address);
                    }
                }, this::say, getCurrentIP());
        connectToThread.start();
        
        
        savePrefs.run();
    }

    public void startFileList() {
        stopFileListing();
        fileListThread = new FileListerThread(new FileListerThread.ItemListListener() {
                                                    public void addItemsToFileList(FileRecord[] files) {
                                                        ClientWindow.this.addItemsToFileList(files);
                                                    }
    
                                                    public void finish() {
                                                        selectElement();
                                                    }
                                              },
                                              this::say,
                                              getCurrentIP(),
                                              getCurrentType(),
                                              t -> typeDescriptionList.get(t)
                                                      .map(TypeDescription::getDescription)
                                                      .orElse(t.toString()));
        fileListThread.start();
    }

    private void selectElement() {
        if (initiaData.isEmpty()) {
            return;
        }

        ClientWindowData data = initiaData.get();
        
        if (data.fileName().isEmpty()) {
            return;
        }

        TreeMCListTableModel<String> model = (TreeMCListTableModel<String>) fileList.getModel();

        var foundElement = model.findElement(item -> !item.isContainer()
                && item.getObject().equals(data.fileName().get()));

        foundElement.ifPresent(item -> {
            model.ensureParentFoldersOpen(item);
            item.setSelected(true);
            
            fileList.selectAndScrollToItem(item);
            
            fileList.repaint();
        });
        
        initiaData = Optional.empty();
        
    }

    public void startStats() {
        stopStats();
        fileInfoListerThread =
                new FileInfoListerThread(protocol,
                                         this::showFileStats,
                                         this::say,
                                         getCurrentIP(),
                                         getCurrentType(),
                                         getCurrentFile());
        fileInfoListerThread.start();
    }
    

    public static class FolderMCListItem<E> extends TreeMCListItem<E> {
        private long sizeOfContents = 0;
        
        /**
         * @param myPath present if this is a container/folder. Empty if this is a lead node.
         */
        public FolderMCListItem(TreePath parent, ColumnSortable<E> delegate, Optional<TreePath> myPath) {
            super(parent, delegate, myPath);
        }
        public Sortable<?> getValueOfColumn(int i) {
            if (i == 1) {
                return new SortableByte(sizeOfContents > 0 ? sizeOfContents : -2);
            }
            return super.getValueOfColumn(i);
        }

        public void addSize(long sizeOfFile) {
            sizeOfContents += sizeOfFile;
        }
    }
}


