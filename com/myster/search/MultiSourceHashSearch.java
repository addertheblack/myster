package com.myster.search;

import java.io.IOException;

import com.myster.hash.FileHash;
import com.myster.type.MysterType;
import com.myster.net.MysterSocket;
import com.myster.net.MysterAddress;
import com.myster.client.stream.StandardSuite;
import com.myster.client.stream.UnknownProtocolException;

public class MultiSourceHashSearch implements MysterSearchClientSection {
	FileHash hash;
	MysterType type;
	HashSearchListener hashSearchListener;
	
	public MultiSourceHashSearch(MysterType type, FileHash hash, HashSearchListener listener) {
		this.type = type;
		this.hash = hash;
		this.hashSearchListener = listener;
	}
	
	public void start() {
		hashSearchListener.fireEvent(new HashSearchEvent(HashSearchEvent.START_SEARCH, null));
	}
	
	public void search(MysterSocket socket, MysterAddress address) throws IOException {
		System.out.println("Hash Search -> Searching "+address);
		try {
			hashSearchListener.fireEvent(new HashSearchEvent(HashSearchEvent.SEARCH_RESULT, new MysterFileStub(address, type, StandardSuite.getFileFromHash(socket, type, hash))));
		} catch (UnknownProtocolException ex) {
			StandardSuite.disconnectWithoutException(socket);
			
			System.out.println("Hash Search -> Server "+address+" doesn't understand search by hash connection section.");
		}
	}
	
	private void endSearch() {
		hashSearchListener.fireEvent(new HashSearchEvent(HashSearchEvent.END_SEARCH, null));
	}
	
	public void flagToEnd() {
		//...
	}
	
	public void end() {
		//...
	}
}
