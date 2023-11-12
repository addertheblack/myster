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

import javax.swing.SwingUtilities;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.igd.PortMappingListener;
import org.fourthline.cling.support.model.PortMapping;

import com.general.application.ApplicationSingleton;
import com.general.application.ApplicationSingletonListener;
import com.general.util.AnswerDialog;
import com.general.util.UnexpectedError;
import com.general.util.UnexpectedException;
import com.general.util.Util;
import com.myster.application.MysterGlobals;
import com.myster.bandwidth.BandwidthManager;
import com.myster.client.ui.ClientWindow;
import com.myster.filemanager.FileTypeListManager;
import com.myster.filemanager.ui.FMIChooser;
import com.myster.menubar.MysterMenuBar;
import com.myster.message.MessageManager;
import com.myster.message.ui.MessagePreferencesPanel;
import com.myster.pref.Preferences;
import com.myster.search.MultiSourceHashSearch;
import com.myster.search.ui.SearchWindow;
import com.myster.server.BannersManager.BannersPreferences;
import com.myster.server.ServerFacade;
import com.myster.server.ui.ServerStatsWindow;
import com.myster.tracker.IPListManager;
import com.myster.tracker.MysterIPPool;
import com.myster.tracker.ui.TrackerWindow;
import com.myster.type.ui.TypeManagerPreferencesGUI;
import com.myster.ui.PreferencesGui;
import com.myster.util.I18n;

public class Myster {
    private static final String LOCK_FILE_NAME = ".lockFile";

