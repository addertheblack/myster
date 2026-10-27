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

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;

import com.general.mclist.ColumnSortable;
import com.general.mclist.GenericMCListItem;
import com.general.mclist.JMCList;
import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.general.mclist.MCListFactory;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.MutableSortableString;
import com.general.mclist.Sortable;
import com.general.mclist.SortableByte;
import com.general.mclist.SortableString;
import com.general.mclist.TreeMCList;
import com.general.mclist.TreeMCListTableModel;
import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.general.mclist.TreeMCListTableModel.TreePath;
import com.general.mclist.TreeMCListTableModel.TreePathString;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.IconLoader;
import com.general.util.MessageField;
import com.general.util.MessagePanel;
import com.general.util.StandardWindowBehavior;
import com.general.util.Util;
import com.myster.access.AccessList;
import com.myster.link.ui.FileLinkCopyActionUtils;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.search.ui.ClientGenericHandleObject;
import com.myster.search.ui.ClientInfoFactoryUtils;
import com.myster.search.ui.FileTypeColumnHandler;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.ParamBuilder;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.thumbnail.RemoteThumbnailCache;
import com.myster.thumbnail.ui.ThumbnailUiUtils;
import com.myster.tracker.MysterServer;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.tracker.Tracker;
import com.myster.type.MetadataTypeId;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;
import com.myster.ui.DownloadStartErrorDialog;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.DownloadDirectoryChooser;
import com.myster.ui.WindowPrefDataKeeper;
import com.myster.ui.WindowPrefDataKeeper.PrefData;
import com.myster.util.ContextMenu;
import com.myster.util.Sayable;

/** A browser window permanently bound to one public-key identity; its endpoint may change. */
public class ClientWindow extends MysterFrame implements Sayable {
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
    private final TypeDescriptionList typeDescriptionList;

    private JButton refreshButton;
    private JButton toggleStatsButton;
    private JTextField serverAddressField;
    private MCList<MysterType> fileTypeList;
    private JMCList<String> fileList;
    private JTextArea statsPanel;
    private ClientFilePreviewPane previewPane;
    private JSplitPane splitPane;
    private final PublicKeyIdentity serverIdentity;
    private MysterAddress serverAddress; //mutable
    private MessageField msg;
    private TypeListerThread connectToThread;
    private FileListerThread fileListThread;
    private FileInfoListerThread fileInfoListerThread;

    private TypeMetadataCache typeMetadataCache;
    private RemoteThumbnailCache thumbnailCache;
    private ClientPreviewController previewController;
    private ClientFileThumbnailController<String> fileThumbnailController;
    private MysterType listedType;
    private MysterAddress thumbnailAddress;
    private final Map<MysterType, MutableSortableString> typeDisplayNames = new HashMap<>();
    private FileTypeColumnHandler currentTypeHandler = new ClientGenericHandleObject();

    private final MysterFrameContext context;
    private Runnable savePrefs;
    
    /**
     * Pending destination supplied by {@link #setInitialData(ClientWindowData)}, for both new
     * and reused windows. This drives automatic server/type/file selection through the normal
     * UI events: type-list completion selects the requested type, that selection starts file listing,
     * and listing completion selects and reveals the requested file.
     *
     * <p>Keep this request across compatible intermediate events, including a file that has
     * not arrived yet in an unfinished listing. Clear it when the destination is reached,
     * a listing establishes that it cannot be reached, or an incompatible user action cancels
     * it (for example, selecting another type/file or refreshing). A new request
     * replaces the previous one. Empty means no automatic selection remains pending.
     *
     * <p>This is the navigation request, not a snapshot of the current selection. Extend
     * {@link ClientWindowData} when navigation needs additional input rather than maintaining
     * a parallel set of navigation fields or callbacks.
     */
    private Optional<ClientWindowData> initialData = Optional.empty();

    /**
     * Destination within a server's public-key identity. The address is a connection hint;
     * only the type and filename are optional. Address-only opening requests must discover
     * the identity through the provider before constructing this data or a window.
     */
    public record ClientWindowData(MysterAddress ip,
                                   PublicKeyIdentity identity,
                                   Optional<MysterType> type,
                                   Optional<String> fileName) {
        public ClientWindowData {
            Objects.requireNonNull(ip, "ip");
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(fileName, "fileName");
        }
    }

    /** The server identity is independent of navigation and the lifetime of any worker. */
    public PublicKeyIdentity getServerIdentity() {
        return serverIdentity;
    }
    
