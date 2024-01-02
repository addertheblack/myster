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
import com.myster.tracker.IpListManager;
import com.myster.tracker.MysterServer;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowLocationKeeper;
import com.myster.util.Sayable;

public class ClientWindow extends MysterFrame implements Sayable {
    private static final String ENTER_AN_IP_HERE = "Enter a server address";
    private static final int XDEFAULT = 600;
    private static final int YDEFAULT = 400;
    private static final int SBXDEFAULT = 72; //send button X default
    private static final int GYDEFAULT = 50; //Generic Y default
    private static final String WINDOW_KEEPER_KEY = "Myster's Client Windows";
    private static final String CLIENT_WINDOW_TITLE_PREFIX = "Direct Connection ";

    private static int counter = 0;
    private static WindowLocationKeeper keeper;
    private static IpListManager ipListManager;
    private static MysterProtocol protocol;
    private static HashCrawlerManager hashManager;
    
    private GridBagLayout gblayout;
    private GridBagConstraints gbconstrains;
    private JButton connect;
    private JTextField ipTextField;
    private MCList fileTypeList;
    private MCList fileList;
    private FileInfoPane pane;
    private String currentip;
    private JButton instant;
    private MessageField msg;
    private TypeListerThread connectToThread;
    private FileListerThread fileListThread;
    private FileInfoListerThread fileInfoListerThread;
    
    private boolean hasBeenShown = false;

    public static void init(MysterProtocol protocol, HashCrawlerManager hashManager, IpListManager ipListManager) {
        ClientWindow.protocol = protocol;
        ClientWindow.ipListManager = ipListManager;
        ClientWindow.hashManager = hashManager;
    }
    
    public static void initWindowLocations(MysterFrameContext c) {
        Rectangle[] rectangles = com.myster.ui.WindowLocationKeeper.getLastLocs(WINDOW_KEEPER_KEY);

        keeper = new WindowLocationKeeper(WINDOW_KEEPER_KEY);
        
        for (int i = 0; i < rectangles.length; i++) {
            ClientWindow window = new ClientWindow(c);
            window.setBounds(rectangles[i]);
            window.show();
        }
    }

    public ClientWindow(MysterFrameContext c) {
        super(c, "Direct Connection " + (++counter));

        init();

        keeper.addFrame(this);
    }

    public ClientWindow(MysterFrameContext c, String ip) {
        super(c, "Direct Connection " + (++counter));
        init();
        ipTextField.setText(ip);
        ipTextField.setForeground(Color.BLACK);
    }

    private void init() {
        setBackground(new Color(240, 240, 240));

        //Do interface setup:
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
                    com.myster.net.MysterAddress address = new com.myster.net.MysterAddress(ipTextField
                            .getText());
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

    public void addItemToTypeList(MysterType s) {
        fileTypeList.addItem(new GenericMCListItem(new Sortable[] { new SortableString(s.toString()) }, s));
    }

    public void addItemsToFileList(String[] files) {
        GenericMCListItem[] items = new GenericMCListItem[files.length];

        for (int i = 0; i < items.length; i++)
            items[i] = new GenericMCListItem(new Sortable[] { new SortableString(files[i]) },
                    files[i]);

        fileList.addItem(items);
    }

    public void refreshIP(final MysterAddress address) {
        MysterServer server = ipListManager.getQuickServerStats(address);

        String fallbackWindowName = currentip.equals("") ? "myself" : currentip;
        String windowName =
                (server == null ? fallbackWindowName : "\"" + server.getServerIdentity() + "\" ("
                        + currentip + ")");
        setTitle(CLIENT_WINDOW_TITLE_PREFIX + "to " + windowName);
    }

    //To be in an interface??
    public String getCurrentIP() {
        return currentip;
    }

    public MysterType getCurrentType() {
        int selectedIndex = fileTypeList.getSelectedIndex();

        if (selectedIndex != -1)
            return ((MysterType) (fileTypeList.getItem(selectedIndex)));

        return null;
    }

    public String getCurrentFile() {
        int selectedIndex = fileList.getSelectedIndex();

        if (selectedIndex == -1)
            return "";

        return (String) fileList.getItem(selectedIndex);
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
                                              getCurrentType());
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