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
import com.myster.message.MessageManager;

public class Myster{

	public static File file;
	public static final String fileLockName=".lockFile";

	private static ResourceBundle resources;

	public static void main(String args[]) {
		final boolean isServer=(args.length>0&&args[0].equals("-s"));
		

		
		System.out.println("java.vm.specification.version:"+System.getProperty("java.vm.specification.version"));
		System.out.println("java.vm.specification.vendor :"+System.getProperty("java.vm.specification.vendor"));
		System.out.println("java.vm.specification.name   :"+System.getProperty("java.vm.specification.name"));
		System.out.println("java.vm.version              :"+System.getProperty("java.vm.version"));
		System.out.println("java.vm.vendor               :"+System.getProperty("java.vm.vendor"));
		System.out.println("java.vm.name                 :"+System.getProperty("java.vm.name"));
		
		
		
		//Locale.setDefault(new Locale(Locale.JAPANESE.getLanguage(),Locale.JAPAN.getCountry()));
		/*
		try {
			resources = ResourceBundle.getBundle("com.properties.Myster");
		}
		catch (MissingResourceException e) {
			System.err.println("resources not found");
			//System.exit(1);
		}*/
		
		/*
		int i_temp=0;
		byte[] ping=(new String("PI")).getBytes();
		for (int i=0; i<ping.length; i++) {
			i_temp<<=8;
			i_temp|=255 & ((int)ping[i]);
			
		}
		System.out.println(""+ i_temp);
		*/

		/*
		Useless code:
		System.setProperty("sun.net.inetaddr.ttl", "0");	//gets around DNS caching problem. not supported in 1.1
		*/


/*
		try {
		TransactionManager.addTransactionProtocol(new TransactionProtocol() {
			public  int getTransactionCode() {
				return 666;
			}
			
			public void transactionReceived(Transaction transaction)  {
				System.out.println("Got a transaction!.");
				sendTransaction(new Transaction(transaction, new byte[0]));
			}
		});
		
		final MysterAddress address=new MysterAddress("127.0.0.1");
		
		TransactionSocket s=new TransactionSocket(address, 666);
		
		DataPacket data=new DataPacket() {
				public MysterAddress getAddress() { return address; }
				public byte[] getData() { return new byte[1]; }
				public byte[] getBytes()	{ return new byte[1]; }
				public byte[] getHeader() 	{ return new byte[1]; }
		};
		
		s.sendTransaction(data, new TransactionListener() {
				public  void transactionReply(TransactionEvent e) { System.out.println("REPLY!"); }
				public  void transactionTimout(TransactionEvent e) { System.out.println("TIMOUT!");}
		});

		} catch (Exception ex) {
			ex.printStackTrace();
			return;
		}
		
		if (true==true) return;
*/

		MessageManager.init();
		
		try {
			MessageManager.sendInstantMessage(new MysterAddress("127.0.0.1"), "Hello");
			MessageManager.sendInstantMessage(new MysterAddress("127.0.0.1"), "Hello");
			MessageManager.sendInstantMessage(new MysterAddress("127.0.0.1"), "Hello");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		//if (true==true) return ;
		

		start();
		
		

		
		System.out.println( "MAIN THREAD: Starting loader Thread.." );
		(new Thread() {
			public void run() {
				//if (true) return;
				try {Thread.currentThread().sleep(1000);} catch (Exception ex) {}
				
				String macHack="";//(System.getProperty("java.vm.vendor")==null?" (unknown 1.1 java)":System.getProperty("java.vm.vendor"));
				com.myster.util.ProgressWindow progress=new com.myster.util.ProgressWindow();
				progress.setLocation(100,100);
				progress.setTitle(Myster.tr("Loading Myster..."));
				progress.showBytes=false;
				
				
				progress.say(Myster.tr("Loading UDP Operator...")+macHack);
				progress.update(10);
				try { //this stuff is a bit of a hack.. to be fixed later..
					PongTransport ponger=new PongTransport();UDPPingClient.setPonger(ponger);
					DatagramProtocolManager.addTransport(ponger);
					DatagramProtocolManager.addTransport(new PingTransport());
					//System.out.println("Ping was "+(ponger.ping(new MysterAddress("127.0.0.1"))?"a success":"a timeout"));
				} catch (Exception ex) {
					ex.printStackTrace();
					com.general.util.AnswerDialog.simpleAlert("Myster's UDP sub-system could not initialize. This means Myster will probably not work correctly. Here is the official error:\n\n"+ex);
				}
				
				progress.say(Myster.tr("Loading Server Stats Window...")+macHack);
				progress.update(15);
				
				//System.out.println( "MAIN THREAD: Starting Operator.."+macHack);
				Point p=ServerStatsWindow.getInstance().getLocation();
				ServerStatsWindow.getInstance().setLocation(-1111,-1111);
				ServerStatsWindow.getInstance().setVisible(true);
				progress.say(Myster.tr("Loading Server Stats Window....")+macHack);
				try {Thread.currentThread().sleep(1000);} catch (Exception ex) {}
				ServerStatsWindow.getInstance().setVisible(false);
				ServerStatsWindow.getInstance().setLocation(p);
				
				progress.say(Myster.tr("Loading Server Fascade...")+macHack);
				progress.update(25);
				ServerFacade.assertServer();

				progress.say(Myster.tr("Loading a search window...")+macHack);
				progress.update(27);
				//progress.setVisible(false);
				progress.setVisible(true);
				
				progress.say(Myster.tr("Loading tracker...")+macHack);
				progress.update(50);
				IPListManagerSingleton.getIPListManager();
				
				progress.say(Myster.tr("Loading FileManager...")+macHack);
				progress.update(70);
				FileTypeListManager.getInstance();
				
				progress.say(Myster.tr(Myster.tr("Loading WindowManager..."))+macHack);
				progress.update(78);
				com.myster.ui.WindowManager.init();
				
				Preferences.getInstance().addPanel(BandwidthManager.getPrefsPanel());
			
				progress.say(Myster.tr("Loading Plugins...")+macHack);
				progress.update(80);
				try {
					(new com.myster.plugin.PluginLoader(new File("plugins"))).loadPlugins();
				} catch (Exception ex) {}
				
				progress.done();
				progress.setVisible(false);
				progress.dispose();
				
				if (isServer) {}
				else {
					Preferences.initWindowLocations();
					com.myster.client.ui.ClientWindow.initWindowLocations();
					ServerStatsWindow.initWindowLocations();
					com.myster.tracker.ui.TrackerWindow.initWindowLocations();
					SearchWindow.initWindowLocations();
					
					
					
					//SearchWindow sw=new SearchWindow();
					//sw.say(Myster.tr("Idle.."));
				}
		
			}
		}).start();
	}
	
	
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
		Preferences.getInstance().flush(); //flushes prefs to disk.
		File file =new File(fileLockName);
		if (file.exists()) {
			file.delete();
		}
		System.exit(0);
	}

	public static final String tr(String text) {
		if (true==true) return text;
		
		try {
			return resources.getString(text);
		} catch (MissingResourceException ex) {
			//System.err.println("missing translation key: \"" + text + "\"");
			//ex.printStackTrace();
			return text;
		}
	}
}