    private static Optional<MysterType> getSelectedTypeFromPrefs(Preferences p) {
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

    /** Restores saved identities; old address-only records use the provider's discovery flow. */
    public static PromiseFuture<Integer> initWindowLocations(MysterFrameContext c) {
        Util.ensureEventDispatchThread();
        record SavedDestination(String addressText, String publicKey, Optional<MysterType> type) {}
        record ResolvedDestination(MysterAddress address, Optional<PublicKeyIdentity> identity,
                                   Optional<MysterType> type) {}
        List<PrefData<SavedDestination>> savedLocations = c.keeper().getLastLocs(WINDOW_KEEPER_KEY, (p) -> {
            return new SavedDestination(p.get(IP_KEY, null), p.get(SERVER_KEY, null),
                    getSelectedTypeFromPrefs(p));
        });

        // Snapshot preferences first: getLastLocs removes their nodes and holds the keeper's
        // monitor while reading. DNS resolution must happen after that monitor is released.
        return PromiseFutures.execute(() -> {
            List<PrefData<ResolvedDestination>> resolvedLocations = new ArrayList<>();

            for (PrefData<SavedDestination> prefData : savedLocations) {
                String address = prefData.data().addressText();

                if (address == null || address.isBlank()) continue;

                try {
                    String savedKey = prefData.data().publicKey();

                    if (savedKey != null && savedKey.isEmpty()) continue;

                    Optional<PublicKeyIdentity> identity = savedKey == null ? Optional.empty()
                            : com.myster.identity.Util.publicKeyFromString(savedKey).map(PublicKeyIdentity::new);
                    // A corrupt saved identity must never turn into permission to bind this
                    // window to whichever server now happens to answer at the old address.
                    if (savedKey != null && identity.isEmpty()) continue;

                    var data = new ResolvedDestination(MysterAddress.createMysterAddress(address),
                                                       identity, prefData.data().type());

                    resolvedLocations.add(new PrefData<>(prefData.location(), data));
                } catch (UnknownHostException | IllegalArgumentException _) {
                    // Older or invalid window records cannot identify a server to restore.
                }
            }

            return resolvedLocations;
        }).mapAsync(resolvedLocations -> {
            List<PromiseFuture<Integer>> restored = new ArrayList<>();

            for (PrefData<ResolvedDestination> prefData : resolvedLocations) {
                // Count failures and cancellations as zero so one failed legacy lookup does
                // not discard the successful count used by startup's default-window decision.
                restored.add(PromiseFuture.newPromiseFuture(result -> {
                    var opening = c.clientWindowProvider().openWindow(prefData.data().address(),
                                                                      prefData.data().identity(),
                                                                      prefData.data().type(),
                                                                      Optional.empty());

                    result.trackForCancellation(opening);

                    opening.withInvoker(Invoker.EDT).addFinallyCallResultListener(opened -> {
                        if (opened.isResult()) {
                            opened.getResult().setBounds(prefData.location().bounds());
                        }
                        result.setResult(opened.isResult() ? 1 : 0);
                    });
                }));
            }

            return PromiseFutures.all(restored).mapAsyncInline(counts ->
                    PromiseFuture.newPromiseFuture(counts.stream().mapToInt(Integer::intValue).sum()));
        }, Invoker.EDT);
    }

    /** Creates a window permanently bound to {@code serverIdentity}, using the supplied endpoint. */
    public ClientWindow(MysterFrameContext c,
                        MysterProtocol protocol,
                        HashCrawlerManager hashManager,
                        Tracker tracker,
                        TypeDescriptionList typeDescriptionList,
                        MysterAddress serverAddress,
                        PublicKeyIdentity serverIdentity) {
        super(c, "Direct Connection " + (++counter));
        
        this.context = c;
        this.protocol = protocol;
        this.hashManager = hashManager;
        this.tracker = tracker;
        this.typeDescriptionList = typeDescriptionList;
        this.serverAddress = Objects.requireNonNull(serverAddress, "serverAddress");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");

        init();

        typeMetadataCache = new TypeMetadataCache(protocol.getStream());
        thumbnailCache = new RemoteThumbnailCache(protocol.getStream());
        previewController = new ClientPreviewController(
                previewPane,
                thumbnailCache,
                this::buildThumbnailRequest,
                () -> previewPane.isShowing() && (getExtendedState() & ICONIFIED) == 0 && !isSelectedFileListItemADir());
        fileThumbnailController = new ClientFileThumbnailController<>(
                fileList, thumbnailCache, this::buildFileThumbnailRequest,
                () -> thumbnailAddress != null && fileList.isShowing()
                        && (getExtendedState() & ICONIFIED) == 0);
    }

    /**
     * Sets initial data for navigating within this window's server.
     * The constructor binds the public-key identity. Subsequent requests may change the endpoint,
     * type, or file, but cannot change that identity. An endpoint change discards old listings and
     * cancels their workers before fetching rows authenticated against the same immutable key.
     * Use {@link ClientWindowProvider#getOrCreateWindow(ClientWindowData)} for another server.
     *
     * The process is sequential:
     * 1. Connect (if needed) - triggers type listing
     * 2. Select type (if needed) - triggers file listing
     * 3. Select file (if needed)
     *
     * Each step is only triggered if not already completed.
     *
     * @param data the destination within this window's server identity
     * @throws IllegalArgumentException if the request changes the server identity
     */
    public void setInitialData(ClientWindowData data) {
        Util.ensureEventDispatchThread();

        if (!serverIdentity.equals(data.identity())) {
            throw new IllegalArgumentException("A client window cannot change its server identity.");
        }

        boolean addressChanged = !serverAddress.equals(data.ip());
        serverAddress = data.ip();
        serverAddressField.setText(serverAddress.toString());

        // Step 1: Set the initial data
        this.initialData = Optional.of(data);

        // Step 2: Check if we need to connect to this window's server
        boolean typesLoaded = fileTypeList.length() > 0;

        if (addressChanged || !typesLoaded) {
            // Need to connect - this will trigger type listing
            // When type listing finishes, its completion callback will select the type
            refresh();
            return;
        }

        // Step 3: Already connected with types loaded, check if we need to select a type
        if (data.type().isPresent()) {
            MysterType targetType = data.type().get();
            int selectedTypeIndex = fileTypeList.getSelectedIndex();

            // Check if the correct type is already selected
            if (selectedTypeIndex >= 0) {
                MysterType currentType = fileTypeList.getMCListItem(selectedTypeIndex).getObject();
                if (currentType.equals(targetType)) {
                    // Correct type already selected
                    // Step 4: Select file if needed
                    if (data.fileName().isPresent()) {
                        selectElement();
                    } else {
                        initialData = Optional.empty();
                    }
                    return;
                }
            }

            // Need to select the type - find it in the list
            for (int i = 0; i < fileTypeList.length(); i++) {
                if (fileTypeList.getMCListItem(i).getObject().equals(targetType)) {
                    // Selecting the type will trigger file listing
                    // When files arrive, selectElement() will be called to select the file
                    fileTypeList.select(i);
                    return;
                }
            }

            // Type not found in list - might arrive later, keep initiaData
            return;
        }

        // No type specified, just clear initial data
        initialData = Optional.empty();
    }

    private void recursivelyStartDownloads(TreeMCListTableModel<String> model,
                                           TreeMCListItem<String> item,
                                           Path baseDirectory,
                                           Path relativePath) {
        if (item.isContainer()) {
            TreePath myPathOrFail = item.getMyPathOrFail();
            for (TreeMCListItem<String> i : model.getChildrenAtPath(myPathOrFail)) {
                recursivelyStartDownloads(model, i, baseDirectory, relativePath.resolve(Path.of(item.getObject())));
            }
        } else {
            protocol.getStream()
                    .downloadFile(new MSDownloadParams(context,
                                                       hashManager,
                                                       protocol.getDnsLookup(),
                                                       new MysterFileStub(serverAddress,
                                                                          getCurrentType(),
                                                                          item.getObject()),
                                                       baseDirectory,
                                                       relativePath,
                                                       DownloadStartErrorDialog.handler(this)));
        }
    }

    private Optional<Path> resolveDownloadDirectory(boolean alwaysAskForDirectory) {
        return DownloadDirectoryChooser
                .chooseWritableDownloadDirectory(this,
                                                 "Select a folder to save the file in",
                                                 alwaysAskForDirectory,
                                                 context.fileManager(),
                                                 getCurrentType());
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
    private static final String SERVER_KEY = "Server Public Key";

    /** Saves the identity as authoritative; the address is only its last known endpoint. */
    void savePreferences(Preferences p) {
        p.putByteArray(SERVER_KEY, serverIdentity.getPublicKey().getEncoded());
        p.put(IP_KEY, serverAddress.toString());
        if (getCurrentType() != null) {
            p.put(TYPE_KEY, getCurrentType().toHexString());
        } else {
            p.remove(TYPE_KEY);
        }
    }
    
    private void init() {
        savePrefs = context.keeper().addFrame(this, this::savePreferences,
                WINDOW_KEEPER_KEY, WindowPrefDataKeeper.MULTIPLE_WINDOWS);
        
        setLayout(new GridBagLayout());
        var builder = new com.general.util.GridBagBuilder()
            .withFill(GridBagConstraints.BOTH)
            .withInsets(new Insets(5, 5, 5, 5));

        statsPanel = MessagePanel.createNew("");
        statsPanel.setMinimumSize(new Dimension(150, 1));
        statsPanel.setPreferredSize(new Dimension(200, 1));
        statsPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));

        refreshButton = new JButton("Refresh");
        refreshButton.setIcon(IconLoader.loadSvg(ClientWindow.class, "connect-button"));
        refreshButton.setSize(SBXDEFAULT, GYDEFAULT);
        getRootPane().setDefaultButton(refreshButton);

        // Create toggle button for stats panel
        toggleStatsButton = new JButton("≡");
        toggleStatsButton.setToolTipText("Show/Hide Stats Panel");
        toggleStatsButton.setPreferredSize(new Dimension(30, refreshButton.getPreferredSize().height));
        toggleStatsButton.setFocusable(false);

        serverAddressField = new JTextField(serverAddress.toString());
        serverAddressField.setEditable(false);
        serverAddressField.setToolTipText("Server address. Open another connection to use a different server.");

        fileTypeList = MCListFactory.buildMCList(1, true, this);
        fileTypeList.sortBy(-1);
        fileTypeList.setColumnName(0, "Type");

        JMenuItem addTypeItem = new JMenuItem("Add this type");
        JPopupMenu typePopup = new JPopupMenu();
        typePopup.add(addTypeItem);
        addTypeItem.addActionListener(_ -> importSelectedType());
        ((javax.swing.JTable) fileTypeList).addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) { maybeShowTypePopup(e); }
            public void mousePressed(MouseEvent e)  { maybeShowTypePopup(e); }
            private void maybeShowTypePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = ((javax.swing.JTable) fileTypeList).rowAtPoint(e.getPoint());
                if (row < 0) return;
                fileTypeList.select(row);
                MysterType rowType = fileTypeList.getItem(row);
                boolean alreadyKnown = typeDescriptionList.get(rowType).isPresent();
                addTypeItem.setEnabled(!alreadyKnown);
                addTypeItem.setText(alreadyKnown ? "Type already added" : "Add this type");
                typePopup.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        fileList = TreeMCList.create(new String[]{"Name", "Size"}, new TreePathString(new String[] {}),
                (item, logicalSize) -> fileThumbnailController == null
                        ? Optional.empty()
                        : fileThumbnailController.lookup(item, logicalSize));
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JMenuItem downloadMenuItem = ContextMenu.createDownloadItem(fileList, _ -> {
            List<TreeMCListItem<String>> itemsToDownload = extractItemsFromSelection();
            
            if (itemsToDownload.isEmpty()) {
                return;
            }
            
            Optional<Path> baseDir = resolveDownloadDirectory(false);
            if (baseDir.isEmpty()) {
                return;
            }
            
            // Download each top-level item
            TreeMCListTableModel<String> model = (TreeMCListTableModel<String>) fileList.getModel();
            for (TreeMCListItem<String> treeItem : itemsToDownload) {
                recursivelyStartDownloads(model, treeItem, baseDir.get(), Path.of(""));
            }
        });
        JMenuItem downloadToMenuItem = ContextMenu.createDownloadToItem(fileList, _ -> {
            List<TreeMCListItem<String>> itemsToDownload = extractItemsFromSelection();
            
            if (itemsToDownload.isEmpty()) {
                return;
            }
            
            Optional<Path> baseDir = resolveDownloadDirectory(true);
            if (baseDir.isEmpty()) {
                return;
            }
            
            // Download each top-level item
            TreeMCListTableModel<String> model = (TreeMCListTableModel<String>) fileList.getModel();
            for (TreeMCListItem<String> treeItem : itemsToDownload) {
                recursivelyStartDownloads(model, treeItem, baseDir.get(), Path.of(""));
            }
        });
        JMenuItem bookmarkMenuItem = ContextMenu.createBookmarkServerItem(fileList, e -> {
            int index = fileList.getSelectedRow();
            if (index == -1) {
                return;
            }
        });
        JMenuItem jMenuItem = FileLinkCopyActionUtils.remoteItem(() -> this, context.linkService(), () -> {
            if (fileList.getSelectedIndexes().length != 1 || isSelectedFileListItemADir() || getCurrentType() == null
                    || getCurrentFile().isEmpty() || thumbnailAddress == null)
                return Optional.empty();
            return Optional.of(new FileLinkCopyActionUtils.RemoteFile(
                    new MysterFileStub(thumbnailAddress, getCurrentType(), getCurrentFile()),
                    Optional.of(serverIdentity.getPublicKey())));
        });

