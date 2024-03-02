
package com.general.thread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SimpleTaskTracker implements Cancellable, TaskTracker {
    private final List<Cancellable> cancellables = new ArrayList<>();
    
    private boolean cancelled;

    @Override
    public synchronized void registerDependentTask(Cancellable... c) {
        List<Cancellable> tasksToAdd = Arrays.asList(c);
        
        if (cancelled) {
            tasksToAdd.forEach(Cancellable::cancel);
        } else {
            cancellables.addAll(tasksToAdd);
        }
    }

    @Override
    public synchronized void cancel() {
        cancelled = true;
        
        cancellables.forEach(Cancellable::cancel);
    }
}
