/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001



*/

package com.myster.search;

import java.awt.event.*;
import java.awt.*;
import com.general.util.*;
import com.myster.tracker.*;
import com.general.util.*;
import com.myster.search.CrawlerThread;
import com.myster.search.IPQueue;
import com.myster.search.MysterFileStub;
import com.myster.search.SearchResultListener;
import java.net.UnknownHostException;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;
import com.myster.net.MysterAddress;
import com.myster.type.MysterType;




public class MysterSearch extends MysterThread {
	Sayable msg;
	CrawlerThread t[];
	MysterType type;
	
	StandardMysterSearch searcher;


	public MysterSearch(SearchResultListener listener, Sayable msg, MysterType type, String searchString) {
		this.msg=msg;
		this.searcher = new StandardMysterSearch(searchString, listener);
		this.type = type;
		
		t=new CrawlerThread[15];
	}
	
	public void run() {
		msg.say("SEARCH: Starting Search..");
		
		searcher.start();
		
		MysterServer[] iparray=IPListManagerSingleton.getIPListManager().getTop(type,50);
		
		IPQueue queue=new IPQueue();
		GroupInt group=new GroupInt();
		
		int i=0;
		
		for (i=0; (i<iparray.length)&&(iparray[i]!=null); i++) {
			queue.addIP(iparray[i].getAddress());
		}
		
		if (i==0) {
			String[] lastresort=IPListManagerSingleton.getIPListManager().getOnRamps();
			//msg.say("ERROR: No search done. Add an IP using the \"AddIP\" command.");
			//add extra IPs to boot strap the searching
			for (int j=0; j<lastresort.length; j++) {
				try {
					queue.addIP(new MysterAddress(lastresort[j]));
					System.out.println("last resort: "+lastresort[j]);
				} catch (UnknownHostException ex) {
					//..
				}
			}
		}
		
		
		for (i=0; i<t.length; i++) {
			t[i]=new CrawlerThread(searcher,type ,  queue, msg,group);
			group.addOne();
			t[i].start();
			msg.say("Starting a new Search Thread...");
			
		}
		
		msg.say("Launched "+i+" search threads");
		System.out.println("Launched "+i+" search threads");	
		
		for (int index=0; index<t.length; index++) {
			try {t[index].join();} catch (InterruptedException ex) {}	// slow: change someday.
		}
		
		searcher.done();
	}
	
	public void flagToEnd() {
		for (int i=0; i<IPListManager.LISTSIZE; i++) {
			try {t[i].flagToEnd();} catch (Exception ex) {}	// slow: change someday.
		}
		
		searcher.flagToEnd();
		
	}
	
	public void end() {
		msg.say("Stopping previous search threads (if any)..");
		//Stops all previous threads
		flagToEnd();
		try {
			join();
		} catch (Exception ex) {}
		
		searcher.end();
	}
	
	

}