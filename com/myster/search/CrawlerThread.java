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
import com.myster.search.IPQueue;
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
	GroupInt group;
	MysterSocket socket;
	Sayable msg;
	
	boolean endFlag=false;

	public final int DEPTH=20;

	public CrawlerThread(MysterSearchClientSection searcher, MysterType type, IPQueue iplist,Sayable msg, GroupInt i) {
		super("Crawler Thread " + type);
		this.ipQueue=iplist;
		this.searchType=type;
		this.msg=msg;
		this.searcher=searcher;
		
		group=(i==null?new GroupInt():i); //MASSIVE CHEAT! Fix this.
	}
	
	/** The thread does a top ten then searchs.. It only does a top ten on the first few IPs, so we don't
	* Do an insane flood..
	* This routine is responsible for most of the search.
	*/
	
	public void run() {
		int counter=0;

		for (MysterAddress currentIp=ipQueue.getNextIP(); currentIp!=null||counter==0; currentIp=ipQueue.getNextIP()) {
			try {
				counter++;
				if (currentIp==null) {
					try {System.out.println("!CRAWLER THREAD FIRST LINE WOOOOO");
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
				
				socket=MysterSocketFactory.makeStreamConnection(currentIp);
				
				if (endFlag) {
					cleanUp();
					return;
				}
				
				if (counter<DEPTH) {
			
					Vector ipList=StandardSuite.getTopServers(socket, searchType);
					
						if (endFlag) {
							cleanUp();
							return;
						}
					
					for (int i=0; i<ipList.size(); i++) {
						try {
							MysterAddress temp=new MysterAddress((String)(ipList.elementAt(i)));
							ipQueue.addIP(temp);
							IPListManagerSingleton.getIPListManager().addIP(temp);
						} catch (UnknownHostException es) {
							//nothing.
						}
					}
				}
				
				if (endFlag) {
					cleanUp();
					return;
				}
				
				searcher.search(socket, currentIp);
				
				msg.say("Searched "+ipQueue.getIndexNumber()+" Myster servers.");
				//don't close the connection... It's being used by the getting thread...
				
			} catch (IOException ex) {
				if (socket!=null) {
					try {
						socket.close();
					} catch (IOException exp){}
				}
			}

		}
		
		
		if (group.subtractOne()<=0) msg.say("Done search");
		else msg.say("Still Searching: "+group.getValue()+" outstanding searches");
		
		
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

		
		try {
			socket.close();
		} catch (Exception ex) {
			System.out.println("Crawler thread was not happy about being asked to close.");
		}
		
	}
}