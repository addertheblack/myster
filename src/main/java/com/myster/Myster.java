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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.general.application.ApplicationContext;
import com.general.application.ApplicationSingletonListener;
import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.application.MysterGlobals;
import com.myster.bandwidth.BandwidthManager;
import com.myster.client.ui.ClientWindow;
import com.myster.filemanager.FileTypeListManager;
import com.myster.filemanager.ui.FmiChooser;
import com.myster.hash.HashManager;
import com.myster.hash.ui.HashManagerGUI;
import com.myster.identity.Cid128;
import com.myster.identity.Identity;
import com.myster.message.ui.MessagePreferencesPanel;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.MysterProtocolImpl;
import com.myster.net.datagram.DatagramEncryptUtil.Lookup;
import com.myster.net.datagram.DatagramProtocolManager;
import com.myster.net.datagram.client.MysterDatagramImpl;
import com.myster.net.datagram.client.PublicKeyLookupImpl;
import com.myster.net.datagram.client.UDPPingClient;
import com.myster.net.datagram.message.ImTransactionServer;
import com.myster.net.datagram.message.MessageWindow;
import com.myster.net.server.BannersManager.BannersPreferences;
import com.myster.net.server.ServerFacade;
import com.myster.net.server.ServerPreferences;
import com.myster.net.server.ServerUtils;
import com.myster.net.server.datagram.FileStatsDatagramServer;
import com.myster.net.server.datagram.PingTransport;
import com.myster.net.server.datagram.SearchDatagramServer;
import com.myster.net.server.datagram.SearchHashDatagramServer;
import com.myster.net.server.datagram.ServerStatsDatagramServer;
import com.myster.net.server.datagram.TopTenDatagramServer;
import com.myster.net.server.datagram.TypeDatagramServer;
import com.myster.net.stream.client.MysterStreamImpl;
import com.myster.net.stream.client.msdownload.MSDownloadLocalQueue;
import com.myster.pref.MysterPreferences;
import com.myster.pref.ui.ThemePane;
import com.myster.progress.ui.DefaultDownloadManager;
import com.myster.progress.ui.DownloadManager;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MultiSourceHashSearch;
import com.myster.search.ui.SearchWindow;
import com.myster.server.event.ServerEventDispatcher;
import com.myster.server.ui.ServerPreferencesPane;
import com.myster.server.ui.ServerStatsWindow;
import com.myster.tracker.MysterServerPoolImpl;
import com.myster.tracker.Tracker;
import com.myster.tracker.ui.TrackerWindow;
import com.myster.transaction.TransactionManager;
import com.myster.type.DefaultTypeDescriptionList;
import com.myster.type.TypeDescriptionList;
import com.myster.type.ui.TypeManagerPreferencesGUI;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.PreferencesGui;
import com.myster.ui.WindowManager;
import com.myster.ui.WindowPrefDataKeeper;
import com.myster.ui.menubar.MysterMenuBar;
import com.myster.ui.menubar.event.MenuBarEvent;
import com.myster.ui.menubar.event.MenuBarListener;
import com.myster.ui.tray.MysterTray;
import com.myster.util.I18n;
import com.myster.util.ThemeUtil;
import com.simtechdata.waifupnp.UPnP;

public class Myster {
    private static final Logger LOGGER = Logger.getLogger(Myster.class.getName());
    private static final Logger INSTRUMENTATION = Logger.getLogger("INSTRUMENTATION");

