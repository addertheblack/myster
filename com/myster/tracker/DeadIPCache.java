package com.myster.tracker;

import com.general.util.LinkedList;
import com.myster.net.MysterAddress;

class DeadIPCache {
	LinkedList queue=new LinkedList();
	
	public static final long EXPIRE_TIME=60*60*1000;//.. time
	
	public synchronized boolean isDeadAddress(MysterAddress address) {
		removeDead();
		return queue.contains(new DeadItem(address, System.currentTimeMillis()));
	}
	
	public synchronized void addDeadAddress(MysterAddress address) {
		removeDead();
		if (queue.getSize()>150) System.out.println("Dead IP Cache has "+queue.getSize()+" items in it!");
		DeadItem i=new DeadItem(address, System.currentTimeMillis());
		if (!queue.contains(i)) queue.addToTail(i);
	}
	
	private synchronized void removeDead() {
		long currentTime=System.currentTimeMillis();
		while (queue.getHead()!=null&&(currentTime-EXPIRE_TIME)>((DeadItem)(queue.getHead())).timeStamp) {
			queue.removeFromHead();
		}	
	}
	
	private static class DeadItem {
		public final long timeStamp;
		public final MysterAddress address;
		
		public DeadItem(MysterAddress a, long t) {
			timeStamp=t;
			address=a;
		}
		
		public boolean equals(Object o) {
			DeadItem i=(DeadItem)o;
			return (address.equals(i.address));
		}
		
		public String toString() {
			return ""+address+" "+timeStamp;
		}
	}
}