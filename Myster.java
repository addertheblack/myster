/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/


import java.io.*;
import java.net.*;
import java.awt.*;
import com.myster.util.MysterThread;
import com.general.util.Util;
import com.myster.server.Operator;
import com.myster.server.datagram.UDPOperator;
import com.myster.tracker.IPListManagerSingleton;

import java.util.zip.*;
import java.util.*;
import com.myster.filemanager.*; 
import com.myster.search.ui.SearchWindow;

import com.myster.server.ui.ServerStatsWindow;

import com.general.util.AskDialog;
 
import com.myster.server.ServerFacade;



import com.myster.server.datagram.*;
import com.myster.net.*;
import com.myster.client.datagram.*;
import com.general.util.*;

import com.myster.mml.*;

import com.myster.pref.Preferences;
import com.myster.bandwidth.BandwidthManager;

import java.util.Locale;

import com.myster.transaction.*; //test

import com.myster.util.I18n;


public class Myster{

	public static File file;
	public static final String fileLockName=".lockFile";

	private static long programLaunchTime = 0;

	public static void main(String args[]) {
		final boolean isServer=(args.length>0&&args[0].equals("-s"));
		

		
		System.out.println("java.vm.specification.version:"+System.getProperty("java.vm.specification.version"));
		System.out.println("java.vm.specification.vendor :"+System.getProperty("java.vm.specification.vendor"));
		System.out.println("java.vm.specification.name   :"+System.getProperty("java.vm.specification.name"));
		System.out.println("java.vm.version              :"+System.getProperty("java.vm.version"));
		System.out.println("java.vm.vendor               :"+System.getProperty("java.vm.vendor"));
		System.out.println("java.vm.name                 :"+System.getProperty("java.vm.name"));
		
		programLaunchTime = System.currentTimeMillis();
		
		I18n.init();

		//if (true==true) return;
		
		start();
		
		

		
		System.out.println( "MAIN THREAD: Starting loader Thread.." );
		(new Thread() {
			public void run() {
				try {Thread.currentThread().sleep(500);} catch (Exception ex) {}
				
				String macHack="";//(System.getProperty("java.vm.vendor")==null?" (unknown 1.1 java)":System.getProperty("java.vm.vendor"));
				com.myster.util.ProgressWindow progress=new com.myster.util.ProgressWindow();
				progress.setVisible(true);
				progress.setLocation(100,100);
				progress.setTitle(I18n.tr("Loading Myster..."));
				
				try {
					com.myster.hash.HashManager.init();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			
				com.myster.hash.ui.HashManagerGUI.init();
				
				progress.setText(I18n.tr("Loading UDP Operator...")+macHack);
				progress.setValue(10);
				try { //this stuff is a bit of a hack.. to be fixed later..
					PongTransport ponger=new PongTransport();UDPPingClient.setPonger(ponger);
					DatagramProtocolManager.addTransport(ponger);
					DatagramProtocolManager.addTransport(new PingTransport());
					//System.out.println("Ping was "+(ponger.ping(new MysterAddress("127.0.0.1"))?"a success":"a timeout"));
				} catch (Exception ex) {
					ex.printStackTrace();
					com.general.util.AnswerDialog.simpleAlert("Myster's UDP sub-system could not initialize. This means Myster will probably not work correctly. Here is the official error:\n\n"+ex);
				}
				
				progress.setText(I18n.tr("Loading Server Stats Window... %1%%", ""+15)+macHack);
				progress.setValue(15);
				
				//System.out.println( "MAIN THREAD: Starting Operator.."+macHack);
				Point p=ServerStatsWindow.getInstance().getLocation();
				ServerStatsWindow.getInstance().setLocation(-500,-500);
				ServerStatsWindow.getInstance().setVisible(true);
				progress.setText(I18n.tr("Loading Server Stats Window... %1%%", ""+18)+macHack);
				progress.setValue(18);
				//try {Thread.currentThread().sleep(1000);} catch (Exception ex) {}
				ServerStatsWindow.getInstance().setVisible(false);
				ServerStatsWindow.getInstance().setLocation(p);
				
				progress.setText(I18n.tr("Loading Server Fascade...")+macHack);
				progress.setValue(25);
				ServerFacade.assertServer();
				
				progress.setText(I18n.tr("Loading tracker...")+macHack);
				progress.setValue(50);
				IPListManagerSingleton.getIPListManager();
				
				progress.setText(I18n.tr("Loading FileManager...")+macHack);
				progress.setValue(70);
				FileTypeListManager.getInstance();
				
				progress.setText(I18n.tr("Loading Instant Messaging...")+macHack);
				progress.setValue(72);
				com.myster.message.MessageManager.init();
				
				progress.setText(I18n.tr(I18n.tr("Loading WindowManager..."))+macHack);
				progress.setValue(78);
				com.myster.ui.WindowManager.init();
				
				Preferences.getInstance().addPanel(BandwidthManager.getPrefsPanel());
			
				progress.setText(I18n.tr("Loading Plugins...")+macHack);
				progress.setValue(80);
				try {
					(new com.myster.plugin.PluginLoader(new File("plugins"))).loadPlugins();
				} catch (Exception ex) {}
				
				com.myster.hash.ui.HashPreferences.init(); //meep
				
				com.myster.type.ui.TypeManagerPreferencesGUI.init();
				
				com.myster.hash.HashManager.start();
				
				//progress.done();
				progress.setVisible(false);
				//progress.dispose();
				
				if (isServer) {}
				else {
					Preferences.initWindowLocations();
					com.myster.client.ui.ClientWindow.initWindowLocations();
					ServerStatsWindow.initWindowLocations();
					com.myster.tracker.ui.TrackerWindow.initWindowLocations();
					SearchWindow.initWindowLocations();
					com.myster.hash.ui.HashManagerGUI.initGui();
					Preferences.initGui();
					
					//SearchWindow sw=new SearchWindow();
					//sw.say(I18n.tr("Idle.."));
				}
				
				/*
				System.out.println("Hello????");
				try {
					System.out.println("-------------->"+com.myster.client.stream.StandardSuite.getFileFromHash(new MysterAddress("127.0.0.1"), new com.myster.type.MysterType((new String("MPG3")).getBytes()), FileTypeListManager.getInstance().getFileItem(new com.myster.type.MysterType((new String("MPG3")).getBytes()), "moo.mp3").getHash("md5")));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				System.out.println("Hello????     ....");
				*/
			}
		}).start();
	}
	
	
	//Utils, globals etc..
	
	//These variables are System wide variables that 
	//dictate how long things are or what port were's on or whatever...
	public static final int PORT=6669;			//Default port.
	public static final String SPEEDPATH="Globals/speed/";
	public static final String ADDRESSPATH="Globals/address/";
	public static final String DEFAULT_ENCODING = "ascii";
	

	//public static double speed=14.4;
	//public static int files=200;
	
	public static void start() {
		File file =new File(fileLockName);
		if (file.exists()) {
			fileExists(file);
		} else {
			try {
				newFile(file);
			} catch (Exception ex) {
				
			}
		}
	}
	
	public static void fileExists(File file) {
		try {
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			int passWord=in.readInt();
			int port=in.readInt();
			
			try {
				Socket socket=new Socket(InetAddress.getLocalHost(), 10457);
				DataInputStream sin=new DataInputStream(socket.getInputStream());
				DataOutputStream sout=new DataOutputStream(socket.getOutputStream());
				
				sout.writeInt(passWord);
				sout.writeInt(0); //a command
				if (sin.read()==1) {
					System.out.println("Sucess, other myster client should put up a new window");
					System.exit(0);
				} else {
					System.out.println("Other Myster Prog wrote back error of type: ??");
					System.exit(1);
				}
			
			} catch (Exception ex) {
				newFile(file);
				System.out.println("Could not connect to self... Deleting the lock file");
			}
		} catch (Exception ex) {
			System.out.println("Big error, chief. Now would be a good time to panic.");
			ex.printStackTrace();System.exit(1);
			
		}
	}
	
	public static int password=-1;
	public static void newFile(File file) {
		try {
			DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
			Math.random();
			Math.random();
			Math.random();
			double temp=Math.random();
			password=(int)(32000*temp);
			
			(new Thread(){
				public void run() {
					try {
						DataInputStream sin;
						DataOutputStream sout;
						ServerSocket serversocket=new ServerSocket(10457,1,InetAddress.getLocalHost());
						
						for (;;) {
							Socket socket=serversocket.accept();
							sin=new DataInputStream(socket.getInputStream());
							sout=new DataOutputStream(socket.getOutputStream());
							
							try {
								System.out.println("getting connection form self");
								sin.readInt();
								sin.readInt();
								sout.write(1);
								SearchWindow sw=new SearchWindow();
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							
							try {socket.close();} catch (Exception ex){}
						}
					} catch (Exception ex) {
						ex.printStackTrace();
						AnswerDialog.simpleAlert("Could not connect on Local socket..\n"+"Make sur eyou are connected to the internet, close all open Myster versions and/or restart.");
						System.out.println("Could not connect on Local socket..");
						System.out.println("Close all open Myster versions or just restart.. that should work");
						System.exit(1);
					}
				}
			}).start();
			
			out.writeInt(password);
			out.writeInt(10457);
			out.close();
		} catch (Exception ex) {
			System.out.println("Big error, chief.");
			ex.printStackTrace();
		}
	}
	
	/**
	* Instead of calling System.exit() directly to quit, call this routine. It makes shure cleanup is done.
	*
	* NOTE: It's a very fequent occurence for the program to quit without calling this routine
	*		 so your code should in no  way depend on it. (Some platform do not call this at all
	*		 when quitting!).
	*/
	public static void quit() {
		//Preferences.getInstance().flush(); //flushes prefs to disk.
		//File file =new File(fileLockName);
		//if (file.exists()) {
		//	file.delete();
		//}
		System.out.println("Byeeeee.");
		System.exit(0);
	}
	
	/**
	*	Returns the time that was returned by System.currentTimeMillis when the program was first launched.
	*/
	public static long getLaunchedTime() {
		return programLaunchTime;
	}
}
