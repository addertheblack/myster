package com.myster.search;

import java.io.IOException;

import com.myster.hash.FileHash;
import com.myster.type.MysterType;
import com.myster.net.MysterSocket;
import com.myster.net.MysterAddress;
import com.myster.client.stream.StandardSuite;
import com.myster.client.stream.UnknownProtocolException;

import java.util.Hashtable;
import java.util.Vector;

import com.myster.client.stream.MultiSourceUtilities;

public class MultiSourceHashSearch implements MysterSearchClientSection {
	//STATIC SUB SYSTEM
	
	private static final Hashtable typeHashtable = new Hashtable();
	
	private synchronized static Vector getEntriesForType(MysterType type) {
		 return getBatchForType(type).entries;
	}
	
	private synchronized static BatchedType getBatchForType(MysterType type) {
		 BatchedType batch = (BatchedType)typeHashtable.get(type);
		 
		 if (batch == null) {
		 	batch = new BatchedType();
		 
		 	typeHashtable.put(type, batch);
		 }
		 
		 return batch;
	}
	
	public synchronized static void addHash(MysterType type, FileHash hash, HashSearchListener listener) {
		Vector entriesVector = getEntriesForType(type);
		
		entriesVector.addElement(new SearchEntry(hash, listener));
		
		if ((entriesVector.size() == 1)) {
			startCrawler(type);
		}
	}
	
	public synchronized static void removeHash(MysterType type, FileHash hash, HashSearchListener listener) {
		Vector entriesVector = getEntriesForType(type);

		entriesVector.removeElement(new SearchEntry(hash, listener));
		
		if (entriesVector.size() == 0) {
			stopCrawler(type);
		}
	}
	
	private synchronized static SearchEntry[] getSearchEntries(MysterType type) {
		Vector entriesVector = getEntriesForType(type);
	
		SearchEntry[] entries = new SearchEntry[entriesVector.size()];
		
		for (int i = 0; i < entries.length; i++) {
			entries[i] = (SearchEntry)entriesVector.elementAt(i);
		}

		return entries;
	}
	
	// asserts that the crawler is stopping
	private synchronized static void stopCrawler(MysterType type) {
		BatchedType batchedType = getBatchForType(type);
	
		if (batchedType.crawler == null) return;
		
		batchedType.crawler.flagToEnd();
		
		batchedType.crawler = null;
	}
	
	private synchronized static void restartCrawler(MysterType type) {
		stopCrawler(type);
		if (getEntriesForType(type).size()>0) { // are we still relevent?
			MultiSourceUtilities.debug("Retarting crawler!");
			startCrawler(type);
		}
	}
	
	// asserts that the crawler is running
	private synchronized static void startCrawler(MysterType type) {
		BatchedType batchedType = getBatchForType(type);
	
		if (batchedType.crawler != null) return;
	
		IPQueue ipQueue = new IPQueue();
		
		String[] startingIps = com.myster.tracker.IPListManagerSingleton.getIPListManager().getOnRamps();
		
		for (int i = 0; i < startingIps.length; i++) {
			try { ipQueue.addIP(new MysterAddress(startingIps[i])); } catch (IOException ex) {ex.printStackTrace();}
		}
		
		batchedType.crawler = new CrawlerThread(new MultiSourceHashSearch(), //note.. will not restart when crawl is done 
									type,
									ipQueue,
									new com.myster.util.Sayable() {
										public void say(String string) {
											MultiSourceUtilities.debug("Hash Search -> "+string);
										}},
									null);
									
		batchedType.crawler.start();
	}
	
	
	private static class SearchEntry {
		public final FileHash hash;
		public final HashSearchListener listener;
		
		public SearchEntry(FileHash hash, HashSearchListener listener) {
			this.hash = hash;
			this.listener = listener;
		}
		
		public boolean equals(Object o) {
			SearchEntry other;
			
			try {
				other = (SearchEntry)o;
			} catch(ClassCastException ex) {
				return false;
			}
			
			return (other.listener.equals(listener) && other.hash.equals(hash));
		}
	}
	
	private static class BatchedType {
		public final Vector entries = new Vector(10,10);
		public CrawlerThread crawler;
	}
	
	
	
	
	
	
	
	
	//OBJECT SYSTEM
	
	
	public void start() {
		// lalalala...
	}
	
	public void search(MysterSocket socket, MysterAddress address, MysterType type) throws IOException {
		MultiSourceUtilities.debug("Hash Search -> Searching "+address);
		
		SearchEntry[] searchEntries = getSearchEntries(type);
		
		for (int i = 0; i < searchEntries.length; i++) {
			searchEntries[i].listener.fireEvent(new HashSearchEvent(HashSearchEvent.START_SEARCH, null));
		}
		
		try {
		
			//This loops goes through each entry one at a time. it oculd be optimised by sending them
			//in a batch in the same way as file stats are done when downloaded off the server after a search
			for (int i = 0; i < searchEntries.length; i++) {
				SearchEntry searchEntry = searchEntries[i];
			
				MultiSourceUtilities.debug("HashSearch -> Searching has "+searchEntry.hash);
			
				String fileName = StandardSuite.getFileFromHash(socket, type, searchEntry.hash);

				if (!fileName.equals("")) {
					MultiSourceUtilities.debug("HASH SEARCH FOUND FILE -> "+fileName);
					searchEntry.listener.fireEvent(new HashSearchEvent(HashSearchEvent.SEARCH_RESULT,
																		 new MysterFileStub(address, type, fileName)));
				}
			}
		} catch (UnknownProtocolException ex) {
			StandardSuite.disconnectWithoutException(socket);
			
			MultiSourceUtilities.debug("Hash Search -> Server "+address+" doesn't understand search by hash connection section.");
		}
		
		for (int i = 0; i < searchEntries.length; i++) {
			searchEntries[i].listener.fireEvent(new HashSearchEvent(HashSearchEvent.END_SEARCH, null));
		}
	}
	
	public void endSearch(final MysterType type) {
		MultiSourceUtilities.debug("Hash Search -> Crawler has crawled the whole network!");
		com.general.util.Timer timer = new com.general.util.Timer(new Runnable() {
				public void run() {
					restartCrawler(type);
				}
		}, 1);
	}
	
	
	public void flagToEnd() {
		// crawler thread passes this along to make quitting faster..
	}
	
	public void end() {
		//..
	}
}
