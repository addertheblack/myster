/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.tracker;

import com.myster.pref.Preferences;
import java.io.IOException;
import java.util.*;
import com.myster.mml.MML;
import com.myster.mml.MMLException;
import java.net.UnknownHostException;
import com.myster.net.MysterAddress;
import java.io.IOException;

/**
*	This class exists to make sure that if a server is listed under many catagories (ie
*	it's a good MPG3 sever as well as being just excellent in PORN) that no additional 
*	Memory is wasted listing the server TWICE.. It also cuts down on the number
*	of pings a very good server receives.. Objects that get MysterIPs from this pool
*	must call the MysterIP method delete(); so that they can be collected by the 
*	MysterIPPool's funky garbage collector.
*
*/

 class MysterIPPool {
	Hashtable hashtable;							//I have a grand imagination!
	static final String pref_key="Tracker.MysterIPPool";	//This is where MysterIPPools stores all it's IPs..
	static MysterIPPool instance;			//Part of the singleton desing pattern.
	Preferences prefs;						//So I don't have to call preferences.getInstance() all the time.
	
		
	private static final int GC_LOWER_LIMIT=350;	//limit of number of IPs before IP start getting deleted.
	private static final int GC_UPPER_LIMIT=100;		//limit of number of IPs before "up" IPs start getting deleted.
	
	private static final int VECTOR_INITIAL_SIZE=400;
	private static final int VECTOR_GROWTH_FACTOR=100;
	
	private MysterIPPool() {
		System.out.println("Loading IPPool.....");
		hashtable=new Hashtable();		//You put cereal on the Hashtable. In a bowl of course...
		Preferences prefs=Preferences.getInstance();
		
		//try {
		//	System.out.println("Sleeping 1");
		//	Thread.currentThread().sleep(500);
		//} catch (InterruptedException ex) {}
		
		MML mml=prefs.getAsMML(pref_key);
				
		//try {
		//	System.out.println("Sleeping 2");
		//	Thread.currentThread().sleep(500);
		//} catch (InterruptedException ex) {}
														
		if (mml!=null) {
			Vector dirList=mml.list("/"); //list root dir
			for (int i=0; i<dirList.size(); i++) {
				try {
					MysterIP mysterip=new MysterIP(new MML(mml.get("/"+(String)(dirList.elementAt(i)))));
					hashtable.put(mysterip.getAddress(), mysterip);	//make a new MysterIP from each file.
				} catch (MMLException ex) {
					ex.printStackTrace();
				}
			}
		}
		
		//try {
		//	System.out.println("Sleeping 3");
		//	Thread.currentThread().sleep(500);
		//} catch (InterruptedException ex) {}
		
		System.out.println("Loaded IPPool");
		this.prefs=prefs;
	}
	
	/**
	*	Gets an instance of the single MysterIPPool a la singleton design pattern.
	*	(Also implements dynamic loading)
	*/
	protected static MysterIPPool getInstance() {
		if (instance==null) synchronizationIssue();
		return instance;
	}
	
	private static synchronized void synchronizationIssue() {
		try {
			if (instance==null) {instance=new MysterIPPool();}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	
	
	
	
	
	
	/**
	*	Given a string address, it returns a com.myster object. com.myster objects are like little
	*	statistics objects. You can get these objects and use these objects from anywhere in the
	*	program thanks to the new garbage collector based system.
	*/
	DeadIPCache deadCache=new DeadIPCache();
	public MysterServer getMysterServer(MysterAddress address) throws IOException {
		
		
		//Below is where the blacklisting code will eventually go.
		if (address.getIP().equals("")||address.getIP().equals("127.0.0.1")||address.getIP().equals("0.0.0.0")) {
			throw new IOException("Black listed internet address");
		}

		MysterServer temp=getMysterIPLevelOne(address);
		
		if (temp==null) {
			boolean b_temp=(deadCache.isDeadAddress(address));
			//System.out.println("IP is dead! "+b_temp);
			if (b_temp) throw new IOException("IP is dead");
			try {
				return getMysterIPLevelTwo(new MysterIP(address.toString()));//get the "other" name for that ip.
			} catch (Exception ex) {
				deadCache.addDeadAddress(address);
				throw new IOException("Bad thing happened in MysterIP Pool add");
			}
		} else {
			return temp;
		}
	}
	
	public MysterServer getMysterServer(String name) throws IOException{
		return getMysterServer(new MysterAddress(name));
	}
	
	/** In oder to avoid having thread problems the two functions below are used. They
		are required because the checking the index and getting the object at that index
		should be atomic, hence the synchronized! and the two functions (for two levels of checking */
		
	
	
	public MysterServer getMysterIPLevelOne(MysterAddress address) {
		MysterIP mysterip=getMysterIP(address);
		
		if (mysterip==null) return null;
		
		return mysterip.getInterface();
	}
	
	public boolean existsInPool(MysterAddress s) {
		return (getMysterIP(s)!=null);
	}
	
	public boolean existsInPool(String s) { 
		try {
			return existsInPool(new MysterAddress(s));
		} catch (UnknownHostException ex) {
			return false;
		}
	}
	
	private synchronized MysterServer getMysterIPLevelTwo(MysterIP m) throws IOException{
			MysterAddress address=m.getAddress();	//possible future bugs here...
			if (existsInPool(address)) return getMysterIPLevelOne(address);;

			return addANewMysterObjectToThePool(m).getInterface();
	}
	
	/*
	private MysterIP getObjectAtIndex(int index) {
		MysterIP temp=(MysterIP)vector.elementAt(index);
		return temp;
	}
	*/
	
	/**
	*	this function adds a new IP to the MysterIPPool data structure.. It's 
	*	syncronized so it's thread safe.
	*
	*	The function double checks to make sure that there really hasen't been another
	*	myster IP cached during the time it took to check and returns the appropriate object.
	*/
	
	private synchronized MysterIP addANewMysterObjectToThePool(MysterIP ip) {
		if (!existsInPool(ip.getAddress())) {				
			deleteUseless();			//Cleans up the pool, deletes useless MysterIP objects!
			hashtable.put(ip.getAddress(),ip);		//if deleteUseless went first, the garbag collector would get the ip we just added! DOH!
			save();
			return ip;
		} else {
			return getMysterIP(ip.getAddress());
		}
	}
	
	/**
	*	This method can be invoked whenever the program feels it has too many MysterIP objects.
	*	This method will only delete objects not being used by the rest of the program.
	*/
	
	private synchronized void deleteUseless() {
		if (hashtable.size()<=GC_UPPER_LIMIT) return;
		
		
		Enumeration enum=hashtable.keys();	//ugh.. This syntax SUCKS!
		Vector keysToDelete=new Vector(100,100);
		
		//Collect worthless....
		while (enum.hasMoreElements()) {
			MysterAddress workingKey=(MysterAddress)(enum.nextElement());
			
			MysterIP mysterip=(MysterIP)(hashtable.get(workingKey));
			
			if (mysterip.getMysterCount()<=0){
				if (!mysterip.getStatus()) {
					keysToDelete.addElement(workingKey);
				}
			}
		}
		
		//remove worthless...
		for (int i=0; i<keysToDelete.size(); i++) {
			hashtable.remove(keysToDelete.elementAt(i)); //weeee...
		}
		
		
		//brag about it...
		if (keysToDelete.size()>=100) {
			System.out.println(keysToDelete.size()+" useless MysterIP objects found. Cleaning up...");
			System.gc();
			System.out.println("Deleted "+keysToDelete.size()+" useless MysterIP objects from the Myster pool.");
		}
		System.out.println("IPPool cleaned up. There are now "+hashtable.size()+" objects in the pool");
		
		
		//signal that the changes should be saved asap...
		save();
	}
	
	//Private Should be pretty obvious.
	private synchronized MysterIP getMysterIP(MysterAddress address) {
		return (MysterIP)(hashtable.get(address));
	}
	
	/**
	*	Saves the state of the MysterIPPool.. Thanks to the new preferences manager, this routine can be called
	*	as often as I like.
	*/
	private synchronized void save() {
		MML mml=new MML(); //make a new file system.

		Enumeration enum=hashtable.elements();	//ugh.. This syntax SUCKS!
		
		//Collect worthless....
		int i=0;
		while (enum.hasMoreElements()) {
			MysterIP mysterip=(MysterIP)(enum.nextElement());
			
			if (mysterip.getMysterCount()>0) {
				mml.put("/"+i,mysterip.toMML().toString());	//write the MysterIP's MML representation as a string.														//directories are numbered 1, 2, 3 etc...
			}
			i++; //needed..
		}
		
		//System.out.println("Saving: "+mml.toString());
		Preferences.getInstance().put(pref_key, mml);
	}
}