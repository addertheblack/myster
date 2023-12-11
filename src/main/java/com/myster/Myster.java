/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2004
 */

package com.myster;

import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.general.application.ApplicationContext;
import com.general.application.ApplicationSingletonListener;
import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.application.MysterGlobals;
import com.myster.bandwidth.BandwidthManager;
import com.myster.client.net.MysterProtocol;
import com.myster.client.net.MysterProtocolImpl;
import com.myster.client.ui.ClientWindow;
import com.myster.filemanager.FileTypeListManager;
import com.myster.filemanager.ui.FMIChooser;
import com.myster.message.InstantMessageTransport;
import com.myster.message.MessageManager;
import com.myster.message.MessageWindow;
import com.myster.message.ui.MessagePreferencesPanel;
import com.myster.pref.Preferences;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MultiSourceHashSearch;
import com.myster.search.ui.SearchWindow;
import com.myster.server.BannersManager.BannersPreferences;
import com.myster.server.ServerFacade;
import com.myster.server.ui.ServerStatsWindow;
import com.myster.tracker.IPListManager;
import com.myster.tracker.MysterIPPoolImpl;
import com.myster.tracker.ui.TrackerWindow;
import com.myster.transaction.TransactionManager;
import com.myster.type.ui.TypeManagerPreferencesGUI;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.PreferencesGui;
import com.myster.ui.menubar.MysterMenuBar;
import com.myster.ui.tray.MysterTray;
import com.myster.util.I18n;
import com.simtechdata.waifupnp.UPnP;

public class Myster {
    private static final String LOCK_FILE_NAME = ".lockFile";

