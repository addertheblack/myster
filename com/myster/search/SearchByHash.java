package com.myster.search;

import java.io.IOException;

import com.myster.net.MysterSocket;
import com.myster.net.MysterAddress;
import com.myster.type.MysterType;
import com.myster.hash.FileHash;
//import com.myster.SearchResultListener;

public class SearchByHash implements MysterSearchClientSection {
	final MysterType type;
	final FileHash hash;
	final SearchResultListener listener;
	
	public SearchByHash(String searchString, MysterType type, SearchResultListener listener) {
		this.type = type;
		//this.hash = hash;
		this.hash = null;
		this.listener = listener;
	}
	
	public void start() {
	
	}

	public void search(MysterSocket socket, MysterAddress address,MysterType type) throws IOException  {
		String searchResult=com.myster.client.stream.StandardSuite.getFileFromHash(socket,type,hash);
		
		//if (endFlag) return;

		if (searchResult!=null) {
			listener.addSearchResults(new SearchResult[]{
					new HashSearchResult(new MysterFileStub(address, type, searchResult))});
		}
	}
	
	public void endSearch(MysterType type) {}
	public void searchedAll(MysterType type) {}
	
	public void done() {}
	
	public void end() {

	}
	
	public void flagToEnd() {
		//..
	}
}
