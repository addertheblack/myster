



package com.myster.server;

import java.util.StringTokenizer;
import java.awt.*;

import com.myster.server.event.ServerEventManager;
import com.myster.pref.Preferences;
import com.myster.pref.ui.PreferencesPanel;
import com.myster.transferqueue.TransferQueue;

public class ServerFacade {
	static Operator opp;
	static boolean b=true;
	static TransferQueue downloadQueue;
	
	/**
		call this if you code assumes the server is actively running or you wish to start the server.
		This routine should not be called by the user, only the system startup thread.
	*/
	public static synchronized void assertServer() {
		if (b) {
			opp.start();
			b=false;
		}
	}
	
	private static synchronized Operator getInstance() {
		if (opp==null) {
			BannersManager.init(); //init banners stuff..
		
			downloadQueue = new ServerQueue();
			opp=new Operator(downloadQueue, getServerThreads());
			Preferences.getInstance().addPanel(new PrefPanel());
			addStandardConnectionSections();
		}
		return opp;
	}
	
	/**
		Gets the server event dispatcher. Usefull if you want your module to listen for SERVER events.
	*/
	public static ServerEventManager getServerDispatcher() {
		return getInstance().getDispatcher();
	}	
	
	
	private static String identityKey="ServerIdentityKey/";
	protected static void setIdentity(String s) {
		if (s==null) return;
		Preferences.getInstance().put(identityKey, s);
	}
	
	public static String getIdentity() {
		return Preferences.getInstance().query(identityKey);
	}
	
	public static void addConnectionSection(ConnectionSection section) {
		opp.addConnectionSection(section);
	}
	
	private static String serverThreadKey="MysterTCPServerThreads/";
	private static int getServerThreads() {
		String info=Preferences.getInstance().get(serverThreadKey);
		if (info==null) info="35"; //default value;
		try {
			return Integer.parseInt(info);
		} catch (NumberFormatException ex) {
			return 35; //should *NEVER* happen.
		}
	}
	
	private static void setServerThreads(int i) {
		Preferences.getInstance().put(serverThreadKey, ""+i);
	}
	
	private static void addStandardConnectionSections() {
		opp.addConnectionSection(new com.myster.server.stream.IPLister());
		opp.addConnectionSection(new com.myster.server.stream.RequestDirThread());
		opp.addConnectionSection(new com.myster.server.stream.FileSenderThread());
		opp.addConnectionSection(new com.myster.server.stream.FileTypeLister());
		opp.addConnectionSection(new com.myster.server.stream.RequestSearchThread());
		opp.addConnectionSection(new com.myster.server.stream.HandshakeThread());
		opp.addConnectionSection(new com.myster.server.stream.FileInfoLister());
		opp.addConnectionSection(new com.myster.server.stream.FileByHash());
		opp.addConnectionSection(new com.myster.server.stream.MultiSourceSender());
	}
	
	private static void setDownloadSpots(int spots) {
		downloadQueue.setDownloadSpots(spots);
	}
	
	private static int getDownloadSpots() {
		return downloadQueue.getDownloadSpots();
	}
	
	private static class PrefPanel extends PreferencesPanel {
		private final TextField serverIdentityField;
		private final Label serverIdentityLabel;
		
		private final Choice openSlotChoice;
		private final Label openSlotLabel;
		
		private final Choice serverThreadsChoice;
		private final Label serverThreadsLabel;
		
		private final Label spacerLabel;
		
		private final Label explanation;
		
		private final com.myster.server.stream.FileSenderThread.FreeLoaderPref leech;
		
		private PrefPanel () {
			//setBackground(Color.red);
			setLayout(new GridLayout(5,2,5,5));
			
			openSlotLabel=new Label("Download Spots: *");
			add(openSlotLabel);
			
			openSlotChoice=new Choice();
			for (int i = 2; i<=10; i++){
				openSlotChoice.add(""+i);
			}
			add(openSlotChoice);
			
			serverThreadsLabel=new Label("Server Threads: * (expert setting)");
			add(serverThreadsLabel);
			
			serverThreadsChoice=new Choice();
			serverThreadsChoice.add(""+35);
			serverThreadsChoice.add(""+40);
			serverThreadsChoice.add(""+60);
			serverThreadsChoice.add(""+80);
			serverThreadsChoice.add(""+120);
			add(serverThreadsChoice);
			
			serverIdentityLabel=new Label("Server Identity:");
			add(serverIdentityLabel);
			
			serverIdentityField=new TextField();
			add(serverIdentityField);
			
			spacerLabel=new Label();
			add(spacerLabel);
			
			leech=com.myster.server.stream.FileSenderThread.getPrefPanel();
			add(leech);
			
			explanation=new Label("          * requires restart");
			add(explanation);
			
			reset();
		}
	
		public Dimension getPreferredSize() {
			return new Dimension(STD_XSIZE, 140);
		}

		public String getKey() {
			return "Server";
		}
		
		public void save() {
			ServerFacade.setIdentity(serverIdentityField.getText());
			ServerFacade.setDownloadSpots(Integer.parseInt(openSlotChoice.getSelectedItem()));
			setServerThreads(Integer.parseInt((new StringTokenizer(serverThreadsChoice.getSelectedItem()," ")).nextToken()));
			leech.save();
		}
		
		public void reset() {
			serverIdentityField.setText(ServerFacade.getIdentity());
			openSlotChoice.select(""+ServerFacade.getDownloadSpots());
			serverThreadsChoice.select(""+getServerThreads());
			leech.reset();
		}
		
	}
}