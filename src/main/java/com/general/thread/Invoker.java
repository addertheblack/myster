
package com.general.thread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import com.general.util.RuntimeInterruptedException;
import com.general.util.Util;

public interface Invoker {
    public static Invoker EDT = new Invoker() {
        @Override
        public void invoke(Runnable r) {
            SwingUtilities.invokeLater(r);
        }

        @Override
        public boolean isInvokerThread() {
            return SwingUtilities.isEventDispatchThread();
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException();
        }
    };
    
    public static Invoker SYNCHRONOUS_PUSH_ONTO_EDT = new Invoker() {
        @Override
        public void invoke(Runnable r) {
            try {
                Util.invokeAndWaitForAnyThread(r);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeInterruptedException(exception);
            }
        }

        @Override
        public boolean isInvokerThread() {
            return SwingUtilities.isEventDispatchThread();
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException();
        }
    };
    
    public static Invoker EDT_NOW_OR_LATER = new Invoker() {
        @Override
        public void invoke(Runnable r) {
            Util.invokeNowOrLater(r);
        }

        @Override
        public boolean isInvokerThread() {
            return SwingUtilities.isEventDispatchThread();
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException();
        }
    };
    
    public static Invoker SYNCHRONOUS = new Invoker() {
        @Override
        public void invoke(Runnable r) {
            try {
                r.run();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public boolean isInvokerThread() {
            return true;
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException();
        }
    };
    
    static final Thread.Builder BUILDER = Thread.ofVirtual().name("Invoker", 0);
    public static Invoker newVThreadInvoker() {
        final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        final AtomicBoolean cancelled = new AtomicBoolean();
        Thread t = BUILDER.start(() -> {
            while(!cancelled.get()) {
                try {
                    Runnable r = queue.take();
                    r.run();
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            }
        });
        
        return new Invoker() {
            
            @Override
            public void shutdown() {
                cancelled.set(true);
                
                // wake up thread to stop
                // thread.interrupt() is just a baaaad idea here
                // because calling interrupt on a thread will cause runnables running to bug out
                queue.add(()->{});
            }
            
            @Override
            public boolean isInvokerThread() {
                return Thread.currentThread() == t;
            }
            
            @Override
            public void invoke(Runnable r) {
               queue.add(r);
            }
        };
    }
    
    public void invoke(Runnable r);
    
    // invokeAndWait();// <- uses isInvokerThread()
    
    public boolean isInvokerThread();
    
    public void shutdown();
    
    default void waitForThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        
        this.invoke(() -> {
            latch.countDown();  
        });
        
        latch.await();
    }
}
