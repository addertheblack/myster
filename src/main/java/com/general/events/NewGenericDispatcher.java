
package com.general.events;

import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.general.thread.Invoker;

public class NewGenericDispatcher<L> {
    private final LinkedHashSet<L> listeners = new LinkedHashSet<>();
    private final L dispatcher;

    private final Lock lock = new ReentrantLock();
    
    @SuppressWarnings("unchecked")
    public NewGenericDispatcher(Class<L> c, Invoker invoker) {
        dispatcher = (L) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {c}, (Object proxy, Method method, Object[] args) -> {
            try {
                /*
                 * We do this to avoid silly thing with the Java run time
                 * disallowing access to private inner classes
                 */
                method.setAccessible(true);
            } catch (SecurityException | InaccessibleObjectException ex) {
                // ignore
            }
            
            // Synchronised is more convenient.. sigh
            lock.lock();
            List<L> copy;
            try {
                copy = new ArrayList<>(listeners);
            } finally {
                lock.unlock();
            }
            
            invoker.invoke(() -> {
                copy.forEach(l -> dispatchListener(l, method, args));
            });

            return null;
        });
    }
    
    private static <L> void dispatchListener(L l, Method method, Object[] args) {
        try {
            method.invoke(l, args);
        } catch (ReflectiveOperationException ex) {
            ex.printStackTrace();
        }
    }
    
    
    public void addListener(L l) {
        lock.lock();
        try {
            listeners.add(l);
        } finally {
            lock.unlock();
        }
    }
    
    public void removeListener(L l) {
        lock.lock();
        try {
            listeners.remove(l);
        } finally {
            lock.unlock();
        }
    }
    
    public L fire() {
        return dispatcher;
    }
}