        // OPEN FILE ON DISK!
        ContextMenu.addPopUpMenu(fileList, () -> {
        }, downloadMenuItem, downloadToMenuItem, null, bookmarkMenuItem, jMenuItem);

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
        previewPane = new ClientFilePreviewPane(statsPanel);
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, previewPane);
        splitPane.setResizeWeight(1.0); // Give all extra space to left side
        previewPane.setVisible(false); // hide by default will collapse the right side
        
        // Set minimum size for left side to prevent it from collapsing
        leftPanel.setMinimumSize(new Dimension(400, 1));
        
        // Start with stats panel collapsed (divider all the way to the right)
        splitPane.setDividerLocation(1.0);
        
        // Add components to the frame: top row (address/refresh/toggle), middle (split pane), bottom (message)
        add(serverAddressField, builder.withGridLoc(0, 0).withSize(1, 1).withWeight(1, 0));
        add(refreshButton, builder.withGridLoc(1, 0).withSize(1, 1).withWeight(0, 0));
        add(toggleStatsButton, builder.withGridLoc(2, 0).withSize(1, 1).withWeight(0, 0));
        add(splitPane, builder.withGridLoc(0, 1).withSize(3, 1).withWeight(1, 1));
        add(msg, builder.withGridLoc(0, 2).withSize(3, 1).withWeight(1, 0));
        
        pack();

        Dimension preferredSize = refreshButton.getPreferredSize();
        preferredSize.width *= 2;
        fileTypeList.getPane().setPreferredSize(preferredSize);

        setResizable(true);
        setSize(XDEFAULT, YDEFAULT);

        // Toggle stats panel visibility
        toggleStatsButton.addActionListener(_ -> {
            boolean isVisible = previewPane.isVisible();
            previewPane.setVisible(!isVisible);
            if (!isVisible) {
                // Show the stats panel - set divider to 75% position
                splitPane.setDividerLocation(0.75);
            } else {
                // Hide the stats panel - move divider all the way right
                splitPane.setDividerLocation(1.0);
            }
            refreshThumbnailDemand();
        });

        installSelectionListeners();

        addWindowListener(new StandardWindowBehavior());
        previewPane.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                refreshThumbnailDemand();
            }
        });
        addWindowStateListener(_ -> refreshThumbnailDemand());
        addPropertyChangeListener("graphicsConfiguration", _ -> refreshThumbnailDemand());
    }


    /** Package protected for unit tests: wires real list events without creating a native frame. */
    void installSelectionListeners() {
        final ActionListener refreshButtonEvent = new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                initialData = Optional.empty();
                refresh();
            }
        };
        refreshButton.addActionListener(refreshButtonEvent);

        fileTypeList.addMCListEventListener(new MCListEventAdapter(){
            public void selectItem(MCListEvent e) {
                initialData.filter(data -> !data.type().equals(Optional.ofNullable(getCurrentType()))
                                   || data.fileName().isEmpty())
                           .ifPresent(_ -> initialData = Optional.empty());
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
                initialData.filter(data -> !data.fileName().equals(Optional.of(getCurrentFile())))
                           .ifPresent(_ -> initialData = Optional.empty());
                startStats();
                refreshThumbnailDemand();
            }

            public void unselectItem(MCListEvent e) {
                stopStats();
                refreshThumbnailDemand();
            }
        });
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
    }
        
    public void dispose() {
        if (fileThumbnailController != null) {
            fileThumbnailController.close();
        }
        if (previewController != null) {
            previewController.close();
        }
        initialData = Optional.empty();
        super.dispose();
        stopConnect();
    }

    public void addItemToTypeList(MysterType t) {
        String displayName = typeDescriptionList.get(t)
                .map(TypeDescription::getDescription)
                .orElseGet(() -> typeMetadataCache.getDisplayName(t));
        MutableSortableString nameCell = new MutableSortableString(displayName);
        typeDisplayNames.put(t, nameCell);
        fileTypeList.addItem(new GenericMCListItem<MysterType>(
                new Sortable[] { nameCell, new SortableString(t.toString()) }, t));
    }

    /**
     * Updates the display name of an existing type row in-place. Called when a transient name
     * resolution completes or immediately after a type is imported (so the row reflects the real
     * name without removal/re-insertion).
     *
     * <p>Safe to call even if the row is no longer present (e.g. after reconnect cleared the
     * list) — the missing-key case is a no-op.
     *
     * @param type the type whose row should be refreshed
     */
    public void refreshTypeDisplay(MysterType type) {
        MutableSortableString nameCell = typeDisplayNames.get(type);
        if (nameCell == null) return;
        String resolved = typeDescriptionList.get(type)
                .map(TypeDescription::getDescription)
                .orElseGet(() -> typeMetadataCache.getDisplayName(type));
        nameCell.setValue(resolved);
        fileTypeList.repaint();
        refreshThumbnailDemand();
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

            mkdir(localContainers, parentPath, currentTypeHandler);

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

    private static void mkdir(HashMap<TreePath, FolderMCListItem<String>> knownContainers, String[] p, FileTypeColumnHandler handler) {
        loopBackThroughParents(p, (current, parent) -> {
            if (knownContainers.containsKey(new TreePathString(current))) {
                return; // we've already done this one so skip
            }

            knownContainers
                    .put(new TreePathString(current),
                         new FolderMCListItem<String>(new TreePathString(parent),
                                                      extractDirElement(current[current.length - 1], handler),
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

    /**
     * Reconfigures the file list columns based on the currently selected type.
     * Called at the start of every file listing so the columns match the type handler
     * (e.g. MPG3 gets extra metadata columns; generic types get only Name and Size).
     */
    private void recolumnizeFileList() {
        currentTypeHandler = ClientInfoFactoryUtils.getHandler(typeDescriptionList, getCurrentType());
        int count = currentTypeHandler.getColumnCount();
        fileList.setNumberOfColumns(count);
        for (int i = 0; i < count; i++) {
            fileList.setColumnName(i, currentTypeHandler.getHeader(i));
            fileList.setColumnWidth(i, currentTypeHandler.getHeaderSize(i));
        }
        fileList.sortBy(0);
    }

    private ColumnSortable<String> extractFileRecordElement(final FileRecord file) {
        return currentTypeHandler.getFileItem(file);
    }

    private static ColumnSortable<String> extractDirElement(final String name, FileTypeColumnHandler handler) {
        return handler.getFolderItem(name);
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
        thumbnailAddress = address;
        MysterServer server = tracker.getQuickServerStats(address);

        String fallbackWindowName = address.getInetAddress().isLoopbackAddress() ? "myself" : serverAddress.toString();
        String windowName =
                (server == null ? fallbackWindowName : "\"" + server.getServerName() + "\" ("
                        + fallbackWindowName + ")");
        setTitle(CLIENT_WINDOW_TITLE_PREFIX + "to " + windowName);
        refreshThumbnailDemand();
    }

    /** Returns the current endpoint of this window's immutable server identity. */
    public MysterAddress getCurrentIP() {
        return serverAddress;
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

    private Optional<RemoteThumbnailCache.Request> buildThumbnailRequest() {
        if (thumbnailAddress == null || isSelectedFileListItemADir()) {
            return Optional.empty();
        }
        MysterType type = getCurrentType();
        if (type == null || getCurrentFile().isEmpty()) {
            return Optional.empty();
        }

        MetadataTypeId metadataTypeId = typeDescriptionList.get(type)
                .map(TypeDescription::getMetadataTypeId)
                .orElseGet(() -> typeMetadataCache.getMetadataTypeId(type).orElse(MetadataTypeId.GENERIC));
        if (!ThumbnailUiUtils.isEligible(metadataTypeId)) {
            return Optional.empty();
        }

        int size = ThumbnailUiUtils.devicePixelSize(
                previewPane.getThumbnailLogicalSide(),
                previewPane.getGraphicsConfiguration());
        if (size == 0) {
            return Optional.empty();
        }

        return Optional.of(new RemoteThumbnailCache.Request(thumbnailAddress, type, getCurrentFile(),
                                                            size, serverIdentity.getPublicKey()));
    }

    private void refreshThumbnailDemand() {
        if (previewController != null) {
            previewController.conditionalReload();
        }
        if (fileThumbnailController != null) {
            fileThumbnailController.reconcile();
        }
    }

    private Optional<RemoteThumbnailCache.Request> buildFileThumbnailRequest(
            TreeMCListItem<String> item, int logicalSize,
            java.awt.GraphicsConfiguration configuration, boolean allowResolution) {
        if (item.isContainer() || thumbnailAddress == null || listedType == null) {
            return Optional.empty();
        }

        Optional<MetadataTypeId> profile = typeDescriptionList.get(listedType)
                .map(TypeDescription::getMetadataTypeId);

        if (profile.isEmpty()) {
            profile = typeMetadataCache.getMetadataTypeId(listedType);
        }

        if (profile.isEmpty()) {
            if (allowResolution && !typeMetadataCache.hasAttempted(listedType)) {
                typeMetadataCache.resolveAsync(listedType, thumbnailAddress, serverIdentity.getPublicKey(),
                        () -> javax.swing.SwingUtilities.invokeLater(this::refreshThumbnailDemand));
            }
            return Optional.empty();
        }

        if (!ThumbnailUiUtils.isEligible(profile.get())) {
            return Optional.empty();
        }

        int size = ThumbnailUiUtils.devicePixelSize(logicalSize, configuration);
        if (size == 0) {
            return Optional.empty();
        }

        return Optional.of(new RemoteThumbnailCache.Request(thumbnailAddress,
                                                            listedType,
                                                            item.getObject(),
                                                            size,
                                                            serverIdentity.getPublicKey()));
    }
    
    public boolean isSelectedFileListItemADir() {
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
        thumbnailAddress = null;
        if (connectToThread != null) {
            connectToThread.flagToEnd();
        }
        fileTypeList.clearAll();
        typeDisplayNames.clear();
        if (thumbnailCache != null) {
            thumbnailCache.reset();
        }
        stopFileListing();
    }

    private void stopFileListing() {
        if (fileListThread != null) {
            fileListThread.flagToEnd();
            fileListThread = null;
        }
        listedType = null;
        fileList.clearAll();
        stopStats();
        containers.clear();
    }

    private void stopStats() {
        if (fileInfoListerThread != null) {
            fileInfoListerThread.flagToEnd();
        }
        statsPanel.setText("");
        refreshThumbnailDemand();
    }

    public void refresh() {
        Util.ensureEventDispatchThread();
        // Every refresh uses the constructor-bound identity, even with no pending navigation.
        PublicKey expectedKey = serverIdentity.getPublicKey();
        stopConnect();
        typeMetadataCache = new TypeMetadataCache(protocol.getStream());
        
        connectToThread = new TypeListerThread(
                protocol,
                new TypeListerThread.TypeListener() {
                    public void addItemToTypeList(MysterType s) {
                        ClientWindow.this.addItemToTypeList(s);
                    }

                    public void finished() {
                        selectTypeIfRequired();
                    }

                    public void failed(java.io.IOException error) {
                        initialData = Optional.empty();
                    }

                    public void refreshIP(MysterAddress address) {
                        ClientWindow.this.refreshIP(address);
                    }

                    public void refreshTypeDisplay(MysterType type) {
                        ClientWindow.this.refreshTypeDisplay(type);
                    }
                },
                this::say,
                getCurrentIP(),
                typeDescriptionList,
                typeMetadataCache,
                expectedKey);
        connectToThread.start();
        

        savePrefs.run();
    }

    private void selectTypeIfRequired() {
        initialData.ifPresent(data -> {
            if (data.type().isEmpty()) {
                initialData = Optional.empty();
                return;
            }

            MysterType requestedType = data.type().get();
            for (int i = 0; i < fileTypeList.length(); i++) {
                if (fileTypeList.getMCListItem(i).getObject().equals(requestedType)) {
                    fileTypeList.select(i);
                    return;
                }
            }

            initialData = Optional.empty();
            say("The requested type is not available on this server.");
        });
    }

    public void startFileList() {
        stopFileListing();
        listedType = getCurrentType();
        recolumnizeFileList();
        fileListThread = new FileListerThread(protocol.getStream(), Optional.of(serverIdentity.getPublicKey()), new FileListerThread.ItemListListener() {
                                                    public void addItemsToFileList(FileRecord[] files) {
                                                        ClientWindow.this.addItemsToFileList(files);
                                                    }
    
                                                    public void finish() {
                                                        // Mark the list complete before retrying, so a missing file is definitive.
                                                        fileListThread = null;
                                                        selectElement();
                                                    }
                                                    public void failed(java.io.IOException error) {
                                                        fileListThread = null;
                                                        initialData = Optional.empty();
                                                    }
                                              },
                                              this::say,
                                              getCurrentIP().toString(),
                                              getCurrentType(),
                                              t -> typeDescriptionList.get(t)
                                                      .map(TypeDescription::getDescription)
                                                      .orElse(t.toString()));
        fileListThread.start();
    }

    /**
     * Attempts to select the pending filename from the rows currently available.
     *
     * <p>Called on the EDT both by {@link #setInitialData(ClientWindowData)} for an already
     * selected type and by the file-list finish callback. These events run serially, but a
     * new destination can arrive between file batches, while the model is still incomplete.
     * The finish callback retries after all batches have been applied; canceled or replaced
     * destinations are ignored through the current {@code initialData} value.
     */
    private void selectElement() {
        if (initialData.isEmpty()) {
            return;
        }

        ClientWindowData data = initialData.get();
        
        if (data.fileName().isEmpty()) {
            return;
        }

        TreeMCListTableModel<String> model = (TreeMCListTableModel<String>) fileList.getModel();

        var foundElement = model.findElement(item -> !item.isContainer()
                && item.getObject().equals(data.fileName().get()));

        foundElement.ifPresent(item -> {
            model.ensureParentFoldersOpen(item);
            fileList.clearSelection();
            item.setSelected(true);
            
            fileList.selectAndScrollToItem(item);
            
            fileList.repaint();
        });
        
        // Finding the file completes the request immediately. If it is still missing while
        // listing is running, preserve initialData: clearing it would lose a destination that
        // a later batch may contain. finish() clears fileListThread before retrying, making
        // absence definitive; then clear the request and report the missing file only once.
        if (foundElement.isPresent() || fileListThread == null) {
            initialData = Optional.empty();
            if (foundElement.isEmpty()) say("The requested file is not available in this type.");
        }
    }

    public void startStats() {
        stopStats();
        fileInfoListerThread =
                new FileInfoListerThread(protocol,
                                         this::showFileStats,
                                         this::say,
                                         getCurrentIP(),
                                         getCurrentType(),
                                         getCurrentFile(),
                                         serverIdentity.getPublicKey());
        fileInfoListerThread.start();
    }
    

    /**
     * Imports the currently-selected type using the connected server as the access-list source.
     * No-op if nothing is selected or the type is already known.
     *
     * <p>Fetches the access list from the server, saves it, and enables the type. The
     * {@code typeEnabled} event propagates to all subscribers automatically. On any failure,
     * a themed error message is shown in the status bar.
     */
    private void importSelectedType() {
        int idx = fileTypeList.getSelectedIndex();
        if (idx < 0) return;
        MysterType type = fileTypeList.getItem(idx);
        if (typeDescriptionList.get(type).isPresent()) return;

        msg.say("Fetching type info...");
        MysterAddress address = serverAddress;
        PromiseFutures.execute(() -> {
            try (var socket = protocol.getStream()
                                      .makeStreamConnection(new ParamBuilder(address).withExpectedServerPublicKey(
                                              serverIdentity.getPublicKey()))) {
                return protocol.getStream().getAccessList(socket, type);
            }
        }).useEdt().addResultListener((Optional<AccessList> result) -> {
            if (result.isEmpty()) {
                msg.sayError("Server has no access list for this type.");
                return;
            }
            try {
                typeDescriptionList.importType(result.get());
                refreshTypeDisplay(type);
                String name = typeDescriptionList.get(type)
                                                 .map(TypeDescription::getDescription)
                                                 .orElse(type.toHexString());
                msg.say("Type '" + name + "' added.");
            } catch (IllegalArgumentException e) {
                msg.sayError("This type is already in your list.");
            } catch (Exception e) {
                msg.sayError("Could not save type: " + e.getMessage());
            }
        }).addExceptionListener(e -> {
            msg.sayError("Could not fetch type info: " + e.getMessage());
        });
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
