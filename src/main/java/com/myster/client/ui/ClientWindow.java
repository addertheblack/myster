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

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JTextField;

import com.general.mclist.GenericMCListItem;
import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.general.mclist.MCListFactory;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.general.util.AnswerDialog;
import com.general.util.MessageField;
import com.general.util.StandardWindowBehavior;
import com.general.util.Util;
import com.myster.client.net.MysterProtocol;
import com.myster.net.MysterAddress;
import com.myster.search.HashCrawlerManager;
import com.myster.server.ServerPreferences;
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
    
    private GridBagLayout gblayout;
    private GridBagConstraints gbconstrains;
    private JButton connect;
    private JTextField ipTextField;
    private MCList<MysterType> fileTypeList;
    private MCList<String> fileList;
    private FileInfoPane pane;
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
        ipTextField.setForeground(Color.BLACK);
    }
    
    public ClientWindow(MysterFrameContext c, String ip, MysterType type) {
        this(c, ip);
        this.type = type;
    }

    private void init() {
        context.keeper().addFrame(this, WINDOW_KEEPER_KEY, WindowLocationKeeper.MULTIPLE_WINDOWS);
        
        setBackground(new Color(240, 240, 240));

        // Do interface setup:
        gblayout = new GridBagLayout();
        setLayout(gblayout);
        gbconstrains = new GridBagConstraints();
        gbconstrains.fill = GridBagConstraints.BOTH;
        gbconstrains.insets = new Insets(5, 5, 5, 5);
        gbconstrains.ipadx = 1;
        gbconstrains.ipady = 1;

        pane = new FileInfoPane();
        pane.setSize(XDEFAULT / 3, YDEFAULT - 40);

        connect = new JButton("Connect");
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
        ipTextField.setForeground(Color.GRAY);
        ipTextField.addFocusListener(new FocusListener() {
            @Override
            public void focusLost(FocusEvent e) {
                if (ipTextField.getText().equals("")) {
                    ipTextField.setText(ENTER_AN_IP_HERE);
                    ipTextField.setForeground(Color.GRAY);
                }
            }
            
            @Override
            public void focusGained(FocusEvent e) {
                if (ipTextField.getText().equals("")) {
                    ipTextField.setForeground(Color.BLACK);
                    ipTextField.setText("");
                }
            }
        });

        fileTypeList = MCListFactory.buildMCList(1, true, this);
        fileTypeList.sortBy(-1);
        fileTypeList.setColumnName(0, "Type");
        
        fileList = MCListFactory.buildMCList(1, true, this);
        fileList.sortBy(-1);
        fileList.setColumnName(0, "Files");
        //fileList.setColumnWidth(0, 300);

        msg = new MessageField("Idle...");
        msg.setSize(XDEFAULT, GYDEFAULT);

        instant = new JButton("Instant Message");
        instant.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    com.myster.net.MysterAddress address =
                            MysterAddress.createMysterAddress(ipTextField.getText());
                    com.myster.message.MessageWindow window =
                            new com.myster.message.MessageWindow(getMysterFrameContext(),
                                                                 protocol,
                                                                 address);
                    window.setVisible(true);
                } catch (java.net.UnknownHostException ex) {
                    (new AnswerDialog(ClientWindow.this, "The address " + ipTextField.getText()
                            + " does not apear to be a valid internet address.")).answer();
                }
            }
        });

        //reshape(0, 0, XDEFAULT, YDEFAULT);

        addComponent(connect, 0, 0, 1, 1, 1, 0);
        addComponent(ipTextField, 0, 1, 2, 1, 6, 0);
        addComponent(instant, 0, 3, 1, 1, 5, 0);
        addComponent(fileTypeList.getPane(), 1, 0, 1, 1, 1, 99);
        addComponent(fileList.getPane(), 1, 1, 2, 1, 6, 99);
        addComponent(pane, 1, 3, 1, 1, 5, 99);
        addComponent(msg, 2, 0, 4, 1, 99, 0);

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
    
    private void addComponent(Component c, int row, int column, int width, int height, int weightx,
            int weighty) {
        gbconstrains.gridx = column;
        gbconstrains.gridy = row;

        gbconstrains.gridwidth = width;
        gbconstrains.gridheight = height;

        gbconstrains.weightx = weightx;
        gbconstrains.weighty = weighty;

        gblayout.setConstraints(c, gbconstrains);

        add(c);
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

    public void addItemsToFileList(String[] files) {
        @SuppressWarnings("unchecked")
        GenericMCListItem<String>[] items = new GenericMCListItem[files.length];

        for (int i = 0; i < items.length; i++)
            items[i] = new GenericMCListItem<String>(new Sortable[] { new SortableString(files[i]) },
                    files[i]);

        fileList.addItem(items);
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
        pane.display(k);
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
        pane.clear();
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