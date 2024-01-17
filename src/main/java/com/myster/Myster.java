/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2004
 */

package com.myster;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.awt.EventQueue;
import java.awt.Frame;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.general.application.ApplicationContext;
import com.general.application.ApplicationSingletonListener;
import com.general.net.AsyncDatagramSocket;
import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.application.MysterGlobals;
import com.myster.bandwidth.BandwidthManager;
import com.myster.client.datagram.MysterDatagramImpl;
import com.myster.client.datagram.UDPPingClient;
import com.myster.client.net.MysterProtocol;
import com.myster.client.net.MysterProtocolImpl;
import com.myster.client.stream.MysterStreamImpl;
import com.myster.client.ui.ClientWindow;
import com.myster.filemanager.FileTypeListManager;
import com.myster.filemanager.ui.FmiChooser;
import com.myster.hash.HashManager;
import com.myster.message.ImTransactionServer;
import com.myster.message.MessageWindow;
import com.myster.message.ui.MessagePreferencesPanel;
import com.myster.net.DatagramProtocolManager;
import com.myster.pref.MysterPreferences;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MultiSourceHashSearch;
import com.myster.search.ui.SearchWindow;
import com.myster.server.BannersManager.BannersPreferences;
import com.myster.server.ServerFacade;
import com.myster.server.datagram.FileStatsDatagramServer;
import com.myster.server.datagram.SearchDatagramServer;
import com.myster.server.datagram.SearchHashDatagramServer;
import com.myster.server.datagram.ServerStatsDatagramServer;
import com.myster.server.datagram.TopTenDatagramServer;
import com.myster.server.datagram.TypeDatagramServer;
import com.myster.server.event.ServerEventDispatcher;
import com.myster.server.ui.ServerStatsWindow;
import com.myster.tracker.IpListManager;
import com.myster.tracker.MysterIpPoolImpl;
import com.myster.tracker.ui.TrackerWindow;
import com.myster.transaction.TransactionManager;
import com.myster.type.ui.TypeManagerPreferencesGUI;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.PreferencesGui;
import com.myster.ui.WindowManager;
import com.myster.ui.menubar.MysterMenuBar;
import com.myster.ui.menubar.event.MenuBarEvent;
import com.myster.ui.menubar.event.MenuBarListener;
import com.myster.ui.tray.MysterTray;
import com.myster.util.I18n;
import com.simtechdata.waifupnp.UPnP;

public class Myster {
    private static final Logger LOGGER = Logger.getLogger(AsyncDatagramSocket.class.getName());