    public static void main(String[] args) throws IOException {
        setupLogging();
        
        String loggingConfig = System.getProperty("java.util.logging.config.file");
        if (loggingConfig != null) {
            LOGGER.info("Logging config file: " + loggingConfig);
        } else {
            LOGGER.info("Logging config file not set");
        }
        
        TypeDescriptionList tdList;
        try {
            tdList = new DefaultTypeDescriptionList();
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
        
        // this sets the look and feel to follow the light/dark app prefs on the macos
        // It does some areas that the theme can't access
        System.setProperty("apple.awt.application.appearance", "system");

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

        MysterPreferences preferences = MysterPreferences.getInstance();
        
        INSTRUMENTATION.info("-------->> before javax.swing.UIManager invoke later "
                + (System.currentTimeMillis() - startTime));

        // we do this as early as possible since the EDT is not part of this thread so we can get
        // two threads working at the same time
        SwingUtilities.invokeLater(() -> {
            INSTRUMENTATION
                    .info("-------->> !! EDT Started: " + (System.currentTimeMillis() - startTime));
            ThemeUtil.applyThemeFromPreferences(preferences);

            INSTRUMENTATION.info("-------->> !! Set look and feel: " + (System.currentTimeMillis() - startTime));

            // this gets awt to start initializing on the EDT while we initialize Myster's
            // backend
            var f = new JFrame();
            f.pack(); // this starts up the AWT graphics stuff
            f.dispose(); // we just want to warm up the awt stuff.. We don't need to do anything yet.
            INSTRUMENTATION.info("-------->> !! EDT Basic AWT libs initialized: " + (System.currentTimeMillis() - startTime));
        });

        INSTRUMENTATION.info("-------->> before Appl init " + (System.currentTimeMillis() - startTime));

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

            Util.invokeAndWaitNoThrows(() -> {
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
                System.exit(0); // too many weird bugs just force it to quit.
            });
            return;
        }
        
        INSTRUMENTATION.info("-------->> Init Identity " + (System.currentTimeMillis() - startTime));

        Identity identity = Identity.getIdentity();
        
        INSTRUMENTATION.info("-------->> Init I18n " + (System.currentTimeMillis() - startTime));
        I18n.init();

        INSTRUMENTATION.info("-------->> Init datagram server " + (System.currentTimeMillis() - startTime));
        ServerEventDispatcher serverDispatcher = new ServerEventDispatcher();
        DatagramProtocolManager datagramManager = new DatagramProtocolManager();
        TransactionManager transactionManager =
                new TransactionManager(serverDispatcher, datagramManager);

        
        INSTRUMENTATION.info("-------->> Init client protocol impl " + (System.currentTimeMillis() - startTime));
        PublicKeyLookupImpl serverLookup = new PublicKeyLookupImpl();
        MSDownloadLocalQueue downloadQueue = 
                new MSDownloadLocalQueue(Preferences.userRoot().node("Downloads"));
        
        MysterProtocol protocol =
                new MysterProtocolImpl(new MysterStreamImpl(downloadQueue),
                                       new MysterDatagramImpl(transactionManager,
                                                              new UDPPingClient(datagramManager),
                                                              serverLookup)); // AddressLookup - placeholder for now

        INSTRUMENTATION.info("-------->> Init IPListManager "
                + (System.currentTimeMillis() - startTime));
        MysterServerPoolImpl pool = new MysterServerPoolImpl(Preferences.userRoot(), protocol);
        Tracker tracker = new Tracker(pool, Preferences.userRoot().node("Tracker.IpListManager"), tdList);
        
        // An annoying circular ref. The tracker uses the protocol to refresh information in its db and the 
        // protocol stack uses the tracker and friends to lookup a server's public key for encryption.
        // Ideally a separate thread would do the refresh and requires the protocol stack while the tracker would simply 
        // be an information repo used for lookups. baby steps
        serverLookup.setMysterServerPool(pool);
        
        // This will cause the tracker sub system to starting pinging and getting server stats for servers in it's in mem db
        // it's a good idea to make sure the damn  server lookup code, used by the protocol stack is initated first.
        pool.startRefreshTimer();
        INSTRUMENTATION
                .info("-------->> after IPListManager " + (System.currentTimeMillis() - startTime));

        Preferences serverPreferenceNodes = Preferences.userRoot().node("Myster Server Preferences");
        ServerPreferences serverPreferences = new ServerPreferences(serverPreferenceNodes);

        final HashCrawlerManager crawlerManager =
                new MultiSourceHashSearch(tracker, protocol);
        ClientWindow.init(protocol, crawlerManager, tracker, serverPreferences, tdList);


        final HashManager hashManager = new HashManager();
        FileTypeListManager fileManager = new FileTypeListManager((f, l) -> hashManager.findHash(f, l), tdList);
        
        ServerFacade serverFacade = new ServerFacade(tracker,
                                                     serverPreferences,
                                                     datagramManager,
                                                     transactionManager,
                                                     identity,
                                                     fileManager,
                                                     serverDispatcher);
        addServerConnectionSettings(serverFacade, tracker, serverPreferences, identity, datagramManager, fileManager);
        
        Optional<KeyPair> mainIdentity = Identity.getIdentity().getMainIdentity();
        serverFacade.addEncryptionSupport(new Lookup() {
            @Override
            public Optional<KeyPair> getServerKeyPair(Object serverId) {
                return mainIdentity;
            }
            
            @Override
            public Optional<PublicKey> findPublicKey(byte[] keyHash) {
                return pool.lookupIdentityFromCid(new Cid128(keyHash));
            }
        });
        
        // asynchronously start the server
        serverFacade.startServer();

        INSTRUMENTATION.info("-------->> Init AWT GUI " + (System.currentTimeMillis() - startTime));

        try {
            EventQueue.invokeAndWait(() -> {
                INSTRUMENTATION.info("-------->>   EDT Init AWT GUI "
                        + (System.currentTimeMillis() - startTime));
                
                // might move this if I can be bothered
//                try {
//                    if (com.myster.type.TypeDescriptionList.getDefault()
//                            .getEnabledTypes().length <= 0) {
//                        AnswerDialog
//                                .simpleAlert("There are not enabled types. This screws up Myster. Please make sure"
//                                        + " the typedescriptionlist.mml is in the right place and correctly"
//                                        + " formated.");
//                        MysterGlobals.quit();
//                        return; // not reached
//                    }
//                } catch (Exception ex) {
//                    AnswerDialog.simpleAlert("Could not load the Type Description List: \n\n" + ex);
//                    MysterGlobals.quit();
//                    return; // not reached
//                }

                
                INSTRUMENTATION.info("-------->>   EDT init WindowManager "
                        + (System.currentTimeMillis() - startTime));
                MysterMenuBar menuBarFactory = new MysterMenuBar();
                WindowManager windowManager = new WindowManager();
                WindowPrefDataKeeper keeper = new WindowPrefDataKeeper(preferences);
                
                // Create temporary context to initialize downloadManager
                // The AI did this and it made me laugh so I kept it in.
                // Fuck you people who think this is a terrible reason to 
                // accept code into the codebase!
                MysterFrameContext tempContext =
                        new MysterFrameContext(menuBarFactory,
                                               windowManager,
                                               tdList,
                                               keeper,
                                               fileManager,
                                               null);
                DownloadManager downloadManager = new DefaultDownloadManager(tempContext);
                
                // Now create the final context with the downloadManager properly set
                final MysterFrameContext context =
                        new MysterFrameContext(menuBarFactory,
                                               windowManager,
                                               tdList,
                                               keeper,
                                               fileManager,
                                               downloadManager);
                
                PreferencesGui preferencesGui = new PreferencesGui(context);

                serverFacade
                        .addDatagramTransactions(new ImTransactionServer(preferences,
                                                                         (instantMessage) -> (new MessageWindow(context,
                                                                                                                protocol,
                                                                                                                instantMessage,
                                                                                                                tracker::getQuickServerStats))
                                                                                                                        .show()));
                INSTRUMENTATION.info("-------->>   EDT init MysterMenuBar "
                        + (System.currentTimeMillis() - startTime));
                menuBarFactory.initMenuBar(tracker, preferencesGui, protocol, context);

                String osName = System.getProperty("os.name").toLowerCase();
                if (osName.startsWith("mac os") && Desktop.isDesktopSupported()) {
                    menuBarFactory.addMenuListener(new MenuBarListener() {
                        public void stateChanged(MenuBarEvent e) {
                            Desktop.getDesktop().setDefaultMenuBar(e.makeNewMenuBar(null));
                        }
                    });
                }

                INSTRUMENTATION.info("-------->>   EDT init TrackerWindow "
                        + (System.currentTimeMillis() - startTime));
                TrackerWindow.init(tracker, context);

                INSTRUMENTATION.info("-------->>   EDT init ServerStatsWindow "
                        + (System.currentTimeMillis() - startTime));
                ServerStatsWindow.init(serverFacade.getServerDispatcher().getServerContext(),
                                       context,
                                       protocol);
                INSTRUMENTATION.info("-------->>   EDT init ServerStatsWindow.getInstance().pack() "
                        + (System.currentTimeMillis() - startTime));
                ServerStatsWindow.getInstance().pack();

                SearchWindow.init(protocol, crawlerManager, tracker);

                INSTRUMENTATION.info("-------->>   EDT add panels tor preferences "
                        + (System.currentTimeMillis() - startTime));
                preferencesGui.addPanel(BandwidthManager.getPrefsPanel());
                preferencesGui.addPanel(new BannersPreferences());
                preferencesGui.addPanel(new ServerPreferencesPane(serverPreferences));
                preferencesGui.addPanel(new FmiChooser(fileManager, tdList));
                preferencesGui.addPanel(new MessagePreferencesPanel(preferences));
                preferencesGui.addPanel(new TypeManagerPreferencesGUI(tdList));
                preferencesGui.addPanel(new ThemePane(preferences));

                INSTRUMENTATION.info("-------->>   EDT init other GUI sub systems " + (System.currentTimeMillis() - startTime));

                if (isServer) {
                    // nothing
                } else {
                    var count = 0;
                    count += com.myster.client.ui.ClientWindow.initWindowLocations(context);
                    count += ServerStatsWindow.initWindowLocations(context);
                    count += com.myster.tracker.ui.TrackerWindow.initWindowLocations(context);
                    
                    HashManagerGUI.init(context, hashManager);
                    
                    count += com.myster.hash.ui.HashManagerGUI.initGui(context);
                    count += SearchWindow.initWindowLocations(context);
                    
                    count += preferencesGui.initGui();
                    
                    // Initialize ProgressManagerWindow location BEFORE restarting downloads
                    count += ((DefaultDownloadManager)downloadManager).initWindowLocations();
                    
                    if (count == 0) {
                        SearchWindow window = new SearchWindow(context);
                        window.setVisible(true);
                    }
                }

                try {
                    com.myster.net.stream.client.msdownload.MSPartialFile
                            .restartDownloads(fileManager, crawlerManager, context, downloadQueue);
                } catch (IOException ex) {
                    LOGGER.info("Error in restarting downloads.");
                    ex.printStackTrace();
                }

                if (Desktop.getDesktop().isSupported(Action.APP_PREFERENCES)) {
                    Desktop.getDesktop().setPreferencesHandler(_ -> preferencesGui.setGUI(true));
                }

                if (Desktop.getDesktop().isSupported(Action.APP_ABOUT)) {
                    Desktop.getDesktop().setAboutHandler(_ -> AnswerDialog
                            .simpleAlert("Myster PR 10\n\nCome on in, join the party.."));
                }
                
                if (Desktop.getDesktop().isSupported(Action.APP_QUIT_HANDLER)) {
                    Desktop.getDesktop().setQuitHandler((_, response) -> {
                        // Add any cleanup code here before quitting
                        MysterGlobals.quit();
                        response.performQuit();
                    });
                }
                
                MysterTray.init();
                
                INSTRUMENTATION.info("-------->>   EDT AWT GUID init complete " + (System.currentTimeMillis() - startTime));
            });
        } catch (InterruptedException ex) {
            ex.printStackTrace(); // never reached.
        } catch (InvocationTargetException ex) {
            Util.invokeLater(() -> AnswerDialog
                    .simpleAlert("" + ex.getTargetException().toString()));
            ex.printStackTrace();
        }

        INSTRUMENTATION.info("-------->> Application init complete " + (System.currentTimeMillis() - startTime));
        hashManager.start();

        // ugh
        printoutAllNetworkInterfaces();
        printoutAllIpAddresses();
        LOGGER.info("UPnP available: " + UPnP.isUPnPAvailable());
        LOGGER.info("External UPnP gateway: " + UPnP.getDefaultGatewayIP());
        LOGGER.info("External IP: " + UPnP.getExternalIP());
        LOGGER.info("Local IP: " + UPnP.getLocalIP());
        LOGGER.info("isMappedTCP(): " + UPnP.isMappedTCP(MysterGlobals.DEFAULT_SERVER_PORT));
        LOGGER.info("External TCP/IP port enabled: " + UPnP.openPortTCP(MysterGlobals.DEFAULT_SERVER_PORT));
        LOGGER.info("External UDP/IP port enabled: " + UPnP.openPortUDP(MysterGlobals.DEFAULT_SERVER_PORT));
        
        ServerUtils.massPing(protocol, tracker);
    } // Utils, globals etc.. //These variables are System wide variables //

