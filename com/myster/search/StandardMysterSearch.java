package com.myster.search;

import java.io.IOException;
import java.util.Vector;

import com.myster.type.MysterType;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.DisconnectException;
import com.myster.mml.RobustMML;

public class StandardMysterSearch implements MysterSearchClientSection {
	final String searchString;
	final SearchResultListener listener;

	volatile boolean endFlag = false;
	
	public StandardMysterSearch(String searchString, SearchResultListener listener) {
		this.searchString = searchString;
		this.listener = listener;
	}
	
	public void start() {
		//listener.startSearch(); (This is already taken care of by the root. All we have to do is add search results.)
	}

	public void search(MysterSocket socket, MysterAddress address, MysterType type) throws IOException {
		if (endFlag) throw new DisconnectException();
		
		Vector searchResults=com.myster.client.stream.StandardSuite.getSearch(socket, type, searchString);

		if (endFlag) throw new DisconnectException();;
		
		if (searchResults.size()!=0) {
			Vector mysterSearchResults = new Vector(searchResults.size());

			for (int i=0; i<searchResults.size(); i++ ){
				mysterSearchResults.addElement(new MysterSearchResult(new MysterFileStub(address, type, (String)(searchResults.elementAt(i)))));
			}
			
			sendSearchResultsToListener(mysterSearchResults,listener);
			
			dealWithFileStats(socket, type, mysterSearchResults, listener);
			
			if (endFlag) throw new DisconnectException();; //FileInfoGetter
		}
	}
	
	public void endSearch(MysterType type) {}
	public void searchedAll(MysterType type) {}
	
	public synchronized void flagToEnd() {
		if (endFlag) return;
		endFlag=true;
	}
	
	private void dealWithFileStats(MysterSocket socket, MysterType type, Vector mysterSearchResults, SearchResultListener listener) throws IOException {
		//This is a speed hack.
		int pointer=0;
		int current=0;
		final int MAX_OUTSTANDING=25;
			while (current<mysterSearchResults.size()) { //usefull.
				if (endFlag) throw new DisconnectException();
			
				if (pointer<mysterSearchResults.size()) {
					SearchResult result=(SearchResult)(mysterSearchResults.elementAt(pointer));
					socket.out.writeInt(77);
					
					socket.out.writeInt(type.getAsInt());
					socket.out.writeUTF(result.getName());
					pointer++;
				}
				
				if (endFlag) throw new DisconnectException();
				
				while (socket.in.available()>0||(pointer-current>MAX_OUTSTANDING)||pointer>=mysterSearchResults.size()) {
					if (socket.in.readByte()!=1) return;
					
					if(endFlag) throw new DisconnectException();
					
					RobustMML mml;
					try {
						mml=new RobustMML(socket.in.readUTF());
					} catch (Exception ex) {
						return;
					}
					
					
					((MysterSearchResult)(mysterSearchResults.elementAt(current))).setMML(mml);
					
					listener.searchStats((SearchResult)(mysterSearchResults.elementAt(current)));
					
					current++;
					
					if (current>=mysterSearchResults.size()) {
						break;
					}
				}
			}
	}
	
	private void sendSearchResultsToListener(Vector mysterSearchResults, SearchResultListener listener) {
		SearchResult[] searchArray=new SearchResult[mysterSearchResults.size()];
		
		for (int i=0; i < searchArray.length; i++) {
			searchArray[i]=(SearchResult)(mysterSearchResults.elementAt(i));
		}
		
		listener.addSearchResults(searchArray);
	}
}
