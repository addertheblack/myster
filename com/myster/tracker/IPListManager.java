/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.tracker;

import java.net.*;
import java.io.*;
import com.general.util.*;
import java.util.Vector;
import com.myster.search.IPQueue;
import com.myster.client.stream.StandardSuite;
import com.myster.util.MysterThread;
import com.myster.type.TypeDescription;
import com.myster.util.TypeChoice;
import com.myster.net.MysterAddress;
import com.myster.client.datagram.PingEventListener;
import com.myster.client.datagram.PingEvent;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeDescription;
import com.myster.type.MysterType;


public class IPListManager { //aka tracker
	public static final int LISTSIZE=100;		//Size of any given list..
	
	String[] lastresort={"bigmacs.homeip.net","mysternetworks.homeip.net",
			 "mysternetworks.dyndns.org", "emaline.homeip.net","myster.homeip.net"
			 };
	
	IPList[] list;
	TypeDescription[] tdlist;

	IPList iplist;
	
	BlockingQueue blockingQueue=new BlockingQueue();
	
	AddIP[] adderWorkers=new AddIP[3];

	protected IPListManager() {
		blockingQueue.setRejectDuplicates(true);
	
		tdlist = TypeDescriptionList.getDefault().getEnabledTypes();
		
		list=new IPList[tdlist.length];
		for (int i=0; i<list.length; i++) {
			assertIndex(i); //loads all lists.
		}
		
		for (int i=0; i<adderWorkers.length; i++){
			adderWorkers[i]=new AddIP();
			adderWorkers[i].start();
		}
		
		(new IPWalker()).start();
	}
	
	private Callback pingEventListener=new Callback();
	public void addIP(MysterAddress ip) {
		try {
			com.myster.client.datagram.UDPPingClient.ping(ip, pingEventListener); //temporary.. should be inside tracker...
		} catch (IOException ex) {
			ex.printStackTrace();
		}	
	}
	
	private class Callback extends PingEventListener {
		public void pingReply(PingEvent e) {
			if (e.isTimeout()) return; //dead ip.
			MysterAddress ip=e.getAddress();
			MysterIPPool pool=MysterIPPool.getInstance();
			MysterServer mysterServer;
	
			
			mysterServer=pool.getMysterIPLevelOne(ip);
			
			
			//Error conditions first.
			if (mysterServer!=null) {
				addIPBlocking(mysterServer); //if not truely new then don't make a new thread.
			} else if (blockingQueue.length()>100) {
				System.out.println("->   !!!!!!!!!!!!!!!!!!!!!!AddIP queue is at Max length.");
			} else {
				blockingQueue.add(ip); //if it's truely new then make a new thread to passively add the ip
			}
		}
	}
	
	public synchronized MysterServer[] getTopTen(MysterType type) {
		return getTop(type, 10);
	}
	
	/**
	*	Returns a list of Myster servers. Returns only server that have been confirmed UP in
	* 	the last 5 minutes or whatever the ping polling time is. If there are not enough UP
	*	Servers orwhatever, the rest of the array is filled with null!!!!!
	*/	
	public synchronized MysterServer[] getTop(MysterType type, int x) {
		IPList iplist;
		iplist=getListFromType(type);
		if (iplist==null) return null;
		return iplist.getTop(x);

	}
	
	/**
	*	Asks the cache if it knows of this MysterServer and gets stats if it does
	*	else returns null
	*/
	public synchronized MysterServer getQuickServerStats(MysterAddress address) { //returns null if IP is not in the pool.
		return MysterIPPool.getInstance().getMysterIPLevelOne(address);
	}
	
	/**
	*	Gets MysterServer from cache or creates if it is available else
	*	creates in with an IO opperation else throws IOException is server is down.
	*/
	public synchronized MysterServer getServerStats(MysterAddress address) throws IOException { //might block for a long time.
		return MysterIPPool.getInstance().getMysterServer(address);
	}
	
	/**
	*	Returns vector of MysterAddress of all the server addresses for that type.
	*/
	public synchronized Vector getAll(MysterType type) {
		IPList iplist;
		iplist=getListFromType(type);
		if (iplist==null) return null;
		return iplist.getAll();
	}
	
	public String[] getOnRamps() {
		String[] temp=new String[lastresort.length];
		System.arraycopy(lastresort, 0, temp, 0, lastresort.length);
		return temp;
	}
	
	private synchronized IPList getListFromIndex(int index) {
		assertIndex(index);
		return list[index];
	}
	
	
	/**
		This routine is here so that the ADDIP Thread can add an com.myster to all lists and the ADDIP Function can add an ip assuming that
		the IP exists already.
	*/
	private void addIPBlocking(MysterServer ip) {
		for (int i=0; i<tdlist.length; i++) {
			assertIndex(i);
			list[i].addIP(ip);
		}
	}
	
