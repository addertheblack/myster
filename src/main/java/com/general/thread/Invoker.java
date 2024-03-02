
package com.general.thread;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

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
    
    static final Thread.Builder BUILDER = Thread.ofVirtual().name("Invoker", 0);
    public static Invoker newVThreadInvoker() {
        final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        final AtomicBoolean cancelled = new AtomicBoolean();
        Thread t = BUILDER.start(() -> {
            while(!cancelled.get()) {
                try {
                    Runnable r = queue.take();
                    r.run();
                } catch (Exception ex) {
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
}