    public static void main(String[] args) throws IOException {
        setupLogging();

        String loggingConfig = System.getProperty("java.util.logging.config.file");
        if (loggingConfig != null) {
            LOGGER.info("Logging config file: " + loggingConfig);
        } else {
            LOGGER.info("Logging config file not set");
        }

        final long startTime = System.currentTimeMillis();

        final boolean isServer = (args.length > 0 && args[0].equals("-s"));

        // ignored by everyone except mac
        System.setProperty("apple.laf.useScreenMenuBar", "true");

        LOGGER.info("java.vm.specification.version:"
                + System.getProperty("java.vm.specification.version"));
        LOGGER.info("java.vm.specification.vendor :"
                + System.getProperty("java.vm.specification.vendor"));
        LOGGER.info("java.vm.specification.name   :"
                + System.getProperty("java.vm.specification.name"));
        LOGGER.info("java.vm.version              :" + System.getProperty("java.vm.version"));
        LOGGER.info("java.vm.vendor               :" + System.getProperty("java.vm.vendor"));
        LOGGER.info("java.vm.name                 :" + System.getProperty("java.vm.name"));
        LOGGER.info("Desktop.isDesktopSupported() :" + Desktop.isDesktopSupported());

        LOGGER.info("-------->> before javax.swing.UIManager invoke later "
                + (System.currentTimeMillis() - startTime));

        SwingUtilities.invokeLater(() -> {
            try {
                Class<?> uiClass = Class.forName("javax.swing.UIManager");
                Method setLookAndFeel = uiClass.getMethod("setLookAndFeel", String.class);
                Method getSystemLookAndFeelClassName =
                        uiClass.getMethod("getSystemLookAndFeelClassName");
                String lookAndFeelName = (String) getSystemLookAndFeelClassName.invoke(null);
                setLookAndFeel.invoke(null, lookAndFeelName);
            } catch (ClassNotFoundException e1) {
                e1.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }

            // this gets awt to start initializing while we initialize Myster's
            // backend
            var f = new JFrame();
            f.pack();
            f.dispose();
        });

        LOGGER.info("-------->> before Appl init " + (System.currentTimeMillis() - startTime));

        ApplicationSingletonListener applicationSingletonListener =
                new ApplicationSingletonListener() {
                    public void requestLaunch(String[] args) {}

                    public void errored(Exception ignore) {
                        // nothing
                    }
                };
        
        ApplicationContext applicationContext =
                new ApplicationContext(10457, applicationSingletonListener, args);

        MysterGlobals.appSigleton = applicationContext;

        try {
            if (!applicationContext.start())
                return;
        } catch (IOException e) {
            e.printStackTrace();
            Frame parent = AnswerDialog.getCenteredFrame();

            AnswerDialog
                    .simpleAlert(parent,
                                 "There seems to be another copy of Myster already running but I couldn't"
                                         + " contact it. If you're sharing the computer with other people, one of them"
                                         + " might be running Myster already or it might be that that Myster was not"
                                         + " started from the same place the previous copy was started. Restarting the "
                                         + " computer will make sure that the other Myster client gets quit.");
            parent.dispose(); // if this isn't here Myster won't quit.
            applicationContext.close();
            System.exit(0);
            return;
        }

        I18n.init();

        LOGGER.info("MAIN THREAD: Starting loader Thread..");

        LOGGER.info("-------->> before preferences " + (System.currentTimeMillis() - startTime));
        MysterPreferences preferences = MysterPreferences.getInstance();
        LOGGER.info("-------->> after preferences " + (System.currentTimeMillis() - startTime));

        ServerEventDispatcher serverDispatcher = new ServerEventDispatcher();
        DatagramProtocolManager datagramManager = new DatagramProtocolManager();
        TransactionManager transactionManager =
                new TransactionManager(serverDispatcher, datagramManager);

        MysterProtocol protocol =
                new MysterProtocolImpl(new MysterStreamImpl(),
                                       new MysterDatagramImpl(transactionManager,
                                                              new UDPPingClient(datagramManager)));

        LOGGER.info("-------->> before IPListManager " + (System.currentTimeMillis() - startTime));
        IpListManager ipListManager =
                new IpListManager(new MysterIpPoolImpl(java.util.prefs.Preferences.userRoot(),
                                                       protocol),
                                  protocol,
                                  java.util.prefs.Preferences.userRoot()
                                          .node("Tracker.IpListManager"));
        LOGGER.info("-------->> after IPListManager " + (System.currentTimeMillis() - startTime));

        final HashCrawlerManager crawlerManager =
                new MultiSourceHashSearch(ipListManager, protocol);
        ClientWindow.init(protocol, crawlerManager, ipListManager);



        ServerFacade serverFacade = new ServerFacade(ipListManager,
                                                     preferences,
                                                     datagramManager,
                                                     transactionManager,
                                                     serverDispatcher);

        serverFacade
                .addDatagramTransactions(new TopTenDatagramServer(ipListManager),
                                         new TypeDatagramServer(),
                                         new SearchDatagramServer(),
                                         new ServerStatsDatagramServer(serverFacade::getIdentity),
                                         new FileStatsDatagramServer(),
                                         new SearchHashDatagramServer());

        final HashManager hashManager = new HashManager();
        FileTypeListManager.init((f, l) -> hashManager.findHash(f, l));

        // asynchronously start the server
        serverFacade.startServer();

        LOGGER.info("-------->> before invokeAndWait " + (System.currentTimeMillis() - startTime));

        try {
            EventQueue.invokeAndWait(() -> {
                LOGGER.info("-------->> inside  invokeAndWait"
                        + (System.currentTimeMillis() - startTime));
                try {
                    if (com.myster.type.TypeDescriptionList.getDefault()
                            .getEnabledTypes().length <= 0) {
                        AnswerDialog
                                .simpleAlert("There are not enabled types. This screws up Myster. Please make sure"
                                        + " the typedescriptionlist.mml is in the right place and correctly"
                                        + " formated.");
                        MysterGlobals.quit();
                        return; // not reached
                    }
                } catch (Exception ex) {
                    AnswerDialog.simpleAlert("Could not load the Type Description List: \n\n" + ex);
                    MysterGlobals.quit();
                    return; // not reached
                }

                LOGGER.info("-------->> before menuBarFactory "
                        + (System.currentTimeMillis() - startTime));

                MysterMenuBar menuBarFactory = new MysterMenuBar();
                WindowManager windowManager = new WindowManager();
                final MysterFrameContext context =
                        new MysterFrameContext(menuBarFactory, windowManager);
                PreferencesGui preferencesGui = new PreferencesGui(context);

                serverFacade
                        .addDatagramTransactions(new ImTransactionServer(preferences,
                                                                         (instantMessage) -> (new MessageWindow(context,
                                                                                                                protocol,
                                                                                                                instantMessage,
                                                                                                                ipListManager::getQuickServerStats))
                                                                                                                        .show()));

                menuBarFactory.initMenuBar(ipListManager, preferencesGui, windowManager, protocol);

                String osName = System.getProperty("os.name").toLowerCase();
                if (osName.startsWith("mac os") && Desktop.isDesktopSupported()) {
                    menuBarFactory.addMenuListener(new MenuBarListener() {
                        public void stateChanged(MenuBarEvent e) {
                            Desktop.getDesktop().setDefaultMenuBar(e.makeNewMenuBar(null));
                        }
                    });
                }

                TrackerWindow.init(ipListManager, context);

                com.myster.hash.ui.HashManagerGUI.init(context, hashManager);

                ServerStatsWindow.init(serverFacade.getServerDispatcher().getServerContext(),
                                       context,
                                       protocol);
                LOGGER.info("-------->> before ServerStatsWindow.getInstance().pack() "
                        + (System.currentTimeMillis() - startTime));
                ServerStatsWindow.getInstance().pack();

                SearchWindow.init(protocol, crawlerManager, ipListManager);

                LOGGER.info("-------->> before addPanels "
                        + (System.currentTimeMillis() - startTime));
                preferencesGui.addPanel(BandwidthManager.getPrefsPanel());
                preferencesGui.addPanel(new BannersPreferences());
                preferencesGui.addPanel(serverFacade.new ServerPrefPanel());
                preferencesGui.addPanel(new FmiChooser(FileTypeListManager.getInstance()));
                preferencesGui.addPanel(new MessagePreferencesPanel(preferences));
                preferencesGui.addPanel(new TypeManagerPreferencesGUI());

                // try {
                // (new com.myster.plugin.PluginLoader(new File(MysterGlobals
                // .getCurrentDirectory(), "plugins"))).loadPlugins();
                // } catch (Exception ex) {
                // // nothing
                // }

                com.myster.hash.ui.HashPreferences.init(preferencesGui, hashManager);

                LOGGER.info("-------->> before inits " + (System.currentTimeMillis() - startTime));

                if (isServer) {
                    // nothing
                } else {
                    com.myster.client.ui.ClientWindow.initWindowLocations(context);
                    ServerStatsWindow.initWindowLocations();
                    com.myster.tracker.ui.TrackerWindow.initWindowLocations();
                    com.myster.hash.ui.HashManagerGUI.initGui();
                    SearchWindow.initWindowLocations(context);
                }

                try {
                    com.myster.client.stream.MSPartialFile.restartDownloads(crawlerManager,
                                                                            context);
                } catch (IOException ex) {
                    LOGGER.info("Error in restarting downloads.");
                    ex.printStackTrace();
                }

                if (Desktop.getDesktop().isSupported(Action.APP_PREFERENCES)) {
                    Desktop.getDesktop().setPreferencesHandler(e -> preferencesGui.setGUI(true));
                }

                if (Desktop.getDesktop().isSupported(Action.APP_ABOUT)) {
                    Desktop.getDesktop().setAboutHandler(e -> AnswerDialog
                            .simpleAlert("Myster PR 10\n\nCommon, join the party.."));
                }
            });

            LOGGER.info("-------->>" + (System.currentTimeMillis() - startTime));


            Thread.sleep(1);

            Util.invokeLater(() -> MysterTray.init());


        } catch (InterruptedException ex) {
            ex.printStackTrace(); // never reached.
        } catch (InvocationTargetException ex) {
            Util.invokeLater(() -> AnswerDialog
                    .simpleAlert("" + ex.getTargetException().toString()));
        }

        hashManager.start();

        // ugh
        printoutAllNetworkInterfaces();
        printoutAllIpAddresses();
        LOGGER.info("UPnP available: " + UPnP.isUPnPAvailable());
        LOGGER.info("External UPnP gateway: " + UPnP.getDefaultGatewayIP());
        LOGGER.info("External IP: " + UPnP.getExternalIP());
        LOGGER.info("Local IP: " + UPnP.getLocalIP());
        LOGGER.info("isMappedTCP(): " + UPnP.isMappedTCP(MysterGlobals.SERVER_PORT));
        LOGGER.info("External TCP/IP port enabled: " + UPnP.openPortTCP(MysterGlobals.SERVER_PORT));
        LOGGER.info("External UDP/IP port enabled: " + UPnP.openPortUDP(MysterGlobals.SERVER_PORT));
    } // Utils, globals etc.. //These variables are System wide variables //


