package com.myster.search;

import java.io.IOException;
import java.util.Vector;

import com.myster.type.MysterType;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;

public class StandardMysterSearch implements MysterSearchClientSection {
	final String searchString;
	final SearchResultListener listener;
	
	final Vector fileStatsVector;
	
	volatile boolean endFlag = false;
	
	public StandardMysterSearch(String searchString, SearchResultListener listener) {
		this.searchString = searchString;
		this.listener = listener;
		
		this.fileStatsVector = new Vector();
	}
	
	public void start() {
		listener.startSearch();
	}

	public void search(MysterSocket socket, MysterAddress address, MysterType type) throws IOException {
		if (endFlag) return;
		
		Vector searchResults=com.myster.client.stream.StandardSuite.getSearch(socket, type, searchString);

		

		synchronized (this) {
			if (endFlag) return;
			
			if (searchResults.size()!=0) {			
				FileInfoGetter getter=new FileInfoGetter(socket, listener,address, type, searchResults);
				
				fileStatsVector.addElement(getter);
				
				if (endFlag) {
					return;
				}
				
				getter.start();
			}
		}
	}
	
	public void endSearch(MysterType type) {}
	
	public void end() {
		endFlag = true;
	
		flagToEnd();
		
		endOthers();
	}
	
	public void done() {
		synchronized (this) {
			listener.searchOver();
		}
	}
	
	public void flagToEnd() {
		endFlag=true;
		for (int i=0; i<fileStatsVector.size(); i++) {
			((FileInfoGetter)(fileStatsVector.elementAt(i))).flagToEnd();	//tells all the threads to cancel
		}
	}
	
	private void endOthers() {
		for (int i=0; i<fileStatsVector.size(); i++) {
			try {
				((FileInfoGetter)(fileStatsVector.elementAt(i))).end();
			} catch (Exception ex) {}
		}
	}
}
