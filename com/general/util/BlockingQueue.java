package com.general.util;

import com.general.util.LinkedList;

public class BlockingQueue {
	protected LinkedList list=new LinkedList();
	Semaphore sem=new Semaphore(0);
	private boolean rejectDuplicates=false;
	
	public BlockingQueue() {}
	
	/**
	* 	This routine adds an object to the work queue. It does not block.
	*/
	public void add(Object o) {
		if (rejectDuplicates) {
			if (list.contains(o)) return;
		}
		list.addToTail(o);
		sem.signalx();
	}
	
	/**
	* 	This routine gets an object to the work queue. Routine blocks until input is available.
	*/
	public Object get() throws InterruptedException {
		sem.getLock();
		return list.removeFromHead();
		
	}
	
	public int length() {
		return list.getSize();
	}
	
	/**
	*	gets the number of itemssssss waiting in the queue.
	*/
	public int getSize() {
		return length();
	}
	
	public void setRejectDuplicates(boolean b) {
		rejectDuplicates=b;
	}
}