/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.search.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JTextField;

import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.general.mclist.MCListFactory;
import com.general.mclist.MCListItemInterface;
import com.general.util.MessageField;
import com.general.util.StandardWindowBehavior;
import com.myster.client.net.MysterProtocol;
import com.myster.search.HashCrawlerManager;
import com.myster.search.SearchEngine;
import com.myster.search.SearchResult;
import com.myster.search.SearchResultListener;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowLocationKeeper;
import com.myster.util.Sayable;
import com.myster.util.TypeChoice;

public class SearchWindow extends MysterFrame implements SearchResultListener, Sayable {
    private static final Logger LOGGER = Logger.getLogger(SearchWindow.class.getName());
    private static final int XDEFAULT = 640;
    private static final int YDEFAULT = 400;
    private static final String PREF_LOCATION_KEY = "Search Window";

    private static WindowLocationKeeper keeper;

    private static int counter = 0;
    
    private final GridBagLayout gblayout;
    private final GridBagConstraints gbconstrains;

    private final JButton searchButton;
    private final MCList fileList;
    private final JTextField textEntry;
    private final TypeChoice choice;
    private final MessageField msg;
    
    private SearchEngine searchEngine;
    private ClientHandleObject metaDateHandler;

    public SearchWindow(MysterFrameContext c) {
        super(c, "Search Window " + (++counter));

        setBackground(new Color(240, 240, 240));

        //Do interface setup:
        gblayout = new GridBagLayout();
        setLayout(gblayout);
        gbconstrains = new GridBagConstraints();
        gbconstrains.fill = GridBagConstraints.BOTH;
        gbconstrains.insets = new Insets(5, 5, 5, 5);
        gbconstrains.ipadx = 1;
        gbconstrains.ipady = 1;

        searchButton = new JButton("Search") {
            public Dimension getPreferredSize() {
                return new Dimension(Math.max(75, super.getPreferredSize().width), super
                        .getPreferredSize().height);
            }

            public Dimension getMinimumSize() {
                return getPreferredSize();
            }
        };

        searchButton.setSize(50, 25);
        getRootPane().setDefaultButton(searchButton);

        textEntry = new JTextField("", 1);
        textEntry.setEditable(true);

        choice = new TypeChoice(c.tdList());

        fileList = MCListFactory.buildMCList(1, true, this);
        fileList.getPane().setSize(XDEFAULT, YDEFAULT);

        msg = new MessageField("Idle...");

        addComponent(textEntry, 0, 0, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL);
        addComponent(choice, 0, 1, 1, 1, 0, 0, GridBagConstraints.HORIZONTAL);
        addComponent(searchButton, 0, 2, 1, 1, 0, 0, GridBagConstraints.HORIZONTAL);
        addComponent(fileList.getPane(), 1, 0, 4, 1, 1, 1, GridBagConstraints.BOTH);
        addComponent(msg, 2, 0, 4, 1, 1, 0, GridBagConstraints.HORIZONTAL);

        setResizable(true);
        setSize(XDEFAULT, YDEFAULT);

        SearchButtonHandler searchButtonHandler = new SearchButtonHandler(this, searchButton);
        searchButton.addActionListener(searchButtonHandler);

        textEntry.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                startSearch();
            }
        });

        fileList.addMCListEventListener(new MCListEventAdapter() {
            public synchronized void doubleClick(MCListEvent a) {
                MCList list = a.getParent();
                downloadFile(list.getItem(list.getSelectedIndex()));
            }
        });

        addWindowListener(new StandardWindowBehavior());
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                stopSearch();
            }
        });

        fileList.setColumnName(0, "Search Results appear here");
        fileList.setColumnWidth(0, 400);

        keeper.addFrame(this);

        textEntry.setSelectionStart(0);
        textEntry.setSelectionEnd(textEntry.getText().length());

        pack();
    }

    public void show() {
        super.show();
        pack();
    }
    
    private static HashCrawlerManager hashManager;
    private static MysterProtocol protocol;
    private static Tracker manager;
    
    public static void init(MysterProtocol protocol, HashCrawlerManager hashManager, Tracker manager) {
        SearchWindow.protocol = protocol;
        SearchWindow.hashManager = hashManager;
        SearchWindow.manager = manager;
    }
    
    public static int initWindowLocations(MysterFrameContext c) {
        Rectangle[] rectangles = WindowLocationKeeper.getLastLocs(PREF_LOCATION_KEY);

        keeper = new WindowLocationKeeper(PREF_LOCATION_KEY);

        for (int i = 0; i < rectangles.length; i++) {
            SearchWindow window = new SearchWindow(c);
            window.setBounds(rectangles[i]);
            window.setVisible(true);
        }

        return rectangles.length;
    }

    public void addComponent(Component component, int row, int column, int width, int height,
            int weightx, int weighty, int fill) {
        gbconstrains.gridx = column;
        gbconstrains.gridy = row;

        gbconstrains.gridwidth = width;
        gbconstrains.gridheight = height;

        gbconstrains.weightx = weightx;
        gbconstrains.weighty = weighty;

        gbconstrains.fill = fill;

        gblayout.setConstraints(component, gbconstrains);

        super.add(component);

    }

    public void searchStart() {
        searchButton.setText("Stop");
        msg.say("Clearing File List...");
        fileList.clearAll();
        recolumnize();
    }

    public void searchOver() {
        msg.say("Search done. " + fileList.length() + " file" + (fileList.length() == 0 ? "" : "s")
                + " found...");
        searchButton.setText("Search");
    }

    void startSearch() {
        if (searchEngine != null) {
            stopSearch();
        }
        searchEngine = new SearchEngine(protocol,
                                        hashManager,
                                        getMysterFrameContext(),
                                        manager,
                                        this,
                                        this,
                                        this.getMysterType(),
                                        this.getSearchString());
        searchEngine.run();
        setTitle("Search for \"" + getSearchString() + "\"");
    }

    void stopSearch() {
        if (searchEngine == null) {
            return;
        }

        searchEngine.flagToEnd();
        searchEngine = null;
    }

    public void recolumnize() {
        metaDateHandler = ClientInfoFactoryUtilities.getHandler(getMysterType());
        int max = metaDateHandler.getNumberOfColumns();
        fileList.setNumberOfColumns(max);

        for (int i = 0; i < max; i++) {
            fileList.setColumnName(i, metaDateHandler.getHeader(i));
            fileList.setColumnWidth(i, metaDateHandler.getHeaderSize(i));
        }
    }

    public boolean addSearchResults(SearchResult[] resultArray) {
        MCListItemInterface[] m = new MCListItemInterface[resultArray.length];

        for (int i = 0; i < resultArray.length; i++) {
            m[i] = metaDateHandler.getMCListItem(resultArray[i]);
        }

        fileList.addItem(m);
        return true;
    }

    public void searchStats(SearchResult s) {
        //? dunno.
    }

    public String getSearchString() {
        return textEntry.getText();
    }

    public MysterType getMysterType() {
        return choice.getType();
    }

    public static void downloadFile(Object s) {
        ((SearchResult) (s)).download();
    }

    public void say(String s) {
        LOGGER.fine(s);
        msg.say("" + s);
    }
}