package com.myster.bandwidth;

import java.util.Vector;

import com.general.events.EventListener;
import com.general.events.GenericEvent;
import com.general.events.SyncEventDispatcher;
import com.general.util.Semaphore;
import com.general.util.Timer;

/**
*	The bandwidth manager is a set of static funcitons that allow multiple dowload/upload threads to
*	make sure their total bandwidth utilisation doesn't exeed a preset amount.
*/

public class BandwidthManager {
	private static Bandwidth impl=new BandwithImpl();
	private static boolean flag=false;
	
	public static final void replaceImplementation() throws AlreadyImplementedException {
		if (!flag) {
			flag=true;
		} else {
			throw new AlreadyImplementedException("...");
		}
	}
	
	/*
	public static final synchronized int requestBytesOutgoing(int maxBytes) {
		final int speed=10*1024;
		final int maxWaitTime=100;
		final int spacing=(maxWaitTime*speed)/1000;
		
		if (maxBytes>spacing) {
			maxBytes=spacing;
		}
		
		int sleepTime=((maxBytes*1000)/speed);
		
		//System.out.println("Sleep time = "+sleepTime);
		if (sleepTime>0) try {Thread.currentThread().sleep(sleepTime);} catch (Exception ex) {}
		return maxBytes;
	}
	*/

	public static final int requestBytesIncoming(int maxBytes) {
		return maxBytes;
		//return impl.requestBytesIncoming(maxBytes);
	}
	
	public static final int requestBytesOutgoing(int maxBytes) {
		return maxBytes;//impl.requestBytesOutgoing(maxBytes);
	}
}

class AlreadyImplementedException extends Exception {
	public AlreadyImplementedException(String s) {
		super(s);
	}
}

abstract class BandwidthListener extends EventListener {
	public final void fireEvent(GenericEvent e) {
		switch(e.getID()) {
			case BandwidthEvent.TICK:
				tick((BandwidthEvent)e);
				break;
			default: err();
		}
	}
	
	public abstract void tick(BandwidthEvent e) ;
	
	public abstract int getRequestedAmount() ;
}

class BandwidthHandler extends BandwidthListener {
	Semaphore sem;
	int amount=0;
	int requestedAmount=0;
	
	public BandwidthHandler(Semaphore sem, int requestedAmount) {
		this.sem=sem;
		this.requestedAmount=requestedAmount;
	}

	public void tick(BandwidthEvent e) {
		amount=e.getAmount();
		sem.signal();
	}
	
	public int getAmount() {
		return amount;
	}
	
	public int getRequestedAmount() {
		return requestedAmount;
	}
}

class BandwithImpl implements Bandwidth {
	SyncEventDispatcher dispatcher=new SyncEventDispatcher();
	TimerCode timerCode=new TimerCode();
	Vector[] lVectors=new Vector[2];
	
	private final static int AMOUNT_ALLOTED=10000;
	private final static int UPLOAD=0;
	private final static int DOWNLOAD=1;
	
	public BandwithImpl() {
		lVectors[UPLOAD]=new Vector();
		lVectors[DOWNLOAD]=new Vector();
	
		timerCode.start();
	}

	private int requestBytes(int maxbytes, int i) {
		Semaphore sem=new Semaphore(0);
		BandwidthHandler handler=new BandwidthHandler(sem,maxbytes);
		
		addBandwidthListener(handler, i);
		
		try {
			sem.getLock();
		} catch (InterruptedException ex) {
			return 1; //grrr.. how dare you interrupt me?
		}
		
		return handler.getAmount();
	}
	
	
	public int requestBytesIncoming(int maxBytes) {
		return requestBytes(maxBytes, DOWNLOAD);
	}
	
	public int requestBytesOutgoing(int maxBytes) {
		return requestBytes(maxBytes, UPLOAD);
	}
	
	private synchronized void addBandwidthListener(BandwidthListener listener, int index) {
		lVectors[index].addElement(listener);
	}
	
	private synchronized Vector getAndReplace(int i) {
		if (lVectors[i].size()==0) return null;
		Vector vector=lVectors[i];
		lVectors[i]=new Vector(50);
		return vector;
	}
	
	
	private class TimerCode implements Runnable {
		private static final int BANDWIDTH_RES=2000;//in ms
		private long lastTime=0;
	
		public void start() {
			Timer t=new Timer(this, BANDWIDTH_RES);
			lastTime=System.currentTimeMillis();
		}
		
		public void run() {
			doEvents();
			Timer t=new Timer(this, BANDWIDTH_RES);
		}
		
		private void doEvents() {
			
			long newTime=System.currentTimeMillis();
			int amount=(int)((AMOUNT_ALLOTED*(newTime-lastTime))/1000);
			
			if (amount<1) return;

			Vector oldListenersUp=getAndReplace(UPLOAD);
			Vector oldListenersDown=getAndReplace(DOWNLOAD);

			lastTime=newTime;
			
			Timer t=new Timer(this, BANDWIDTH_RES);
			
			if (oldListenersUp!=null) fireEvents(oldListenersUp, amount);
			if (oldListenersDown!=null) fireEvents(oldListenersDown, amount);
		}
	
		public void fireEvents(Vector oldListeners, int maxAlloted) {	
			int amountAllowed=maxAlloted/oldListeners.size();
			amountAllowed++;
			int totalUsed=0;
			for (int i=0; i<oldListeners.size(); i++) {
				BandwidthListener listener=((BandwidthListener)((oldListeners.elementAt(i))));
				int amountUsed=amountAllowed<=listener.getRequestedAmount()?amountAllowed:listener.getRequestedAmount();
				listener.fireEvent(new BandwidthEvent(BandwidthEvent.TICK,amountUsed)); //note, this is happening in the timer thread!
				totalUsed+=amountUsed;
			}
			//if (totalUsed<maxAllocated) addToCache(maxAllocated-totalUsed);
		}
	}
}

interface Bandwidth {
	public int requestBytesIncoming(int maxBytes);
	public int requestBytesOutgoing(int maxBytes);
}

class BandwidthEvent extends GenericEvent {
	public static final int TICK=3;

	int amount=0;
	
	public BandwidthEvent(int id, int amount) {
		super(id);
		this.amount=amount;
	}
	
	public int getAmount() {
		return amount;
	}
}