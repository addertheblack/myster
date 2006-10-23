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
import com.general.util.KeyValue;
import com.general.util.MessageField;
import com.general.util.StandardWindowBehavior;
import com.general.util.Util;
import com.myster.net.MysterAddress;
import com.myster.tracker.IPListManagerSingleton;
import com.myster.tracker.MysterServer;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrame;
import com.myster.ui.WindowLocationKeeper;
import com.myster.util.Sayable;

public class ClientWindow extends MysterFrame implements Sayable {
    private static final int XDEFAULT = 600;

    private static final int YDEFAULT = 400;

    private static final int SBXDEFAULT = 72; //send button X default

    private static final int GYDEFAULT = 50; //Generic Y default

    private static int counter = 0;

    private static final String WINDOW_KEEPER_KEY = "Myster's Client Windows";

    private static final String CLIENT_WINDOW_TITLE_PREFIX = "Direct Connection ";
    
    private static WindowLocationKeeper keeper;
    
    private GridBagLayout gblayout;

    private GridBagConstraints gbconstrains;

    private JButton connect;

    private JTextField IP;

    private MCList fileTypeList;

    private MCList fileList;

    private FileInfoPane pane;

    private String currentip;

    private JButton instant;

    private MessageField msg;

    private TypeListerThread connectToThread;

    private FileListerThread fileListThread;

    private FileInfoListerThread fileInfoListerThread;

    public static void initWindowLocations() {
        Rectangle[] rectangles = com.myster.ui.WindowLocationKeeper.getLastLocs(WINDOW_KEEPER_KEY);

        keeper = new WindowLocationKeeper(WINDOW_KEEPER_KEY);
        
        for (int i = 0; i < rectangles.length; i++) {
            ClientWindow window = new ClientWindow();
            window.setBounds(rectangles[i]);
            window.show();
        }
    }

    public ClientWindow() {
        super("Direct Connection " + (++counter));

        init();

        keeper.addFrame(this);
    }

    public ClientWindow(String ip) {
        super("Direct Connection " + (++counter));
        init();
        IP.setText(ip);
        //connect.dispatchEvent(new KeyEvent(connect, KeyEvent.KEY_PRESSED,
        // System.currentTimeMillis(), 0, KeyEvent.VK_ENTER,
        // (char)KeyEvent.VK_ENTER));
        //connect.dispatchEvent(new KeyEvent(connect, KeyEvent.KEY_RELEASED,
        // System.currentTimeMillis(), 0, KeyEvent.VK_ENTER,
        // (char)KeyEvent.VK_ENTER));
        connect.dispatchEvent(new ActionEvent(connect, ActionEvent.ACTION_PERFORMED,
                "Connect Button"));
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

        IP = new JTextField("Enter an IP here");
        IP.setEditable(true);

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
                    com.myster.net.MysterAddress address = new com.myster.net.MysterAddress(IP
                            .getText());
                    com.myster.message.MessageWindow window = new com.myster.message.MessageWindow(
                            address);
                    window.setVisible(true);
                } catch (java.net.UnknownHostException ex) {
                    (new AnswerDialog(ClientWindow.this, "The address " + IP.getText()
                            + " does not apear to be a valid internet address.")).answer();
                }
            }
        });

        //reshape(0, 0, XDEFAULT, YDEFAULT);

        addComponent(connect, 0, 0, 1, 1, 1, 0);
        addComponent(IP, 0, 1, 2, 1, 6, 0);
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
        IP.addActionListener(connectButtonEvent);

        fileTypeList.addMCListEventListener(new MCListEventAdapter(){
            public void selectItem(MCListEvent e) {
                startFileList();
            }

            public void unselectItem(MCListEvent e) {
                stopFileListing();
            }
        });
        fileList.addMCListEventListener(new FileListAction(this));
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

    public void addItemToTypeList(String s) {
        fileTypeList.addItem(new GenericMCListItem(new Sortable[] { new SortableString(s) }, s));
    }

    public void addItemsToFileList(String[] files) {
        GenericMCListItem[] items = new GenericMCListItem[files.length];

        for (int i = 0; i < items.length; i++)
            items[i] = new GenericMCListItem(new Sortable[] { new SortableString(files[i]) },
                    files[i]);

        fileList.addItem(items);
    }

    public void refreshIP(final MysterAddress address) {
        Util.invokeLater(new Runnable() {
            public void run() {
                MysterServer server = IPListManagerSingleton.getIPListManager()
                        .getQuickServerStats(address);

                setTitle(CLIENT_WINDOW_TITLE_PREFIX + "to \""
                        + (server == null ? currentip : server.getServerIdentity()) + "\"");
            }
        });
    }

    //To be in an interface??
    public String getCurrentIP() {
        return currentip;
    }

    public MysterType getCurrentType() {
        int selectedIndex = fileTypeList.getSelectedIndex();

        if (selectedIndex != -1)
            return new MysterType(((String) (fileTypeList.getItem(selectedIndex))).getBytes());

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

    public void showFileStats(KeyValue k) {
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
        currentip = IP.getText();
        connectToThread = new TypeListerThread(this);
        connectToThread.start();
    }

    public void startFileList() {
        stopFileListing();
        fileListThread = new FileListerThread(this);
        fileListThread.start();
    }

    public void startStats() {
        stopStats();
        fileInfoListerThread = new FileInfoListerThread(this);
        fileInfoListerThread.start();
    }
}