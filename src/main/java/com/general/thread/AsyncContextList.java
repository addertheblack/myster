
package com.general.thread;

public interface AsyncContextList<T>extends Cancellable, TaskTracker {
    boolean addResult(T t);
    
    void done();
    
    
    @Override
    void cancel();
    
    boolean isCancelled();
    void registerDependentTask(Cancellable... c);
}
