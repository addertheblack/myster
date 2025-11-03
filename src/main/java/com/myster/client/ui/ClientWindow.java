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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.general.mclist.ColumnSortable;
import com.general.mclist.GenericMCListItem;
import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.general.mclist.MCListFactory;
import com.general.mclist.Sortable;
import com.general.mclist.SortableByte;
import com.general.mclist.SortableString;
import com.general.mclist.TreeMCList;
import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.general.mclist.TreeMCListTableModel.TreePath;
import com.general.mclist.TreeMCListTableModel.TreePathString;
import com.general.util.AnswerDialog;
import com.general.util.IconLoader;
import com.general.util.MessageField;
import com.general.util.MessagePanel;
import com.general.util.StandardWindowBehavior;
import com.general.util.Util;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.server.ServerPreferences;
import com.myster.search.HashCrawlerManager;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowLocationKeeper;
import com.myster.ui.WindowLocationKeeper.WindowLocation;
import com.myster.util.Sayable;

public class ClientWindow extends MysterFrame implements Sayable {
    private static final String ENTER_AN_IP_HERE = "Enter a server address";
    private static final String WINDOW_KEEPER_KEY = "Myster's Client Windows";
    private static final String CLIENT_WINDOW_TITLE_PREFIX = "Direct Connection ";
    
    private static final int XDEFAULT = 600;
    private static final int YDEFAULT = 400;
    private static final int SBXDEFAULT = 72; //send button X default
    private static final int GYDEFAULT = 50; //Generic Y default
    

    private static int counter = 0;
    private static Tracker tracker;
    private static MysterProtocol protocol;
    private static HashCrawlerManager hashManager;
    private static ServerPreferences serverPreferences;
    private static TypeDescriptionList typeDescriptionList;
    
    private JButton connect;
    private JTextField ipTextField;
    private MCList<MysterType> fileTypeList;
    private MCList<String> fileList;
    private JTextArea statsPanel;
    private String currentip;
    private JButton instant;
    private MessageField msg;
    private TypeListerThread connectToThread;
    private FileListerThread fileListThread;
    private FileInfoListerThread fileInfoListerThread;
    
    private boolean hasBeenShown = false;
    private MysterType type;
    
    private final MysterFrameContext context;

    public static void init(MysterProtocol protocol,
                            HashCrawlerManager hashManager,
                            Tracker tracker,
                            ServerPreferences prefs,
                            TypeDescriptionList typeDescriptionList) {
        ClientWindow.protocol = protocol;
        ClientWindow.tracker = tracker;
        ClientWindow.hashManager = hashManager;
        ClientWindow.typeDescriptionList = typeDescriptionList;
        serverPreferences = prefs;
    }
    
    public static int initWindowLocations(MysterFrameContext c) {
        WindowLocation[] lastLocs = c.keeper().getLastLocs(WINDOW_KEEPER_KEY);
        
        for (int i = 0; i < lastLocs.length; i++) {
            ClientWindow window = new ClientWindow(c);
            window.setBounds(lastLocs[i].bounds());
            window.show();
        }
        
        return lastLocs.length;
    }

    public ClientWindow(MysterFrameContext c) {
        super(c, "Direct Connection " + (++counter));
        
        context = c;

        init();

    }

    public ClientWindow(MysterFrameContext c, String ip) {
        super(c, "Direct Connection " + (++counter));
        
        context = c;
        
        init();
        ipTextField.setText(ip);
        ipTextField.setForeground(javax.swing.UIManager.getColor("TextField.foreground"));
    }
    
    public ClientWindow(MysterFrameContext c, String ip, MysterType type) {
        this(c, ip);
        this.type = type;
    }

