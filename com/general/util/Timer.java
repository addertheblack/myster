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
	
	static TimerVector timers; //should be accessed through getImpl();
	static Timer nextTimer;
	static SafeThread thread;
	static Integer lock=new Integer(1); //so people can't call notify on the class and screw things up.
	
	
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
	
	private static void addEvent(Timer timer) {
		synchronized (lock) {
			getImpl().addTimer(timer);
			thread.interrupt();
		}
	}
	
	private static void timerLoop() {
		synchronized (lock) {
			for (;;) {
				nextTimer=getImpl().getClosestTimer();
				try {
					if (nextTimer==null) lock.wait();
					else {
						long timeToWait=nextTimer.getTime()-System.currentTimeMillis();
						lock.wait(timeToWait<1?1:timeToWait);
					}
				} catch (InterruptedException ex) {
					//System.out.println("Timer was interrupted.");
					continue;
				}
				
				thread.setPriority(Thread.MAX_PRIORITY); //is run on "interrupt time"
				nextTimer.doEvent();
				
				getImpl().removeElement(nextTimer);
			}
		}
	}
	
	
	
	
	private static class TimerVector extends Vector {
	
		public TimerVector(){
			
		}
	
		public synchronized void addTimer(Timer timer) {
			/* //this code would enable some O(1) timer code...
			if (freeBlocks.getSize()>0) {
				for (Object item=freeBlocks.removeFromHead(); i<freeBlocks.getSize(); item=freeBlocks.removeFromHead()) {
					int index=((Integer)item).intValue();
					
					if (index<size()) {
						setElementAt(timer, index);
					}
				}
			}
			*/
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