/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.search;

import com.myster.net.MysterAddress;
import com.sun.java.util.collections.NoSuchElementException;
import com.sun.java.util.collections.Queue;

public class IPQueue implements Queue {
    IPQueueSegment head = null;

    IPQueueSegment tail = null;

    IPQueueSegment start = null;

    int traversed = 0;

    public IPQueue() {
        start = head = tail = new IPQueueSegment(null);
    }

    public synchronized void addIP(MysterAddress m) {
        if (checkIP(m))
            return;
        tail.next = new IPQueueSegment(m);
        tail = tail.next;
    }

    public synchronized MysterAddress getNextIP() {
        if (head.next != null) {
            //note usually we'd do head.next.value since this Queue is
            // implement with
            //a "head" based design as in C++ coding. But here we access it as
            // we
            //do to save time and cut down on the number of lines.

            head = head.next; //move up one..
            traversed++; //add one.
            return head.value;
        }
        return null; //If the queue has no more items the reutnr null.
    }

    public synchronized int getIndexNumber() {
        return traversed;
    }

    //returns !!!true!!! if item is already there...
    public boolean checkIP(MysterAddress ip) {
        IPQueueSegment index = start; //Start at the start.

        while (index.next != null) {
            if (index.next.value.equals(ip))
                return true;
            index = index.next;
        }

        return false;
    }

    private static class IPQueueSegment {
        public IPQueueSegment next = null;

        public MysterAddress value;

        public IPQueueSegment(MysterAddress s) {
            value = s;
        }
    }
    /* (non-Javadoc)
     * @see com.sun.java.util.collections.Queue#element()
     */
    public Object element() throws NoSuchElementException {
        Object o = peek();
        
        if (o == null) throw new NoSuchElementException();
        
        return o;
    }
    /* (non-Javadoc)
     * @see com.sun.java.util.collections.Queue#offer(java.lang.Object)
     */
    public boolean offer(Object o) {
        addIP((MysterAddress)o);
        return true;
    }
    /* (non-Javadoc)
     * @see com.sun.java.util.collections.Queue#peek()
     */
    public Object peek() {
        if (head.next != null) {
            return head.next.value;
        }
        
        return null;
    }
    /* (non-Javadoc)
     * @see com.sun.java.util.collections.Queue#poll()
     */
    public Object poll() {
        return getNextIP();
    }
    /* (non-Javadoc)
     * @see com.sun.java.util.collections.Queue#remove()
     */
    public Object remove() throws NoSuchElementException {
        Object o = getNextIP();
        
        if (o == null) throw new NoSuchElementException();
        
        return o;
    }
}

