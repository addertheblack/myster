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
import com.myster.search.ui.SearchWindow;
import com.myster.util.MysterThread;
import com.myster.type.MysterType;



public class SearchEngine extends MysterThread {
	SearchWindow window;
	MysterSearch msearch;


	public SearchEngine(SearchWindow w) {
		window=w;
		
	}
	
	public void run() {
		window.startSearch();
		msearch=new MysterSearch(window,window,window.getType(), window.getSearchString());
		msearch.start();
		
		try {msearch.join();} catch (Exception ex) {}
		
		window.searchOver();
	}
	
	public void end() {
		Waiter w=new Waiter();
		w.start();
		try {
			msearch.end();
		} catch (Exception ex) {}
		w.end();
	}
	
	private class Waiter extends MysterThread {
		boolean dieflag=false;
		TextSpinner s=new TextSpinner();
		
		public void run() {
			do {
				window.say("Waiting for searches to cancel (this may take a while) "+s.getSpin());
				try {sleep(250); } catch (InterruptedException ex) { break;}
			} while (!dieflag);
		}
		
		public void end() {
			dieflag=true;
			interrupt();
			try {join();} catch (InterruptedException ex) {}
		}
	}	

}