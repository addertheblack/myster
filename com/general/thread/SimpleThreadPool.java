/*
 * Created on Oct 9, 2004
 * by Andrew Trumper
 */
package com.general.thread;

import com.general.util.BlockingQueue;
import com.general.util.LinkedList;
import com.general.util.Semaphore;

/**
 * @author Andrew Trumper
 */
public class SimpleThreadPool {
    private final int startingThreads;

    private final LinkedList freeThreads = new LinkedList();
    
    private final BlockingQueue queue = new BlockingQueue();

    public SimpleThreadPool(int startingThreads) {
        this.startingThreads = startingThreads;
        
        for (int i = 0; i < startingThreads; i++) {
            freeThreads.addToTail(new PoolableThread());
        }
    }

    public Thread execute(Runnable runnable) {
        
        queue.add(runnable);
        if (true == true) return null;
        PoolableThread thread = (PoolableThread) freeThreads.removeFromHead();

        if (thread == null) {
            Thread simpleThread = new Thread(runnable);
            
            simpleThread.start();
            return simpleThread;
        } else {
            thread.execute(runnable);
            return thread;
        }
    }

    private class PoolableThread extends Thread {
        private Semaphore semaphore = new Semaphore(0);

        private Runnable currentRunnable;

        public void run() {
            while (true) {
                try {
                    //semaphore.getLock();
                    currentRunnable = (Runnable)queue.get();
                } catch (InterruptedException ex) {
                    return;
                }

                try {
                    if (currentRunnable == null)
                        continue;

                    Runnable currentRunnable = this.currentRunnable;
                    this.currentRunnable = null;

                    currentRunnable.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    freeThreads.addToTail(this);
                }
            }
        }

        public void execute(Runnable runnable) {
            currentRunnable = runnable;
            semaphore.signal();
        }
    }
}