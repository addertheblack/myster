package com.general.util;

/**
 * Runs a task periodically on the event thread.
 */
public class TimerThread {
    private final Runnable runnable;

    private final int period;

    private final Runnable runnableWrapper;

    private boolean endFlag = false;

    private boolean isActive = false;

    private Timer timer = null;

    private Semaphore sem = new Semaphore(1);

    public TimerThread(Runnable runnable, int periodInMilliseconds) {
        this.runnable = runnable;
        this.period = periodInMilliseconds;

        runnableWrapper = new Runnable() {
            public void run() {
                try {
                    sem.getLock();
                } catch (InterruptedException e) { //UnexpectedExeception here..
                    flagToEnd();
                    return;
                }
                try {
                    if (endFlag)
                        return;

                    TimerThread.this.run();
                } finally {
                    startNextTimer();
                    sem.signal();
                }
            }
        };
    }
    
    /**
     * Over-ride run if you use this constructor.
     * 
     * @param periodInMilliseconds
     */
    public TimerThread(int periodInMilliseconds) {
        this(new Runnable(){public void run(){}}, periodInMilliseconds);
    }

    public void run() {
        runnable.run();
    }

    public synchronized final void start() {
        if (isActive)
            return;

        isActive = true;

        startNextTimer();
    }
    
    private synchronized final void startNextTimer() {
        if (endFlag)
            return;
        timer = new Timer(runnableWrapper, period);
    }

    /**
     * If this is called on the event thread you are guaranteed the same behavior as end().
     */
    public synchronized final void flagToEnd() {
        endFlag = true;
        if (timer != null)
            timer.cancelTimer();
    }

    /**
     * Beware of deadlocks when calling this! You are waiting on a command lock!!.
     * 
     * @throws InterruptedException
     */
    public final void end() throws InterruptedException {
        flagToEnd();
        sem.getLock();
        sem.signal();
    }
}