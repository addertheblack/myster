/*
 * Main.java
 * 
 * Title: Server Stats Window Test App Author: Andrew Trumper Description: An
 * app to test the server stats window
 */

package com.myster.server.ui;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.JTabbedPane;

import com.myster.net.client.MysterProtocol;
import com.myster.server.event.ServerContext;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowPrefDataKeeper;
import com.myster.ui.WindowPrefDataKeeper.PrefData;
import com.myster.util.Sayable;

public class ServerStatsWindow extends MysterFrame implements Sayable {
    private static final Logger log = Logger.getLogger(ServerStatsWindow.class.getName());
    
    private final JTabbedPane tab;
    private final DownloadInfoPanel downloadPanel;
    private final StatsInfoPanel statsinfopanel;

    private Runnable saveWindowData;

    public static final int XSIZE = 600;
    public static final int YSIZE = 400;
    public static final int TABYSIZE = 50;

    private static final String SELECTED_TAB = "Selected Tab";

    private static ServerStatsWindow singleton;
    private static com.myster.ui.WindowPrefDataKeeper keeper;

    private static ServerContext context;

    private static MysterFrameContext mysterFrameContext;

    private static MysterProtocol protocol;

    public static void init(ServerContext context, MysterFrameContext c, MysterProtocol protocol) {
        ServerStatsWindow.context = context;
        ServerStatsWindow.mysterFrameContext = c;
        ServerStatsWindow.protocol = protocol;
    }
    
    public synchronized static ServerStatsWindow getInstance() {
        if (singleton == null) {
            singleton = new ServerStatsWindow(ServerStatsWindow.mysterFrameContext);
        }
        return singleton;
    }

    private record StatsWindowData(int selectedTabIndex) {}
    
    public static int initWindowLocations(MysterFrameContext c) {
        List<PrefData<StatsWindowData>> lastLocs = c.keeper().getLastLocs("Server Stats", (p) -> {
            return new StatsWindowData(p.getInt(SELECTED_TAB, 0));
        });
        if (lastLocs.size() > 0) {
            Dimension d = singleton.getSize();
            PrefData<StatsWindowData> prefData = lastLocs.get(0);
            singleton.tab.setSelectedIndex(Math.min(singleton.tab.getTabCount()-1, prefData.data().selectedTabIndex()));
            singleton.setBounds(prefData.location().bounds());
//            singleton.setSize(d);
            singleton.setVisible(prefData.location().visible());
        }
        
        return lastLocs.size();
    }

    public void say(String s) {
        log.info(s);
    }

    protected ServerStatsWindow(MysterFrameContext c) {
        super(c, "Server Statistics");
        tab = new JTabbedPane();

        keeper = c.keeper();
        saveWindowData = keeper.addFrame(this, (p) -> {
            p.putInt(SELECTED_TAB, tab.getSelectedIndex());
        }, "Server Stats", WindowPrefDataKeeper.SINGLETON_WINDOW); //never remove.

        setResizable(false);

        downloadPanel = new DownloadInfoPanel(context, c, protocol);

        statsinfopanel = new StatsInfoPanel(context, c);
        
        tab.addTab("Downloads", downloadPanel);
        tab.addTab("Server Stats", statsinfopanel);

        initSelf();
        
        pack();
    }
    
    @Override
    public void show() {
        super.show();
        setSize(XSIZE + getInsets().right + getInsets().left, YSIZE
                + getInsets().top + getInsets().bottom + getJMenuBar().getSize().height);
    }

    private boolean inited = false;
    private void initSelf() {
        if (inited)
            return; //this should NEVER happen
        inited = true;
        
        setLayout(new CardLayout());
        tab.setLocation(0, 0);
        tab.setSize(XSIZE,   YSIZE);

        /*
         * tab.setOverlap(10); tab.addTab("Tab 1"); tab.addTab("Tab 2");
         * tab.addTab("Tab 4"); tab.addTab("Monkey bonus tab");
         */


        //tab.addTab("Outbound");
        //tab.addTab("Server Stats");
        //tab.addCustomTab("graphs.gif");
        //*/

        //if you want generic tabs use the thing below.
        //tab.addTab(new ServerTab(tab, null));
        //tab.addTab(new ServerTab(tab, null));
        //tab.addTab(new ServerTab(tab, null));

        add(tab);

        //downloadPanel=new DownloadInfoPanel();
        downloadPanel.setLocation(0, TABYSIZE);
        downloadPanel.setSize(XSIZE, YSIZE - TABYSIZE);

        downloadPanel.inited();

        //statsinfopanel=new StatsInfoPanel();
        statsinfopanel.setLocation(0, TABYSIZE);
        statsinfopanel.setSize(XSIZE, YSIZE - TABYSIZE);
        statsinfopanel.setVisible(false);
        //statsinfopanel.init();

        /*
         * graphinfopanel=new GraphInfoPanel();
         * graphinfopanel.setLocation(0+getInsets().right,
         * TABYSIZE+getInsets().top); graphinfopanel.setSize(XSIZE,
         * YSIZE-TABYSIZE); add(graphinfopanel); graphinfopanel.init();
         * graphinfopanel.setVisible(false); tab.addTabListener(new
         * TabHandler(2, graphinfopanel));
         */
        addWindowListener(new CloseBox());
        
        tab.addChangeListener(e-> {
            if (!isVisible()) {
                return;
            }
            saveWindowData.run();
        });
    }

    private class CloseBox extends WindowAdapter {
        public void windowClosing(WindowEvent e) {
            setVisible(false);
        }
    }
}
