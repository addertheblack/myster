
package com.general.thread;

public interface TaskTracker {
    void registerDependentTask(Cancellable... c);
}
