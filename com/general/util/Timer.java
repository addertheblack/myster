package com.general.util;
/**
*	Warning - this Timer will not work efficiently for more than 100 Timers!
*/


import java.util.Vector;


/**
*	Is a classic timer object.. Similar in functionality to the javascript setTimer method.
*	NOTE: This is a loose timer. Events are dispatched wheever they can be after the minum time
*	has elapsed.
*	
*/

public class Timer {
	Runnable runnable;
	long time;
	boolean runAsThread;
	
	
	public Timer(Runnable thingToRun, long timeToWait) {
		this(thingToRun, timeToWait, false);
	}
	
	/**
	*	Run as thread means that the timer should launch the runnable item in its own thread.
	*	Items are not run as threa dby default. Items not run as thread should return ASAP.
	*
	* 	Time to wait is relative to the current time ad is in millis.
	*/
	public Timer(Runnable thingToRun, long timeToWait, boolean runAsThread) {
		runnable=thingToRun;
		timeToWait=(timeToWait<=0?1:timeToWait); //assert positive.
		time=System.currentTimeMillis()+timeToWait;
		this.runAsThread=runAsThread;
		
		addEvent(this);
	}
	
	public long getTime() { return time; }
	
	
	
	
	
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
	
	
	
	
	
	
	
	/**
	* Static sub-system.
	*
	*/
	
	//Waring.. lock of the class should not be accessed from outside.
	
	static TimerVector timers; //should be accessed through getImpl();
	static Timer nextTimer;
	static SafeThread thread;
	static boolean wasInterrupted=false;
	
	//for dynamic loading.
	private synchronized static TimerVector getImpl() {
		if (timers==null) {
			
			timers=new TimerVector();
			
			thread=new SafeThread(){
				public void run() {
					for(;;) {
						try {
							timerLoop();
						} catch (Exception ex) { //auto-rebooting.
							ex.printStackTrace();
						}
					}
				}
			};
			thread.start();
		}
		
		return timers;
	}
	
	private synchronized static void addEvent(Timer timer) {///DANGER DEADLOCKS!
		getImpl().addTimer(timer);
		wasInterrupted=true;
		Timer.class.notify();
	}

	private synchronized static void timerLoop() {
		for (;;) {
			wasInterrupted=false;
			nextTimer=getImpl().getClosestTimer(); //This code chooses the next event to run 
			try {
				
				//this code waits until it's time.
				if (nextTimer==null) Timer.class.wait();
				else {
					long timeToWait=nextTimer.getTime()-System.currentTimeMillis();
					Timer.class.wait(timeToWait<=0?1:timeToWait);	//might be less than 0 or 0. BAD!
				}
				
				//This code loops if it was interrupted.
				if (wasInterrupted) {
					continue;
				}
			} catch (InterruptedException ex) {
				ex.printStackTrace(); //error..! should not get here..
				continue;
			}
			
			if (System.currentTimeMillis()-nextTimer.getTime()>=0) {
				thread.setPriority(Thread.MAX_PRIORITY); //is run on "interrupt time"
				nextTimer.doEvent();
				
				getImpl().removeElement(nextTimer);
			}
		}
	}
	
	
	
	
	private static class TimerVector extends Vector {
		public synchronized void addTimer(Timer timer) {
			addElement(timer);
		}
		
		public synchronized Timer getTimer(int i) {
			return (Timer)(elementAt(i));
		}
		
		public synchronized Timer getClosestTimer() {
			if (size()<=0) return null;//no items
			
			Timer smallestTimer=getTimer(0);
			for (int i=1; i<size(); i++) {
				if (getTimer(i).getTime()<smallestTimer.getTime()) {
					smallestTimer=getTimer(i);
				}
			}
			
			return smallestTimer;
		}
	}
}