    private static void setupLogging() throws IOException {
        InputStream inputStream =
                Myster.class.getClassLoader().getResourceAsStream("logging.properties");
        if (inputStream != null) {
            try (InputStream in = new BufferedInputStream(inputStream)) {
                LogManager.getLogManager().readConfiguration(in);
            }
        } else {
            LOGGER.info("logging.properties file not found");
            return;
        }
    }


    private static void printoutAllNetworkInterfaces() {
        try {
            LOGGER.info("Full list of Network Interfaces:");
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
                    .hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                LOGGER.info("    " + intf.getName() + " " + intf.getDisplayName());
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
                        .hasMoreElements();) {
                    InetAddress nextAddress = enumIpAddr.nextElement();
                    LOGGER.info("        " + nextAddress.toString());
                }
            }
        } catch (SocketException e) {
            LOGGER.info(" (error retrieving network interface list)");
        }
    }

    private static void printoutAllIpAddresses() {
        List<InetAddress> networkAddresses = new ArrayList<>();
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            LOGGER.info("IP Addr for local host: " + localhost.getHostAddress());

            // Just in case this host has multiple IP addresses....
            InetAddress[] allMyIps = InetAddress.getAllByName(localhost.getCanonicalHostName());
            if (allMyIps != null && allMyIps.length > 1) {
                LOGGER.info(" Full list of IP addresses:");
                for (int i = 0; i < allMyIps.length; i++) {
                    LOGGER.info("    " + allMyIps[i].getHostAddress());
                    if (networkAddresses.isEmpty())
                        networkAddresses.add(allMyIps[i]);
                }
            }
        } catch (UnknownHostException e) {
            LOGGER.info(" (error retrieving server host name)");
        }
    }
}