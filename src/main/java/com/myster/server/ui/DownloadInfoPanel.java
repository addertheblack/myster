/*
 * Main.java
 * 
 * Title: Server Stats Window Test App Author: Andrew Trumper Description: An app to test the server
 * stats window
 */

package com.myster.server.ui;

import java.awt.Color;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JPanel;

import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventListener;
import com.general.mclist.MCListFactory;
import com.general.mclist.MCListItemInterface;
import com.general.util.Timer;
import com.myster.client.ui.ClientWindow;
import com.myster.message.MessageWindow;
import com.myster.server.event.ConnectionManagerEvent;
import com.myster.server.event.ConnectionManagerListener;
import com.myster.server.event.ServerContext;
import com.myster.server.event.ServerDownloadDispatcher;
import com.myster.server.stream.FileSenderThread;
import com.myster.ui.MysterFrameContext;

public class DownloadInfoPanel extends JPanel {
    private MCList list;

    private JButton disconnect, browse, clearAll, message;

    private final ServerContext serverContext;
    private final ConnectionHandler chandler;
    private final MysterFrameContext frameContext;

    public DownloadInfoPanel(ServerContext context, MysterFrameContext c) {
        this.frameContext = c;
        this.serverContext = context;

        setBackground(new Color(240, 240, 240));
        chandler = new ConnectionHandler();
    }

    public void inited() {
        setLayout(new GridBagLayout());
        
        Insets insets = new Insets( 2,2,2,2 );
        
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.insets = insets;
        

        list = MCListFactory.buildMCList(6, false, this);

        Container p = list.getPane();

        p.setSize(590, 300);
        p.setLocation(5, 10);

        add(p, constraints);

        list.setNumberOfColumns(5);

        list.setColumnName(0, "User");
        list.setColumnName(1, "File");
        list.setColumnName(2, "Status");
        list.setColumnName(3, "Progress");
        list.setColumnName(4, "Size");

        list.setColumnWidth(0, 150);
        list.setColumnWidth(1, 215);
        list.setColumnWidth(2, 70);
        list.setColumnWidth(3, 70);
        list.setColumnWidth(4, 70);

        list.addMCListEventListener(chandler.new DownloadStatsMCListEventHandler());

        p.doLayout();

        ButtonPanel panel = new ButtonPanel();

        browse = new JButton("Browse Files");
        panel.add(browse);
        browse.addActionListener(chandler.new ConnectHandler());

        message = new JButton("Instant Message");
        panel.add(message);
        message.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int[] array = list.getSelectedIndexes();
                for (int i = 0; i < array.length; i++) {
                    try {
                        MessageWindow window = new MessageWindow(frameContext, new com.myster.net.MysterAddress(
                                (((DownloadMCListItem) (list.getMCListItem(array[i]))))
                                        .getAddress()));
                        window.setVisible(true);
                    } catch (java.net.UnknownHostException ex) {

                    }
                }

            }
        });

        disconnect = new JButton("Disconnect User");
        panel.add(disconnect);
        disconnect.addActionListener(chandler.new DisconnectHandler());

        clearAll = new JButton("Clear All Done");
        clearAll.setSize(175, 25);
        clearAll.setLocation(10, 315);
        panel.add(clearAll);
        clearAll.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                for (int i = 0; i < list.length(); i++) {
                    MCListItemInterface item = list.getMCListItem(i);
                    if (((DownloadMCListItem) item).isDone()) {
                        list.removeItem(item);
                        i--;
                    }
                }
            }

        });

        constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.insets = insets;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
//        panel.setSize(590, 25);
//        panel.setLocation(5, 317);
        add(panel, constraints);
        panel.doLayout();

        Timer timer = new Timer(new RepaintLoop(), 10000);

        buttonsEnable(list.isAnythingSelected());
        
        serverContext.addConnectionManagerListener(chandler);
    }

    private Image doubleBuffer; //adds double buffering

    public void update(Graphics g) {
        if (doubleBuffer == null) {
            doubleBuffer = createImage(600, 400);
        }
        Graphics graphics = doubleBuffer.getGraphics();
        paint(graphics);
        g.drawImage(doubleBuffer, 0, 0, this);
    }

    public void buttonsEnable(boolean enable) {
        disconnect.setEnabled(enable);
        browse.setEnabled(enable);
        message.setEnabled(enable);
    }

    private class ConnectionHandler extends ConnectionManagerListener {

        public void sectionEventConnect(ConnectionManagerEvent e) {
            if ((e.getSection() == FileSenderThread.NUMBER)
                    || (e.getSection() == com.myster.server.stream.MultiSourceSender.SECTION_NUMBER)) {
                ServerDownloadDispatcher d = (ServerDownloadDispatcher) e.getSectionObject();
                DownloadMCListItem i = new DownloadMCListItem(d);
                list.addItem(i);
                i.setUser(e.getAddress() == null ? "?" : "" + e.getAddress());
            }
        }

        public void sectionEventDisconnect(ConnectionManagerEvent e) {
            if (e.getSection() == FileSenderThread.NUMBER
                    || (e.getSection() == com.myster.server.stream.MultiSourceSender.SECTION_NUMBER)) {
                ServerDownloadDispatcher d = (ServerDownloadDispatcher) e.getSectionObject();
                int index = getIndexOfDispatcher(d);
                if (index == -1) {
                    System.out.println("Couldn't find this dispatcher.. weird..");
                    return;
                }
                DownloadMCListItem downloadMCListItem = (DownloadMCListItem) list
                        .getMCListItem(index);
                if (downloadMCListItem.isTrivialDownload()) {
                    list.removeItem(downloadMCListItem);
                }
            }
        }

        private int getIndexOfDispatcher(ServerDownloadDispatcher dispatcher) {
            for (int i = 0; i < list.length(); i++) {
                if (list.getItem(i) == dispatcher)
                    return i;
            }
            return -1;
        }

        public void disconnectSelected() {
            int[] array = list.getSelectedIndexes();
            for (int i = 0; i < array.length; i++) {
                ((DownloadMCListItem) (list.getMCListItem(array[i]))).disconnectClient(); //isn't
                // this a
                // great
                // line or
                // what?
            }
        }

        public void newConnectWindow() {
            int[] array = list.getSelectedIndexes();
            for (int i = 0; i < array.length; i++) {
                (new ClientWindow(frameContext, ""
                        + (((DownloadMCListItem) (list.getMCListItem(array[i])))).getAddress()))
                        .show();
            }
        }

        public class DisconnectHandler implements ActionListener {

            public void actionPerformed(ActionEvent e) {
                disconnectSelected();
            }
        }

        public class ConnectHandler implements ActionListener {

            public void actionPerformed(ActionEvent e) {
                newConnectWindow();
            }
        }

        public class DownloadStatsMCListEventHandler implements MCListEventListener {
            public void doubleClick(MCListEvent e) {
                newConnectWindow();
            }

            public void selectItem(MCListEvent e) {
                buttonsEnable(e.getParent().isAnythingSelected());
            }

            public void unselectItem(MCListEvent e) {
                buttonsEnable(e.getParent().isAnythingSelected());
            }
        }

    }

    private class RepaintLoop implements Runnable {
        public void run() {
            list.repaint();
            Timer timer = new Timer(this, 700);
        }
    }

    private static class ButtonPanel extends JPanel {
        public ButtonPanel() {
            setLayout(new FlowLayout( FlowLayout.LEFT));//new GridLayout(1, 4, 7, 7));
        }
    }
}