package com.general.util;

/**
 * This class implements a BlockingQueue. The BlockingQueue implemented here is
 * fairly similar to the BlockingQueue implemented in Java 1.5. It differs in
 * that this blocking queue is not setup to block on adding an item (@see
 * com.general.util.DoubleBlockingQueue)... and of course the functions have
 * slightly different names.
 *  
 */
public class BlockingQueue<T> {
    private LinkedList<T> list = new LinkedList<>();

    private Semaphore sem = new Semaphore(0);

    private boolean rejectDuplicates = false;

    /**
     * This routine adds an object to the work queue. It does not block.
     */
    public void add(T o) {
        synchronized (list) {
            if (rejectDuplicates) {
                if (list.contains(o))
                    return;
            }
            list.addToTail(o);
        }
        sem.signalx();
    }

    /**
     * This routine gets an object to the work queue. Routine blocks until input
     * is available.
     */
    public T get() throws InterruptedException {
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
     * If this property is set to true all adds become order n operations.
     * 
     * @param b
     *            if true, the list will not add Objects already in the list.
     */
    public void setRejectDuplicates(boolean b) {
        rejectDuplicates = b;
    }

    /**
     * Scans through the queue looking for object o using equals(). Removes the
     * first occurrence.
     * 
     * @param o
     *            Object to removes
     * @return true if an object was removed, false otherwise.
     */
    public boolean remove(T o) {
        return list.remove(o);
    }
}