	/**
	*	This function looks returns a list of type type is such a list exists.
	*	If no such list exists it returns null.
	*
	*/
	
	private IPList getListFromType(MysterType type) {
		int index;
		index=getIndex(type);
		
		if (index==-1) return null;
		
		assertIndex(index);
		
		if (list[index].getType().equals(type)) return list[index];

		return null;
	}
	
	
	
	/**
		For dynamic loading Note.. this dynamic loading is thread safe!
	*/
	private synchronized void assertIndex(int index) {
		if (list[index]==null) {
			list[index]=createNewList(index);
			System.out.println("Loaded List "+list[index].getType());
		}
	}
	
	private synchronized int getIndex(MysterType type) {
		for (int i=0; i<tdlist.length; i++) {
			if (tdlist[i].getType().equals(type)) return i;
		}
		return -1;
	}
	
	private synchronized IPList createNewList(int index) {
		return (new IPList(tdlist[index].getType()));
	}

		
	/**
	*	What follows is basically the tracker as stated in the DOCS
	*	. Currently Myster polls the i-net.. it should only crawl when something is triggered...
	*	Like when a search is done.. ideally, the IPs discovered during a crawl should be fed back
	*	to the "tracker" portion. The downside is servers do no crawling.. (bad)
	*
	*/
	private class IPWalker extends MysterThread {

		/**
		*
		*	protocal for handshake is Send 101, his # of file, his speed. 
		*
		*/
		
		public IPWalker() {
			super("IPWalker thread");
		}
		
		
		public void run() {
			
			System.out.println("Starting walker thread");
			setPriority(Thread.MIN_PRIORITY); //slightly better than a deamon thread.
			RInt rcounter=new RInt(tdlist.length-1);
			try {sleep(10*1000);} catch (InterruptedException ex) {}
			MysterServer[] iplist=getListFromIndex(rcounter.getVal()).getTop(10);
			try {sleep(10*60*1000);} catch (InterruptedException ex) {} //wait 10 minutes for the list to calm down 
				//if this trick is omited, the list spends ages sorting through a load of ips that aren't up.
			while(true) {
				System.out.println("CRAWLER THREAD: Starting new automatic crawl for new IPS");
				iplist=getListFromIndex(rcounter.getVal()).getTop(10);
				IPQueue ipqueue=new IPQueue();
				
				int max=0;
				
				String[] onramps=getOnRamps();
				for (int i=0; i<onramps.length; i++) {
					try {
						ipqueue.addIP(new MysterAddress(onramps[i]));
					} catch (UnknownHostException ex) {
						System.out.println("One of the arra of last resort is spelled wrong: "+onramps[i]);
					}
					max++;
				}
				
				for (int i=0; i<iplist.length && iplist[i]!=null; i++) { 
					ipqueue.addIP(iplist[i].getAddress());
					max++;
				}
				
				
				int i=0;
				for (MysterAddress ip=ipqueue.getNextIP(); ip!=null; ip=ipqueue.getNextIP()) {
					try {
						if (i<=max+50) addIPs(ip, ipqueue, getListFromIndex(rcounter.getVal()).getType());
					} catch (IOException ex) {
						//nothing.
					}
					if (i>=max) addIP(ip); //..
					i++;
				}
				
				System.out.println("CRAWLER THREAD: Going to sleep for a while.. Good night. Zzz.");
				try {sleep(30*1000*60);} catch (InterruptedException ex) {} //300 000ms = 5 mins.
				rcounter.inc();
			}
		}
		
		private void addIPs(MysterAddress ip, IPQueue ipQueue, MysterType type) throws IOException {
			Vector ipList=StandardSuite.getTopServers(ip, type);
			
			for (int i=0; i<ipList.size(); i++) {
				try {
					ipQueue.addIP(new MysterAddress((String)(ipList.elementAt(i))));
				} catch (UnknownHostException ex) {}
			}

		}
		
	}
static volatile int counter=0;
	private class AddIP extends MysterThread {
		
		public AddIP() {
			super("AddIP Thread");
		}
		
		
		public void run() {
			MysterAddress ip=null;
			for(;;) {
				try {
					ip=(MysterAddress)(blockingQueue.get());//BlockingQueue
					
					counter++;
					//System.out.println("AddIP: ip check out. Crawler threads active: "+counter);
					
					//System.out.println("ADDER : Got "+ip+" from the queue.");
					MysterServer mysterserver=null;
					try {
						mysterserver=MysterIPPool.getInstance().getMysterServer(ip);
						if (mysterserver==null) continue;
					} catch (IOException ex) {
						continue;
					}
	
					addIPBlocking(mysterserver);

				} catch (Exception ex) {
					ex.printStackTrace();
				} finally {
					
					counter--;
					//System.out.println("AddIP: ip check out. Crawler threads active: "+counter);
				}
			}
			//Statement not reached
		}
	}
}