    public static void main(String[] args) {
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


        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    Class uiClass = Class.forName("javax.swing.UIManager");
                    Method setLookAndFeel =
                            uiClass.getMethod("setLookAndFeel", new Class[] { String.class });
                    Method getSystemLookAndFeelClassName =
                            uiClass.getMethod("getSystemLookAndFeelClassName", new Class[] {});
                    String lookAndFeelName = (String) getSystemLookAndFeelClassName.invoke(null, null);
                    setLookAndFeel.invoke(null, new Object[] { lookAndFeelName });
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
            });
        } catch (InvocationTargetException | InterruptedException exception) {
            throw new UnexpectedException(exception);
        }

        //        try {
        //            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        //        } catch (ClassNotFoundException e) {
        //            e.printStackTrace();
        //        } catch (InstantiationException e) {
        //            e.printStackTrace();
        //        } catch (IllegalAccessException e) {
        //            e.printStackTrace();
        //        } catch (UnsupportedLookAndFeelException e) {
        //            e.printStackTrace();
        //        }
        ApplicationSingletonListener applicationSingletonListener = new ApplicationSingletonListener() {
            public void requestLaunch(String[] args) {
                SearchWindow search = new SearchWindow();
                search.show();
            }

            public void errored(Exception ignore) {
                // nothing
            }
        };

        ApplicationSingleton applicationSingleton = new ApplicationSingleton(new File(MysterGlobals
                .getCurrentDirectory(), LOCK_FILE_NAME), 10457, applicationSingletonListener, args);

        MysterGlobals.appSigleton = applicationSingleton;
        final long startTime = System.currentTimeMillis();
        try {
            if (!applicationSingleton.start())
                return;
        } catch (IOException e) {
            e.printStackTrace();
            Frame parent = AnswerDialog.getCenteredFrame();

            AnswerDialog
                    .simpleAlert(
                            parent,
                            "There seems to be another copy of Myster already running but I couldn't"
                                    + " contact it. If you're sharing the computer with other people, one of them"
                                    + " might be running Myster already or it might be that that Myster was not"
                                    + " started from the same place the previous copy was started. Restarting the "
                                    + " computer will make sure that the other Myster client gets quit.");
            parent.dispose(); //if this isn't here Myster won't quit.
            applicationSingleton.close();
            System.exit(0);
            return;
        }

        I18n.init();

        System.out.println("MAIN THREAD: Starting loader Thread..");
        
        Preferences[] p = new Preferences[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                p[0] = Preferences.getInstance();
            });
        } catch (InvocationTargetException | InterruptedException exception) {
            throw new UnexpectedError(exception);
        }
        
        Preferences preferences = p[0];
        
        IPListManager listManager = new IPListManager(new MysterIPPool(preferences));
        
        MultiSourceHashSearch.init(listManager);
        TrackerWindow.init(listManager);
        ClientWindow.init(listManager);
        ServerFacade serverFacade = new ServerFacade(listManager, preferences);
        
        MessageManager.init(listManager, preferences);
        
        serverFacade.startServer();
        
        try {

            Util.invokeAndWait(new Runnable() {
                public void run() {

                    try {
                        if (com.myster.type.TypeDescriptionList.getDefault().getEnabledTypes().length <= 0) {
                            AnswerDialog
                                    .simpleAlert("There are not enabled types. This screws up Myster. Please make sure"
                                            + " the typedescriptionlist.mml is in the right place and correctly"
                                            + " formated.");
                            MysterGlobals.quit();
                            return; //not reached
                        }
                    } catch (Exception ex) {
                        AnswerDialog.simpleAlert("Could not load the Type Description List: \n\n"
                                + ex);
                        MysterGlobals.quit();
                        return; //not reached
                    }
                    PreferencesGui preferencesGui = new PreferencesGui();
                    MysterMenuBar.init(listManager, preferencesGui);

                    try {
                        com.myster.hash.HashManager.init();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    
                    com.myster.hash.ui.HashManagerGUI.init();

                    ServerStatsWindow.init(serverFacade.getServerDispatcher().getServerContext());
                    ServerStatsWindow.getInstance().pack();

                    SearchWindow.init(listManager);

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
                }
            });

            System.out.println("-------->>" + (System.currentTimeMillis() - startTime));
            if (isServer) {
                // nothing
            } else {
                Util.invokeAndWait(new Runnable() {
                    public void run() {
                        com.myster.ui.WindowManager.init();
                        
                        com.myster.client.ui.ClientWindow.initWindowLocations();
                        ServerStatsWindow.initWindowLocations();
                        com.myster.tracker.ui.TrackerWindow.initWindowLocations();
                        com.myster.hash.ui.HashManagerGUI.initGui();
                        SearchWindow.initWindowLocations();
                    }
                });
            }

            Thread.sleep(1);

            Util.invokeLater(new Runnable() {
                public void run() {
                    try {
                        com.myster.client.stream.MSPartialFile.restartDownloads();
                    } catch (IOException ex) {
                        System.out.println("Error in restarting downloads.");
                        ex.printStackTrace();
                    }
                }
            });

            com.myster.hash.HashManager.start();
            FileTypeListManager.getInstance();
        } catch (InterruptedException ex) {
            ex.printStackTrace(); //never reached.
        }

        // ugh

        printoutAllNetworkInterfaces();
        printoutAllIpAddresses();
        setupUpnp();
    } // Utils, globals etc.. //These variables are System wide variables //

    private static UpnpService setupUpnp() {
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            List<PortMapping> portMappings = new ArrayList<>();
            PortMapping e = new PortMapping(MysterGlobals.DEFAULT_PORT,
                                            "" + inetAddress.getHostAddress(),
                                            PortMapping.Protocol.TCP,
                                            "My Port Mapping TCP");
            e.setLeaseDurationSeconds(new UnsignedIntegerFourBytes(100));
            portMappings.add(e);
            PortMapping e2 = new PortMapping(MysterGlobals.DEFAULT_PORT,
                                             "" + inetAddress.getHostAddress(),
                                             PortMapping.Protocol.UDP,
                                             "Mooo");
            e2.setLeaseDurationSeconds(new UnsignedIntegerFourBytes(100));
            portMappings.add(e2);

            UpnpService upnpService = new UpnpServiceImpl(new PortMappingListener(portMappings
                    .toArray(new PortMapping[0])));

            upnpService.getControlPoint().search();
            return upnpService;
        } catch (UnknownHostException exception) {
            System.out.println("Could nto setup upnp because could not get local host: "
                    + exception.getMessage());
            return null;
        }
    }

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