/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.search;

import java.net.*;
import java.io.*;
import com.general.util.*;
import java.util.Vector;
import com.myster.tracker.*;
import com.myster.net.MysterSocket;
import com.myster.client.stream.StandardSuite;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;
import com.myster.net.MysterSocketFactory;
import com.myster.net.MysterAddress;
import com.myster.type.MysterType;

public class CrawlerThread extends MysterThread {
	MysterType searchType;
	IPQueue ipQueue;
	MysterSearchClientSection searcher;
	MysterSocket socket;
	Sayable msg;
	
	boolean endFlag=false;

	public final int DEPTH=20;

	public CrawlerThread(MysterSearchClientSection searcher, MysterType type, IPQueue iplist,Sayable msg) {
		super("Crawler Thread " + type);
		this.ipQueue=iplist;
		this.searchType=type;
		this.msg=msg;
		this.searcher=searcher;
	}
	
	/** The thread does a top ten then searchs.. It only does a top ten on the first few IPs, so we don't
	* Do an insane flood..
	* This routine is responsible for most of the search.
	*/
	
	public void run() {
		int counter=0;
		
		//System.out.println("!CRAWLER THREAD Starting the crawl");
		
		try {
			
			for (MysterAddress currentIp=ipQueue.getNextIP(); currentIp!=null||counter==0; currentIp=ipQueue.getNextIP()) {
				try {
				
					
					counter++;
					if (currentIp==null) {
						try {
							sleep(10*1000); //wait 10 seconds for more ips to come in.
							continue;
						} catch (InterruptedException ex) {
							continue;
						}
					}
				
					if (endFlag) {
						cleanUp();
						return;
					}

					socket = null;
					
					if (endFlag) {
						cleanUp();
						return;
					}
					
					if (counter<DEPTH) {
						MysterAddress[] addresses;
						try {
							addresses = com.myster.client.datagram.StandardDatagramSuite.getTopServers(currentIp, searchType);
							
							System.out.println("We got a UDP top ten!");
						} catch (IOException ex) {
							if (endFlag) {
								cleanUp();
								return;
							}
						
							socket = MysterSocketFactory.makeStreamConnection(currentIp);
							Vector ipList = StandardSuite.getTopServers(socket, searchType);
							
							addresses = new MysterAddress[ipList.size()];
							for (int i = 0; i<addresses.length; i++) addresses[i] = new MysterAddress((String)(ipList.elementAt(i)));
							
							System.out.println("We got a TCP top ten! -> " + ex);
						}
						
						if (endFlag) {
							cleanUp();
							return;
						}
						
						for (int i=0; i<addresses.length; i++) {
							ipQueue.addIP(addresses[i]);
							IPListManagerSingleton.getIPListManager().addIP(addresses[i]);
						}
					}
					
					if (endFlag) {
						cleanUp();
						return;
					}
					
					if (socket == null) socket = MysterSocketFactory.makeStreamConnection(currentIp);
					searcher.search(socket, currentIp, searchType);
					
					StandardSuite.disconnectWithoutException(socket);
				} catch (IOException ex) {
					if (socket!=null) {
						try {
							socket.close();
						} catch (IOException exp){}
					}
				}
				
				msg.say("Searched "+ipQueue.getIndexNumber()+" Myster servers.");

			}
			
			searcher.searchedAll(searchType);
		} finally {
			searcher.endSearch(searchType);
		}
		
	}
	
	private void cleanUp() {
		
		try {
			socket.close() ;
		} catch (Exception ex) {
		
		}
	}
	
	public void end() {
		flagToEnd();
		
		try {
			join();
		} catch (InterruptedException ex) {}
	}
	
	public void flagToEnd() {
		endFlag=true;

		searcher.flagToEnd();
		
		try {
			socket.close();
		} catch (Exception ex) {
			System.out.println("Crawler thread was not happy about being asked to close.");
		}
		
		interrupt();
		
	}
}