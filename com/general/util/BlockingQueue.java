package com.general.util;

/**
 * This class implements a BlockingQueue. The BlockingQueue implemented here is
 * fairly similar to the BlockingQueue implemented in Java 1.5. It differs in
 * that this blocking queue is not setup to block on adding an item (@see
 * com.general.util.DoubleBlockingQueue)... and of course the functions have
 * slightly dfferent names.
 *  
 */
public class BlockingQueue {
    protected LinkedList list = new LinkedList();

    Semaphore sem = new Semaphore(0);

    private boolean rejectDuplicates = false;

    /**
     * This routine adds an object to the work queue. It does not block.
     */
    public void add(Object o) {
        if (rejectDuplicates) {
            if (list.contains(o))
                return;
        }
        list.addToTail(o);
        sem.signalx();
    }

    /**
     * This routine gets an object to the work queue. Routine blocks until input
     * is available.
     */
    public Object get() throws InterruptedException {
        sem.getLock();
        return list.removeFromHead();

    }

    /**
     * 
     * @return The number of objects in the queue.
     */
    public int length() {
        return list.getSize();
    }

    /**
     * 
     * @return The number of objects in the queue.
     */
    public int getSize() {
        return length();
    }

    /**
     * If true is passed here the list will reject objects that are equal to
     * objects already in this list.
     * 
     * @param b
     *            if true, the list will not add Objects already in the list.
     */
    public void setRejectDuplicates(boolean b) {
        rejectDuplicates = b;
    }
}