    public static void main(String[] args) {
        final long startTime = System.currentTimeMillis();

        final boolean isServer = (args.length > 0 && args[0].equals("-s"));

        System.out.println("java.vm.specification.version:"
                + System.getProperty("java.vm.specification.version"));
        System.out.println("java.vm.specification.vendor :"
                + System.getProperty("java.vm.specification.vendor"));
        System.out.println("java.vm.specification.name   :"
                + System.getProperty("java.vm.specification.name"));
        System.out
                .println("java.vm.version              :" + System.getProperty("java.vm.version"));
        System.out.println("java.vm.vendor               :" + System.getProperty("java.vm.vendor"));
        System.out.println("java.vm.name                 :" + System.getProperty("java.vm.name"));

        System.out.println("-------->> before javax.swing.UIManager invoke later "
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

        System.out
                .println("-------->> before Appl init " + (System.currentTimeMillis() - startTime));

        ApplicationSingletonListener applicationSingletonListener =
                new ApplicationSingletonListener() {
                    public void requestLaunch(String[] args) {}

            public void errored(Exception ignore) {
                // nothing
            }
        };

        ApplicationContext applicationContext =
                new ApplicationContext(new File(MysterGlobals.getCurrentDirectory(),
                                                LOCK_FILE_NAME),
                                       10457,
                                       applicationSingletonListener,
                                       args);

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

        System.out.println("MAIN THREAD: Starting loader Thread..");
        
        System.out.println("-------->> before preferences " + (System.currentTimeMillis() - startTime));
        Preferences preferences  = Preferences.getInstance();
        System.out.println("-------->> after preferences " + (System.currentTimeMillis() - startTime));
        
        MysterProtocol protocol = new MysterProtocolImpl();

        System.out.println("-------->> before IPListManager " + (System.currentTimeMillis() - startTime));
        IPListManager listManager = new IPListManager(new MysterIPPoolImpl(preferences, protocol), protocol);
        System.out.println("-------->> after IPListManager " + (System.currentTimeMillis() - startTime));

        final HashCrawlerManager crawlerManager = new MultiSourceHashSearch(listManager, protocol);
        ClientWindow.init(protocol, crawlerManager, listManager);
        ServerFacade serverFacade = new ServerFacade(listManager, preferences);

        MessageManager.init(listManager, preferences);


        // asynchronously start the server
        serverFacade.startServer();

        System.out.println("-------->> before invokeAndWait " + (System.currentTimeMillis() - startTime));

        try {
            Util.invokeAndWait(() -> {
                System.out.println("-------->> inside  invokeAndWait" + (System.currentTimeMillis() - startTime));
                try {
                    if (com.myster.type.TypeDescriptionList.getDefault().getEnabledTypes().length <= 0) {
                        AnswerDialog
                                .simpleAlert("There are not enabled types. This screws up Myster. Please make sure"
                                        + " the typedescriptionlist.mml is in the right place and correctly"
                                        + " formated.");
                        MysterGlobals.quit();
                        return; // not reached
                    }
                } catch (Exception ex) {
                    AnswerDialog.simpleAlert("Could not load the Type Description List: \n\n"
                            + ex);
                    MysterGlobals.quit();
                    return; // not reached
                }

                System.out.println("-------->> before menuBarFactory " + (System.currentTimeMillis() - startTime));

                MysterMenuBar menuBarFactory = new MysterMenuBar();
                final MysterFrameContext context = new MysterFrameContext(menuBarFactory);
                PreferencesGui preferencesGui = new PreferencesGui(context);

                menuBarFactory.initMenuBar(listManager, preferencesGui);

                TransactionManager.addTransactionProtocol(new InstantMessageTransport(preferences,
                        (instantMessage) -> (new MessageWindow(context,
                                instantMessage, listManager::getQuickServerStats))
                                .show()));

                TrackerWindow.init(listManager, context);

                try {
                    com.myster.hash.HashManager.init();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                com.myster.hash.ui.HashManagerGUI.init(context);

                ServerStatsWindow.init(serverFacade.getServerDispatcher().getServerContext(), context);
                 System.out.println("-------->> before ServerStatsWindow.getInstance().pack() " + (System.currentTimeMillis() - startTime));
                ServerStatsWindow.getInstance().pack();

                SearchWindow.init(protocol, crawlerManager, listManager);

                 System.out.println("-------->> before addPanels " + (System.currentTimeMillis() - startTime));
                preferencesGui.addPanel(BandwidthManager.getPrefsPanel());
                preferencesGui.addPanel(new BannersPreferences());
                preferencesGui.addPanel(serverFacade.new ServerPrefPanel());
                preferencesGui.addPanel(new FMIChooser(FileTypeListManager.getInstance()));
                preferencesGui.addPanel(new MessagePreferencesPanel(preferences));
                preferencesGui.addPanel(new TypeManagerPreferencesGUI());

                try {
                    (new com.myster.plugin.PluginLoader(new File(MysterGlobals
                            .getCurrentDirectory(), "plugins"))).loadPlugins();
                } catch (Exception ex) {
                    // nothing
                }

                com.myster.hash.ui.HashPreferences.init();

                System.out.println("-------->> before inits " + (System.currentTimeMillis() - startTime));

                if (isServer) {
                    // nothing
                } else {
                    com.myster.ui.WindowManager.init(menuBarFactory);
                    com.myster.client.ui.ClientWindow.initWindowLocations(context);
                    ServerStatsWindow.initWindowLocations();
                    com.myster.tracker.ui.TrackerWindow.initWindowLocations();
                    com.myster.hash.ui.HashManagerGUI.initGui();
                    SearchWindow.initWindowLocations(context);
                }

                try {
                    com.myster.client.stream.MSPartialFile.restartDownloads(crawlerManager, context);
                } catch (IOException ex) {
                    System.out.println("Error in restarting downloads.");
                    ex.printStackTrace();
                }
            });

            System.out.println("-------->>" + (System.currentTimeMillis() - startTime));

            Thread.sleep(1);

            com.myster.hash.HashManager.start();
            FileTypeListManager.getInstance();
            
            Util.invokeLater(() -> MysterTray.init());
        } catch (InterruptedException ex) {
            ex.printStackTrace(); //never reached.
        }

        // ugh
        printoutAllNetworkInterfaces();
        printoutAllIpAddresses();
        System.out.println("UPnP available: " +UPnP.isUPnPAvailable());
        System.out.println("External UPnP gateway: " +UPnP.getDefaultGatewayIP());
        System.out.println("External IP: " +UPnP.getExternalIP());
        System.out.println("Local IP: " +UPnP.getLocalIP());
        System.out.println("isMappedTCP(): " + UPnP.isMappedTCP(MysterGlobals.DEFAULT_PORT));
        System.out.println("External TCP/IP port enabled: "+ UPnP.openPortTCP(MysterGlobals.DEFAULT_PORT));
        System.out.println("External UDP/IP port enabled: " + UPnP.openPortUDP(MysterGlobals.DEFAULT_PORT));
    } // Utils, globals etc.. //These variables are System wide variables //


    private static void printoutAllNetworkInterfaces() {
        try {
            System.out.println("Full list of Network Interfaces:");
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
                    .hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                System.out.println("    " + intf.getName() + " " + intf.getDisplayName());
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
                        .hasMoreElements();) {
                    InetAddress nextAddress = enumIpAddr.nextElement();
                    System.out.println("        " + nextAddress.toString());
                }
            }
        } catch (SocketException e) {
            System.out.println(" (error retrieving network interface list)");
        }
    }

    private static void printoutAllIpAddresses() {
        List<InetAddress> networkAddresses = new ArrayList<>();
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            System.out.println("IP Addr for local host: " + localhost.getHostAddress());
            // Just in case this host has multiple IP addresses....
            InetAddress[] allMyIps = InetAddress.getAllByName(localhost.getCanonicalHostName());
            if (allMyIps != null && allMyIps.length > 1) {
                System.out.println(" Full list of IP addresses:");
                for (int i = 0; i < allMyIps.length; i++) {
                    System.out.println("    " + allMyIps[i].getHostAddress());
                    if (networkAddresses.isEmpty())
                        networkAddresses.add(allMyIps[i]);
                }
            }
        } catch (UnknownHostException e) {
            System.out.println(" (error retrieving server host name)");
        }
    }
}