package com.general.util;

import java.util.TimerTask;

/**
 * Is a classic timer object.. Similar in functionality to the javascript
 * setTimer method.
 * 
 * NOTE: This is a loose timer. Events are dispatched whenever they can be after
 * the minimum time has elapsed..
 * 
 * NOTE2: Events are now dispatched on the event thread.
 * 
 * has elapsed.
 * 
 * 
 *  IMMUTABLE
 */
public class Timer {
    private static final java.util.Timer timer = new java.util.Timer();

	private final TimerTask task;

    public Timer(Runnable thingToRun, long timeToWait) {
        this(thingToRun, timeToWait, false);
    }

    /**
     * Run as thread means that the timer should launch the runnable item in its
     * own thread. Items are not run as thread by default. Items not run as
     * thread should return ASAP.
     * 
     * Time to wait is relative to the current time ad is in millis.
     */

	public Timer(final Runnable thingToRun, long timeToWait,
			final boolean runAsThread) {
        task = new TimerTask() {
			@Override
			public void run() {
		        if (runAsThread) {
		            (new Thread(thingToRun)).start();
		        } else {
		            Util.invokeLater(thingToRun);
		        }
			}
        };
        
        timer.schedule(task, Math.max(1, timeToWait));
    }

    public void cancelTimer() {
        task.cancel();
    }
}
