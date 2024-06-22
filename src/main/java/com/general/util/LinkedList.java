package com.general.util;

/**
 * Is a generic Linked List implementation. Suitable for O(1) queues.
 */
//Fast queues should use addToTail and removeFromHead
public class LinkedList<T> {
    private final Element<T> head;

    private Element<T> tail;

    private int numOfItems = 0;

    public LinkedList() {
        tail = head = new Element<T>(null);
    }

    //fast O(c)
    public synchronized void addToHead(T o) {
        Element<T> e = new Element<T>(o);
        e.next = head.next;
        head.next = e;
        numOfItems++;

        assertTail(); //assertTail needs to be case in case head = tail
    }

    /** gets an element at the index starting from the head. */
    public synchronized T getElementAt(int index) {
        if (index < 0 || index >= numOfItems)
            return null;

        Element<T> e = head;
        for (int i = 0; (e.next != null && i < index); e = e.next, i++)
            ;

        return e.next.value;
    }

    //fast O(c)
    public synchronized void addToTail(T o) {
        Element<T> e = new Element<T>(o);
        tail.next = e;
        tail = e;
        numOfItems++;
    }

    //fast O(c)
    public synchronized T getTail() {
        return tail.value;
    }

    //slow O(n)
    public synchronized T removeFromTail() {
        T o = tail.value;
        tail = head;
        while (tail.next != null) {
            if (tail.next.next == null) {
                tail.next = null;
                break;
            }
            tail = tail.next;
        }
        numOfItems--;
        return o;
    }

    //fast O(n)
    public synchronized T getHead() {
        if (head.next == null)
            return null;
        return head.next.value;
    }

    //fast O(n)
    public synchronized T removeFromHead() {
        if (head.next == null)
            return null;
        T o = head.next.value;
        head.next = head.next.next;
        assertTail(); //in case item removed was the tail.
        numOfItems--;
        return o;
    }

    //fast O(c)
    public int getSize() {
        return numOfItems;
    }

    //slow O(n)
    //deprecated use getPositionOf
    public boolean contains(T object) {
        return (getPositionOf(object) != -1);
    }

    /**
     * returns the index of the Object starting from the head.
     */
    public synchronized int getPositionOf(T o) {
        Element<T> temp = head;
        int counter = 0;

        while (temp.next != null) {
            if (temp.next.value.equals(o))
                return counter;
            temp = temp.next;
            counter++;
        }
        return -1;
    }

    //slow O(n)
    public synchronized boolean remove(Object o) {
        Element<T> temp = head;
        while (temp.next != null) {
            if (temp.next.value.equals(o)) {
                temp.next = temp.next.next;
                if (temp.next == null)
                    tail = temp;
                numOfItems--;
                assertTail();
                return true;
            }
            temp = temp.next;
        }
        return false;
    }

    //slow O(n)
    private synchronized void findTheEnd() {
        tail = head;
        while (tail.next != null) {
            tail = tail.next;
        }
    }

    //fast
    private synchronized void assertTail() {
        if (numOfItems < 2)
            findTheEnd(); //if (head==tail) won't work!
    }

    private static class Element<T> {
        public T value;

        public Element<T> next;

        public Element(T value) {
            this.value = value;
        }
    }
}