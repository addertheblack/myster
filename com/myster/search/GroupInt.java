package com.myster.search;

class GroupInt {
	private int counter=0;

	protected GroupInt() {
	
	}
	
	
	public synchronized int  addOne() {
		counter++;
		return counter;
	}
	
	public synchronized int  subtractOne() {
		counter--;
		return counter;
	}
	
	public synchronized int  getValue() {
		return counter;
	}

}