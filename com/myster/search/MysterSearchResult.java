package com.myster.search;

import com.myster.net.MysterAddress;
import com.myster.mml.RobustMML;
import com.myster.client.stream.DownloaderThread;
import java.util.Vector;

import com.myster.client.stream.MultiSourceDownload;
import com.myster.hash.SimpleFileHash;

public class MysterSearchResult implements SearchResult {
	RobustMML mml;
	MysterFileStub stub;
	
	public MysterSearchResult(MysterFileStub stub) {
		this.stub=stub;
	}
	
	public void setMML(RobustMML m) {
		mml=m;
		System.out.println(mml.toString());
	}
	
	//is called when the user decides to download the item
	public void download() {
		String hashAsString = (mml != null ? mml.get("/hash/md5") : null);
		
		long fileLength = -1;
		if (mml!=null) {
			try {
				fileLength = Long.parseLong(mml.get("/size"));
			} catch (NumberFormatException ex) {
				System.out.println("Server sent a length that is not a number.");
			}
		}
		
		System.out.println("-->"+mml.toString());
		
		try {
			if ((hashAsString != null) & (fileLength!=-1)) {
				MultiSourceDownload download = new MultiSourceDownload(stub, SimpleFileHash.buildFromHexString("md5", hashAsString), fileLength);
				download.start();
				return; //!!!!!!!!!!!!!!!!!! tricky
			}
		} catch (NumberFormatException ex) {
			System.out.println("Could not download multi source because hash was not properly formated");
		} catch (java.io.IOException ex) {
			ex.printStackTrace();
			return;
		}
		
		Thread t=new DownloaderThread((MysterFileStub)stub);
		t.start();
	}
	
	//returns the network the search result is on.
	public String getNetwork() {
		return "Myster Network";
	}
	
	//gets a value for a meta data thingy
	public String getMetaData(String key) {
		return (mml==null?null:mml.get(key));
	}

	//gets the list of known meta data types for this item.
	public String[] getKeyList() {
		if (mml==null) return new String[]{};
		
		Vector items=mml.list("/");
		
		Vector v_temp=new Vector(items.size());
		
		for (int i=0; i<items.size(); i++) {
			String s_temp=(String)(items.elementAt(i));
			if (mml.isAFile("/"+s_temp)) {
				v_temp.addElement("/"+s_temp);
			}
		}
		
		String[] sa_temp=new String[v_temp.size()];
		
		for (int i=0; i<v_temp.size(); i++) {
			sa_temp[i]=(String)(v_temp.elementAt(i));
		}
		
		return sa_temp;
	}
	
	//gets the Name of the search result (usualy a file name!)
	public String getName() {
		return stub.getName();
	}
	
	//gets the host address
	public MysterAddress getHostAddress() {
		return stub.getMysterAddress();
	}
}