package com.myster.search;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import com.myster.net.MysterAddress;

public class IPQueue {
    private final Set<MysterAddress> set = new HashSet<>();
    private final LinkedList<MysterAddress> list = new LinkedList<>();
    private int itemsProcessed = 0;

    /**
     * Add the MysterAddress to the queue if it isn't already in the queue.
     * 
     * Also does not add the the queue if the address have EVER been in the queue.
     */
    public synchronized void addIP(MysterAddress m) {
        if (set.contains(m)) {
            return;
        }
            
        set.add(m);
        list.add(m);
    }

    /**
     * @return the next item from the queue. Returns null if the queue is empty.
     */
    public synchronized MysterAddress getNextIP() {
        if (list.isEmpty()) {
            return null;
        }
        
        itemsProcessed++;
        return list.removeFirst();
    }

    /**
     * @return The number of items processed.
     */
    public synchronized int getNumberOfItemsProcessed() {
        return itemsProcessed;
    }
}
