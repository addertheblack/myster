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

public class Myster{

	public static File file;
	public static final String fileLockName=".lockFile";

	public static void main(String args[]) {
		final boolean isServer=(args.length>0&&args[0].equals("-s"));
		
		//Locale.setDefault(new Locale(Locale.JAPANESE.getLanguage(),Locale.JAPAN.getCountry()));
		
		System.out.println("java.vm.specification.version:"+System.getProperty("java.vm.specification.version"));
		System.out.println("java.vm.specification.vendor :"+System.getProperty("java.vm.specification.vendor"));
		System.out.println("java.vm.specification.name   :"+System.getProperty("java.vm.specification.name"));
		System.out.println("java.vm.version	             :"+System.getProperty("java.vm.version"));
		System.out.println("java.vm.vendor               :"+System.getProperty("java.vm.vendor"));
		System.out.println("java.vm.name                 :"+System.getProperty("java.vm.name"));
		
		/*
		int i_temp=0;
		byte[] ping=(new String("PONG")).getBytes();
		for (int i=0; i<ping.length; i++) {
			i_temp<<=8;
			i_temp|=255 & ((int)ping[i]);
			
		}
		System.out.println(""+ i_temp);
		*/
		
		/*
		Useless code:
		System.setProperty("sun.net.inetaddr.ttl", "0");	//gets around DNS caching problem. not supported in 1.1
		if (UDPPingClient.ping("127.0.0.1")) System.out.println("yep.");
		else System.out.println("nope.");
		try { Runtime.getRuntime().exec("explorer http://www.apple.com/"); } catch (Exception ex) {}
		*/
		
		/*
		MML mml=new MML();
		
		mml.put("/jack/hill/duck", "this is the value");
		mml.put("/jack/hill/muck", "this is the value1");
		mml.put("/jack/pill", "this is the value3");
		mml.put("/jack/hill/zill", "this is the value4");
		try {
			MML mml2=new MML(mml.toString());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		if (true==true) return;*/
		
		start();
		
		
		try {
			PongTransport ponger=new PongTransport();UDPPingClient.setPonger(ponger);
			DatagramProtocolManager.addTransport(ponger);
			DatagramProtocolManager.addTransport(new PingTransport());
			//System.out.println("Ping was "+(ponger.ping(new MysterAddress("127.0.0.1"))?"a success":"a timeout"));
		} catch (Exception ex) {
			ex.printStackTrace();
			com.general.util.AnswerDialog.simpleAlert("Myster's UDP sub-system could not initialize. This means Myster will probably not work correctly. Here is the official error:\n\n"+ex);
		}


		
		
		System.out.println( "MAIN THREAD: Starting loader Thread.." );
		(new Thread() {
			public void run() {
				//if (true) return;
				try {Thread.currentThread().sleep(1000);} catch (Exception ex) {}
				
				String macHack="";//(System.getProperty("java.vm.vendor")==null?" (unknown 1.1 java)":System.getProperty("java.vm.vendor"));
				com.myster.util.ProgressWindow progress=new com.myster.util.ProgressWindow();
				progress.setLocation(100,100);
				progress.setTitle("Loading Myster...");
				progress.showBytes=false;
				progress.say("Loading UDP Operator..."+macHack);
				progress.update(10);
				
				(new UDPOperator(DatagramProtocolManager.getSocket())).start();
				
				progress.say("Loading Server Stats Window..."+macHack);
				progress.update(15);
				
				//System.out.println( "MAIN THREAD: Starting Operator.."+macHack);
				Point p=ServerStatsWindow.getInstance().getLocation();
				ServerStatsWindow.getInstance().setLocation(-1111,-1111);
				ServerStatsWindow.getInstance().setVisible(true);
				progress.say("Loading Server Stats Window...."+macHack);
				try {Thread.currentThread().sleep(1000);} catch (Exception ex) {}
				progress.say("Loading Server Stats Window....."+macHack);
				ServerStatsWindow.getInstance().setVisible(false);
				ServerStatsWindow.getInstance().setLocation(p);
				
				progress.say("Loading Server Fascade..."+macHack);
				progress.update(25);
				ServerFacade.assertServer();

				progress.say("Loading a search window..."+macHack);
				progress.update(27);
				if (isServer) {}
				else {
					SearchWindow sw=new SearchWindow();
					sw.say("Idle..");
				}
				progress.setVisible(false);
				progress.setVisible(true);
				
				progress.say("Loading tracker..."+macHack);
				progress.update(50);
				IPListManagerSingleton.getIPListManager();
				
				progress.say("Loading FileManager..."+macHack);
				progress.update(70);
				FileTypeListManager.getInstance();
				
				progress.say("Loading WindowManager..."+macHack);
				progress.update(78);
				com.myster.ui.WindowManager.init();
				
				Preferences.getInstance().addPanel(BandwidthManager.getPrefsPanel());
			
				progress.say("Loading Plugins..."+macHack);
				progress.update(80);
				try {
					(new com.myster.plugin.PluginLoader(new File("plugins"))).loadPlugins();
				} catch (Exception ex) {}
				
				progress.done();
				progress.setVisible(false);
				progress.dispose();
			}
		}).start();
	}//
	
	
	//Utils, globals etc..
	
	//These variables are System wide variables that 
	//dictate how long things are or what port were's on or whatever...
	public static final int PORT=6669;			//Default port.
	public static final String SPEEDPATH="Globals/speed/";
	public static final String ADDRESSPATH="Globals/address/";
	

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
			System.out.println("Big error, chief.");
			System.exit(1);
			ex.printStackTrace();
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
								System.out.println("getting conneciton form self");
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
	
	public static void quit() {
		File file =new File(fileLockName);
		if (file.exists()) {
			file.delete();
		}
		System.exit(0);
	}

}
