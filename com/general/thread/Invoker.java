
package com.general.thread;

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
    };
    
    public void invoke(Runnable r);
    
    public boolean isInvokerThread();
}