    private static void addServerConnectionSettings(ServerFacade serverFacade,
                                                    Tracker tracker,
                                                    ServerPreferences preferences,
                                                    Identity identity,
                                                    DatagramProtocolManager datagramManager, 
                                                    FileTypeListManager fileManager) {
        
        serverFacade.addConnectionSection(new com.myster.net.stream.server.MysterServerLister(tracker));
        serverFacade.addConnectionSection(new com.myster.net.stream.server.RequestDirThread());
        serverFacade.addConnectionSection(new com.myster.net.stream.server.FileTypeLister());
        serverFacade.addConnectionSection(new com.myster.net.stream.server.RequestSearchThread());
        serverFacade
                .addConnectionSection(new com.myster.net.stream.server.ServerStats(preferences::getIdentityName,
                                                                               preferences::getServerPort,
                                                                               identity));
        serverFacade.addConnectionSection(new com.myster.net.stream.server.FileStatsStreamServer());
        serverFacade.addConnectionSection(new com.myster.net.stream.server.FileStatsBatchStreamServer());
        serverFacade.addConnectionSection(new com.myster.net.stream.server.FileByHash());
        serverFacade.addConnectionSection(new com.myster.net.stream.server.MultiSourceSender(preferences));
        serverFacade.addConnectionSection(new com.myster.net.stream.server.FileTypeLister());

        datagramManager.mutateTransportManager(preferences.getServerPort(),
                                               t -> t.addTransport(new PingTransport(tracker)));

        serverFacade
                .addDatagramTransactions(new TopTenDatagramServer(tracker),
                                         new TypeDatagramServer(fileManager),
                                         new SearchDatagramServer(fileManager),
                                         new ServerStatsDatagramServer(preferences::getIdentityName,
                                                                       preferences::getServerPort,
                                                                       identity,
                                                                       fileManager),
                                         new FileStatsDatagramServer(fileManager),
                                         new SearchHashDatagramServer(fileManager));
    }

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
        } catch (SocketException _) {
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
