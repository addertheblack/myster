package com.general.util;

public class Semaphore {
	int counter=0;
	
	public Semaphore(int m) {
		counter=m;
	}
	
	//deprecated
	public synchronized void waitx() {
		try {getLock();} catch (InterruptedException ex) {}
	}

	//deprecated
	public synchronized void signalx() {
 		signal();
	}
	
	
	public synchronized void signal() {
		counter++ ; 
		if (counter <= 0) notify() ;
	}
	
	//semaphore wait. "wait" is already used by the API.
	public synchronized void getLock() throws InterruptedException {
		counter-- ;
  	 	if (counter < 0) wait(0);
	}
	
	//semaphore wait. "wait" is already used by the API. (is broken)
	//public synchronized void getLock(int millis) throws InterruptedException {
   // 	counter-- ;
   // 	if (counter < 0) wait(millis);
	//}
}