    private void init() {
        context.keeper().addFrame(this, WINDOW_KEEPER_KEY, WindowLocationKeeper.MULTIPLE_WINDOWS);
        
        // Do interface setup:
        setLayout(new GridBagLayout());
        var builder = new com.general.util.GridBagBuilder()
            .withFill(GridBagConstraints.BOTH)
            .withInsets(new Insets(5, 5, 5, 5));

        statsPanel = MessagePanel.createNew("");
        statsPanel.setMinimumSize(new Dimension(1,1));
        statsPanel.setPreferredSize(new Dimension(1,1));
        statsPanel.setMaximumSize(new Dimension(1,1));

        connect = new JButton("Connect");
        connect.setIcon(IconLoader.loadSvg(ClientWindow.class, "connect-button"));
        connect.setSize(SBXDEFAULT, GYDEFAULT);
        getRootPane().setDefaultButton(connect);

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

        fileList.sortBy(0);
        
        msg = new MessageField("Idle...");

        instant = new JButton("Instant Message");
        instant.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    com.myster.net.MysterAddress address =
                            MysterAddress.createMysterAddress(ipTextField.getText());
                    com.myster.net.datagram.message.MessageWindow window =
                            new com.myster.net.datagram.message.MessageWindow(getMysterFrameContext(),
                                                                 protocol,
                                                                 address);
                    window.setVisible(true);
                } catch (java.net.UnknownHostException _) {
                    (new AnswerDialog(ClientWindow.this, "The address " + ipTextField.getText()
                            + " does not apear to be a valid internet address.")).answer();
                }
            }
        });

        add(connect, builder.withGridLoc(0, 0).withSize(1, 1).withWeight(0, 0));
        add(ipTextField, builder.withGridLoc(1, 0).withSize(1, 1).withWeight(1, 0));
        add(instant,
            builder.withGridLoc(2, 0)
                    .withSize(1, 1)
                    .withWeight(0, 0)
                    .withFill(GridBagConstraints.NONE)
                    .withAnchor(GridBagConstraints.WEST));
        add(fileTypeList.getPane(), builder.withGridLoc(0, 1).withSize(1, 1).withWeight(0, 1));
        add(fileList.getPane(), builder.withGridLoc(1, 1).withSize(1, 1).withWeight(1, 1));
        add(statsPanel, builder.withGridLoc(2, 1).withSize(1, 1).withWeight(1, 1));
        add(msg, builder.withGridLoc(0, 2).withSize(3, 1).withWeight(1, 0));

        fileList.getPane().setMinimumSize(new Dimension(1, 1));
        fileList.getPane().setPreferredSize(new Dimension(1, 1));
        fileList.getPane().setMaximumSize(new Dimension(1,1));
        
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

        fileTypeList.addMCListEventListener(new MCListEventAdapter(){
            public void selectItem(MCListEvent e) {
                startFileList();
            }

            public void unselectItem(MCListEvent e) {
                stopFileListing();
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
        fileList.setColumnWidth(0, 150);

        addWindowListener(new StandardWindowBehavior());
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
        fileTypeList
                .addItem(new GenericMCListItem<MysterType>(new Sortable[] {
                         new SortableString(typeDescriptionList.get(t)
                                .map(TypeDescription::getDescription).orElse(t.toString())),
                         new SortableString(t.toString()) }, t));

        if (t.equals(type)) {
            type = null;
            
            fileTypeList.select(fileTypeList.length()-1);
        }
    }

    public void addItemsToFileList(FileRecord[] files) {
        @SuppressWarnings("unchecked")
        TreeMCListItem<String>[] items = new TreeMCListItem[files.length];

        var containers = new HashMap<TreePath, TreeMCListItem<String>>();
        
        for (int i = 0; i < files.length; i++) {
            var parentPath = extractParentPath(extractPath(files[i]));
            
            var path = new TreePathString(parentPath);

            mkdir(containers, parentPath);

            final var file = files[i];
            
            items[i] = new TreeMCListItem<String>(path, extractFileRecordElement(file), Optional.empty());
        }

        fileList.addItem(items);
        fileList.addItem(containers.values().toArray(new TreeMCListItem[] {}));
    }

    private void mkdir(HashMap<TreePath, TreeMCListItem<String>> containers, String[] p) {
        // TreePathString(..) need to have the path[0..length-1]
        // added as containers first
        for (String[] parentPath = p; parentPath.length > 0; ) {
            if (containers.containsKey(new TreePathString(parentPath))) {
                return; // we've already done this one so skip
            }
            
            var temp = extractParentPath(parentPath);
            
            containers
                    .put(new TreePathString(parentPath),
                         new TreeMCListItem<String>(new TreePathString(temp),
                                                    extractDirElement(parentPath[parentPath.length
                                                            - 1]),
                                                    Optional.of(new TreePathString(parentPath))));
            parentPath = temp;
        }
    }

    private ColumnSortable<String> extractFileRecordElement(final FileRecord file) {
        return new ColumnSortable<String>() {
            public Sortable getValueOfColumn(int column) {
                return new Sortable[] { new SortableString(file.file()),
                        new SortableByte(file.metaData()
                                .getLong("/size")
                                .orElse((long) 0)) }[column];
            }

            public String getObject() {
                return file.file();
            }
        };
    }
    
    private ColumnSortable<String> extractDirElement(final String name) {
        return new ColumnSortable<String>() {
            public Sortable getValueOfColumn(int column) {
                return new Sortable[] { new SortableString(name),
                        new SortableByte(-2) }[column];
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

    public void say(String s) {
        msg.say(s);
    }

    public MessageField getMessageField() {
        return msg;
    }

    public void showFileStats(Map<String, String> k) {
        var builder = new StringBuilder();
        for (Entry<String, String> entry : k.entrySet()) {
            if (entry.getKey().equals("size")) { // hack to
                // show
                // size as
                // bytes string
                // like
                // XXXbytes or
                // XXXMB
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
                new TypeListerThread(ClientWindow.protocol, new TypeListerThread.TypeListener() {
                    public void addItemToTypeList(MysterType s) {
                        ClientWindow.this.addItemToTypeList(s);
                    }

                    public void refreshIP(MysterAddress address) {
                        ClientWindow.this.refreshIP(address);
                    }
                }, this::say, getCurrentIP());
        connectToThread.start();
    }

    public void startFileList() {
        stopFileListing();
        fileListThread = new FileListerThread(this::addItemsToFileList,
                                              this::say,
                                              getCurrentIP(),
                                              getCurrentType(),
                                              t -> typeDescriptionList.get(t)
                                                      .map(TypeDescription::getDescription)
                                                      .orElse(t.toString()));
        fileListThread.start();
    }

    public void startStats() {
        stopStats();
        fileInfoListerThread =
                new FileInfoListerThread(ClientWindow.protocol,
                                         this::showFileStats,
                                         this::say,
                                         getCurrentIP(),
                                         getCurrentType(),
                                         getCurrentFile());
        fileInfoListerThread.start();
    }
}
