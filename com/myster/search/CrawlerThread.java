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

public class CrawlerThread extends MysterThread {
	MysterAddress currentIp;
	String searchString, searchType;
	IPQueue ipQueue;
	SearchResultListener bucket;
	Sayable msg;
	Vector filestatsvector;
	GroupInt group;
	MysterSocket socket;
	
	boolean endFlag=false;

	public final int DEPTH=20;

	public CrawlerThread(String searchString, String type, SearchResultListener bucket, IPQueue iplist, Sayable msg, GroupInt i) {
		this.ipQueue=iplist;
		this.searchString=searchString;
		this.searchType=type;
		this.bucket=bucket;
		this.msg=msg;
		filestatsvector=new Vector(10,10);
		group=i;
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
				
				Vector searchResults=StandardSuite.getSearch(socket, searchType, searchString);

				if (endFlag) {
					cleanUp();
					return;
				}

				if (searchResults.size()!=0) {
					msg.say("Results found....");
					
					FileInfoGetter getter=new FileInfoGetter(socket, bucket,currentIp, searchType, searchResults);
					
					filestatsvector.addElement(getter);
					
					if (endFlag) {
						cleanUp();
						return;
					}
					
					getter.start();
				}
				
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
		
		endOthers();
		
		try {
			join();
		} catch (InterruptedException ex) {}
	}
	
	public void flagToEnd() {
		endFlag=true;
		for (int i=0; i<filestatsvector.size(); i++) {
			((FileInfoGetter)(filestatsvector.elementAt(i))).flagToEnd();	//tells all the threads to cancel
		}
		
		
		try {
			socket.close();
		} catch (Exception ex) {
			System.out.println("Crawler thread was not happy about being asked to close.");
		}
		
	}
	
	private void endOthers() {
		for (int i=0; i<filestatsvector.size(); i++) {
			try {
				((FileInfoGetter)(filestatsvector.elementAt(i))).end();
			} catch (Exception ex) {}
		}
	}
}