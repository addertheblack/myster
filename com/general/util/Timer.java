package com.general.util;





/**
	 
*	Is a classic timer object.. Similar in functionality to the javascript setTimer method.

*	NOTE: This is a loose timer. Events are dispatched wheever they can be after the minum time

*	has elapsed.

*	

*/



public class Timer implements Comparable { //almost but not quite immutable.
	final Runnable runnable;
	final long time;
	final boolean runAsThread;
	volatile boolean isCancelled=false; //is accessed asynchronously
	
	public Timer(Runnable thingToRun, long timeToWait) {
		this(thingToRun, timeToWait, false);
	}
	
	
	
	/** 
	*	Run as thread means that the timer should launch the runnable item in its own thread.
	*	Items are not run as threa dby default. Items not run as thread should return ASAP.
	*
	*		Time to wait is relative to the current time ad is in millis.
	*/
	
	public Timer(Runnable thingToRun, long timeToWait, boolean runAsThread) {
		runnable=thingToRun;
		timeToWait=(timeToWait<=0?1:timeToWait); //assert positive.
		time=System.currentTimeMillis()+timeToWait;
		this.runAsThread=runAsThread;
		
		addEvent(this);
	}
	
	
	
	public long timeLeft() { return time - System.currentTimeMillis(); }
	
	public boolean isReady() { return time - System.currentTimeMillis() - 1 <= 0; }
	
	public boolean isCancelled() { return isCancelled; }
	
	public void cancleTimer() { isCancelled=true; }
	
	
	
	public int compareTo(Object other) {
		Timer otherTimer = (Timer)other;
		if (time < otherTimer.time) return -1;
		else if (time == otherTimer.time) return 0;
		else return 1;
	}
	
	
	
	/** 
	*	Called by dispatcher thread to start event.
	*
	*/ 
	
	private void doEvent() {
		if (runAsThread) {
			(new Thread(runnable)).start();
		} else {
			try {
				runnable.run();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	
	
	
	
	
	static private Semaphore semaphore = new Semaphore(0);
	static private MinHeap timers = new MinHeap();
	static private Thread thread;
	
	static private void addEvent(Timer timer) {
		synchronized (timers) {
			timers.add(timer);
			timers.notify(); //instead of sem.getLock(time);
			
			if (thread == null)  { 
				thread = (new Thread("Timer Thread!!") {
					public void run() {
						try {timerLoop();} catch (Exception ex) { ex.printStackTrace(); }
					}
				});
				
				thread.start();
			}
		}
	}


	



	static private void timerLoop() {
		
		for(;;) {
			Timer timer = null;
			synchronized (timers) {
				boolean isEmpty = false;
				long timeLeft = 0;
				
				
				if (timers.isEmpty()) isEmpty = true;
				else timeLeft = ((Timer)timers.top()).timeLeft();

				
				try {
					if (isEmpty) timers.wait();
					else if (timeLeft > 0) timers.wait(timeLeft);
				} catch(InterruptedException e) {
					throw new UnexpectedInterrupt(e.getMessage());
				}
				
				
				if (!timers.isEmpty() && ((Timer)timers.top()).isReady())
						timer = (Timer)timers.extractTop();
			}
			
			
			
			if (timer != null) {//should never happen that timer==null;
				if (timer.isCancelled()==false) timer.doEvent();
			}
		}
	}
}

