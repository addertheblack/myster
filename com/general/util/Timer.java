package com.general.util;

/**
 * Is a classic timer object.. Similar in functionality to the javascript
 * setTimer method.
 * 
 * NOTE: This is a loose timer. Events are dispatched whenever they can be after
 * the minimum time has elapsed..
 * 
 * NOTE2: Events are now dispatched on the event thread.
 *  
 */
public class Timer implements Comparable { //almost but not quite immutable.
    private final Runnable runnable;

    private final long time;

    private final boolean runAsThread;

    private volatile boolean isCancelled = false; //is accessed asynchronously

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

    public Timer(Runnable thingToRun, long timeToWait, boolean runAsThread) {
        runnable = thingToRun;
        timeToWait = (timeToWait <= 0 ? 1 : timeToWait); //assert positive.
        time = System.currentTimeMillis() + timeToWait;
        this.runAsThread = runAsThread;

        addEvent(this);
    }

    public long timeLeft() {
        return time - System.currentTimeMillis();
    }

    public boolean isReady() {
        return time - System.currentTimeMillis() - 1 <= 0;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void cancelTimer() {
        isCancelled = true;
    }

    public int compareTo(Object other) {
        Timer otherTimer = (Timer) other;
        if (time < otherTimer.time)
            return -1;
        else if (time == otherTimer.time)
            return 0;
        else
            return 1;
    }

    /**
     * Called by dispatcher thread to start event.
     *  
     */

    private void doEvent() {
        if (runAsThread) {
            (new Thread(runnable)).start();
        } else {
            Util.invokeLater(runnable);
        }
    }

    private static MinHeap timers = new MinHeap();

    private static Thread thread;

    private static void addEvent(Timer timer) {
        synchronized (timers) {
            timers.add(timer);
            timers.notifyAll(); //instead of sem.getLock(time);

            if (thread == null) {
                thread = (new Thread("Myster's timer thread") {
                    public void run() {
                        try {
                            timerLoop();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                });

                thread.start();
            }
        }
    }

    private static void timerLoop() {
        for (;;) {
            Timer timer = null;
            synchronized (timers) {
                boolean isEmpty = false;
                long timeLeft = 0;

                if (timers.isEmpty())
                    isEmpty = true;
                else
                    timeLeft = ((Timer) timers.top()).timeLeft();

                try {
                    if (isEmpty)
                        timers.wait();
                    else if (timeLeft > 0)
                        timers.wait(timeLeft);
                } catch (InterruptedException e) {
                    throw new UnexpectedInterrupt(e.getMessage());
                }

                if (!timers.isEmpty() && ((Timer) timers.top()).isReady())
                    timer = (Timer) timers.extractTop();
            }

            if (timer != null) {//should never happen that timer==null;
                if (!timer.isCancelled())
                    timer.doEvent();
            }
        }
    }
}

