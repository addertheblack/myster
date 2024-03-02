
package com.general.thread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CancelContainer implements Cancellable {
    private final List<Cancellable> cancellables = new ArrayList<>();
    
    public synchronized void add(Cancellable ...c ) {
        cancellables.addAll(Arrays.asList(c));
    }
    
    @Override
    public void cancel() {
        cancellables.forEach(Cancellable::cancel);
    }
}
