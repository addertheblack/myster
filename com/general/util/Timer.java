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

	private static void timerLoop() {
		for (;;) {
			synchronized(Timer.class){ //this MUST be done to avoid deadlocks.
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
			}
			
			if (System.currentTimeMillis()-nextTimer.getTime()>=0) {
				thread.setPriority(Thread.MAX_PRIORITY); //is run on "interrupt time"
				nextTimer.doEvent(); //thread must not be in the same lock as the dispatcher when dispatching.
				
				getImpl().removeTimer(nextTimer);
			}
		}
	}
	
	
	
	
	private static class TimerVector{
		Timer[] timers=new Timer[10];
		int lastElement=0;
		LinkedList freeSpaces=new LinkedList();
	
		public synchronized void addTimer(Timer timer) {
			if (freeSpaces.getSize()>0) {
				int temp=((Integer)(freeSpaces.removeFromHead())).intValue();
				timers[temp]=timer;
			} else if (lastElement<timers.length) {
				timers[lastElement]=timer;
				lastElement++;
			} else if (lastElement>=timers.length) {
				Timer[] temp_array=new Timer[timers.length+50];
				for (int i=0; i<timers.length; i++) {
					temp_array[i]=timers[i];
				}
				
				timers=temp_array;
				timers[lastElement]=timer;
				lastElement++;
				System.out.println("Array has been extended...");
			}
		}
		
		public synchronized Timer removeTimer(Timer timer) {
			int temp=getIndex(timer);
			
			if (temp==-1) {
				return null;
			} else {
				freeSpaces.addToTail(new Integer(temp));
				Timer timer_temp=timers[temp];
				timers[temp]=null;
				return timer_temp;
			}
		}
		
		private int getIndex(Timer timer) {
			for (int i=0; i<timers.length; i++) {
				if (timers[i]==timer) return i;
			}
			return -1;
		}
		
		public synchronized Timer getClosestTimer() {
			if (timers.length<=0) return null;//no items
			
			Timer smallestTimer=null;
			for (int i=1; i<timers.length; i++) {
				if (timers[i]!=null) {
					if (smallestTimer==null) smallestTimer=timers[i];
					if (timers[i].getTime()<smallestTimer.getTime()) {
						smallestTimer=timers[i];
					}
				}
			}
			
			return smallestTimer;
		}
	}
}