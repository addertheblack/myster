package com.sun.java.util.collections;

/**
 */
public interface Queue {
    public boolean offer(Object o);

    /**
     * Retrieves and removes the head of this queue, or null if this queue is
     * empty.
     * 
     * @return the head of this queue, or null if this queue is empty.
     */
    public Object poll();

    /**
     * Retrieves and removes the head of this queue. This method differs from
     * the poll method in that it throws an exception if this queue is empty.
     * 
     * @return the head of this queue.
     * @throws NoSuchElementException -
     *             if this queue is empty.
     */
    public Object remove() throws NoSuchElementException;

    /**
     * Retrieves, but does not remove, the head of this queue, returning null if
     * this queue is empty.
     * 
     * @return the head of this queue, or null if this queue is empty.
     */
    public Object peek();

    /**
     * Retrieves, but does not remove, the head of this queue. This method
     * differs from the peek method only in that it throws an exception if this
     * queue is empty.
     * 
     * @return the head of this queue.
     * @throws NoSuchElementException -
     *             if this queue is empty.
     */
    public Object element() throws NoSuchElementException;

}