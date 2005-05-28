/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2004
 */

package com.myster;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.general.application.ApplicationSingleton;
import com.general.application.ApplicationSingletonListener;
import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.application.MysterGlobals;
import com.myster.bandwidth.BandwidthManager;
import com.myster.filemanager.FileTypeListManager;
import com.myster.pref.Preferences;
import com.myster.search.ui.SearchWindow;
import com.myster.server.ServerFacade;
import com.myster.server.ui.ServerStatsWindow;
import com.myster.tracker.IPListManagerSingleton;
import com.myster.util.I18n;

public class Myster {
    private static final String LOCK_FILE_NAME = ".lockFile";

    public static void main(String[] args) {
        final boolean isServer = (args.length > 0 && args[0].equals("-s"));

        /*
         * (new Thread() { public void run() { for (;;) { try { System.out.println("File info -> "+
         * com.myster.client.stream.StandardSuite.getFileFromHash(new
         * com.myster.net.MysterAddress("68.227.184.219") , new com.myster.type.MysterType("MooV") ,
         * com.myster.hash.SimpleFileHash.buildFromHexString("md5",
         * "bdaba746d51978dbe46844c23f566332"))); } catch (Exception ex) { //ex.printStackTrace(); } } }
         * }).start();
         */
        /*
         * (new JFrame()).show(); OutputStream out=null; try { out = new FileOutputStream(new
         * File("/tmp/foo")); } catch (FileNotFoundException e2) { // TODO Auto-generated catch
         * block e2.printStackTrace(); } for (int i = 100000 ;; i++) { try { recurse(i, out); }
         * catch (IOException e1) { // TODO Auto-generated catch block e1.printStackTrace(); }
         * System.out.println("" +i); if (false == true) break; }
         * 
         * 
         * try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch
         * (ClassNotFoundException e) { // TODO Auto-generated catch block e.printStackTrace(); }
         * catch (InstantiationException e) { // TODO Auto-generated catch block
         * e.printStackTrace(); } catch (IllegalAccessException e) { // TODO Auto-generated catch
         * block e.printStackTrace(); } catch (UnsupportedLookAndFeelException e) { // TODO
         * Auto-generated catch block e.printStackTrace(); }
         */

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

//        final long startTime = System.currentTimeMillis();
        try {
            Class uiClass = Class.forName("javax.swing.UIManager");
            Method setLookAndFeel = uiClass.getMethod("setLookAndFeel", new Class[]{String.class});
            Method getSystemLookAndFeelClassName = uiClass.getMethod("getSystemLookAndFeelClassName", new Class[]{});
            String lookAndFeelName = (String) getSystemLookAndFeelClassName.invoke(null, null);
            setLookAndFeel.invoke(null, new Object[]{lookAndFeelName});
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
            AnswerDialog
            .simpleAlert( 
                    "Can't start Myster because there's another version of Myster already running. Here's a more technical explanation: \n\n" +
                    e);
            return;
        }

        I18n.init();

        //if (true == true)
        //    return;

        //start();????????

        System.out.println("MAIN THREAD: Starting loader Thread..");

        try {
//            final com.myster.util.ProgressWindow[] tempArray = new ProgressWindow[1];

            Util.initInvoke();

//            Util.invokeAndWait(new Runnable() {
//
//                public void run() {
//                    tempArray[0] = new com.myster.util.ProgressWindow();
//                    ProgressWindow progress = tempArray[0];
//                    progress.setMenuBarEnabled(false);
//                    progress.setTitle(I18n.tr("Loading Myster..."));
//                    progress.pack();
//                    com.general.util.Util.centerFrame(progress, 0, -50);
//                    //progress.setVisible(true);
//                }
//            });

//            Thread.sleep(1); //for redrawing progress on MacOS X

//            final com.myster.util.ProgressWindow progress = tempArray[0];
            Util.invokeAndWait(new Runnable() {
                public void run() {

                    try {
                        if (com.myster.type.TypeDescriptionList.getDefault().getEnabledTypes().length <= 0) {
                            AnswerDialog
                                    .simpleAlert( 
                                            "There are not enabled types. This screws up Myster. Please make sure"
                                                    + " the typedescriptionlist.mml is in the right place and correctly"
                                                    + " formated.");
                            MysterGlobals.quit();
                            return; //not reached
                        }
                    } catch (Exception ex) {
                        AnswerDialog.simpleAlert( "Could not load the Type Description List: \n\n" + ex);
                        MysterGlobals.quit();
                        return; //not reached
                    }

                    try {
                        com.myster.hash.HashManager.init();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    com.myster.hash.ui.HashManagerGUI.init();
//                }
//            });
//
////            Thread.sleep(1);
//
//            Util.invokeLater(new Runnable() {
//                public void run() {
//                    progress.setText(I18n.tr("Loading Server Components..."));
//                    progress.setValue(10);
                    ServerFacade.init();
//                }
//            });
//
////            Thread.sleep(1);
//
//            Util.invokeLater(new Runnable() {
//                public void run() {
//                    progress.setText(I18n.tr("Loading Server Stats Window... %1%%", "" + 15));
//                    progress.setValue(15);
                    ServerStatsWindow.getInstance().pack();
//                    progress.setText(I18n.tr("Loading Server Stats Window... %1%%", "" + 18));
//                    progress.setValue(18);
//                }
//            });
//
//            Util.invokeLater(new Runnable() {
//                public void run() {
//                    progress.setText(I18n.tr("Loading Instant Messaging..."));
//                    progress.setValue(72);
                    com.myster.message.MessageManager.init();
//                }
//            });
//
////            Thread.sleep(1);
//
//            Util.invokeLater(new Runnable() {
//                public void run() {
//                    progress.setText(I18n.tr(I18n.tr("Loading WindowManager...")));
//                    progress.setValue(78);
                    com.myster.ui.WindowManager.init();

                    Preferences.getInstance().addPanel(BandwidthManager.getPrefsPanel());
//                }
//            });
//
////            Thread.sleep(1);
//
//            Util.invokeLater(new Runnable() {
//                public void run() {
//                    progress.setText(I18n.tr("Loading Plugins..."));
//                    progress.setValue(80);
                    try {
                        (new com.myster.plugin.PluginLoader(new File("plugins"))).loadPlugins();
                    } catch (Exception ex) {
                    }

                    com.myster.hash.ui.HashPreferences.init(); //no opp

                    com.myster.type.ui.TypeManagerPreferencesGUI.init();

                }
            });

//            Thread.sleep(1);

//            Util.invokeAndWait(new Runnable() {
//                public void run() {
//                    progress.setVisible(false);
//                }
//            });

            System.out.println("-------->>"+(System.currentTimeMillis() - startTime));
            if (isServer) {
            } else {

//                Thread.sleep(1);

                Util.invokeAndWait(new Runnable() {
                    public void run() {
                        Preferences.initWindowLocations();
                        com.myster.client.ui.ClientWindow.initWindowLocations();
                        ServerStatsWindow.initWindowLocations();
                        com.myster.tracker.ui.TrackerWindow.initWindowLocations();
                        SearchWindow.initWindowLocations();
                        com.myster.hash.ui.HashManagerGUI.initGui();
                        Preferences.initGui();
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
            ServerFacade.assertServer();
            IPListManagerSingleton.getIPListManager();
            FileTypeListManager.getInstance();
        } catch (InterruptedException ex) {
            ex.printStackTrace(); //never reached.
        }
    } //Utils, globals etc.. //These variables are System wide variables //

}