package com.general.util;

import com.sun.java.util.collections.AbstractCollection;
import com.sun.java.util.collections.Iterator;
import com.sun.java.util.collections.NoSuchElementException;

abstract public class AbstractHeap extends AbstractCollection implements Heap {
    protected int size_;

    protected Object[] data;

    protected Comparator comparator;

    public AbstractHeap() {
        this(4, null);
    }

    public AbstractHeap(int initialMemoryAllocation) {
        this(initialMemoryAllocation, null);
    }

    public AbstractHeap(Comparator comparator) {
        this(4, comparator);
    }

    public AbstractHeap(int initialMemoryAllocation, Comparator comparator) {
        data = new Object[initialMemoryAllocation];
        this.comparator = comparator;
    }

    public void clear() {
        data = new Object[4];
        size_ = 0;
    }

    public boolean add(Object item) {
        if (size_ == data.length) {
            Object[] fresh = new Object[(size_ + 2) * 2];
            System.arraycopy(data, 0, fresh, 0, size_);
            data = fresh;
        }

        int i;
        for (i = size_++; (i > 0) && !isInOrder(data[parent(i)], item); i = parent(i))
            data[i] = data[parent(i)];

        data[i] = item;

        return true;
    }

    private int find(Object item, int i) {
        if (data[i].equals(item))
            return i;

        int l = ((i << 1) + 1);
        int r = ((i << 1) + 2);
        int found = -1;
        if (l < size_ && isInOrder(data[l], item))
            found = find(item, l);
        if (found == -1 && r < size_ && isInOrder(data[r], item))
            found = find(item, r);
        return found;
    }

    private int findKey(Object item) {
        for (int i = 0; i < size_; i++)
            if (data[i].equals(item))
                return i;
        return -1;
    }

    public boolean remove(Object item) {
        if (size_ == 0)
            return false;

        int i;
        try {
            i = find(item, 0);
        } catch (ClassCastException e) {
            i = findKey(item);
        }

        if (i == -1)
            return false;
        data[i] = data[--size_];
        heapify(i);
        return true;
    }

    public boolean contains(Object item) {
        if (size_ == 0)
            return false;
        try {
            return (find(item, 0) != -1);
        } catch (ClassCastException e) {
            return (findKey(item) != -1);
        }
    }

    public class HeapIterator implements Iterator {
        private int index = 0;

        private boolean removeLegal = false;

        public Object next() {
            if (index == size_)
                throw new NoSuchElementException();
            removeLegal = true;
            return data[index++];
        }

        public boolean hasNext() {
            return index < size_;
        }

        public void remove() {
            if (!removeLegal)
                throw new IllegalStateException();
            removeLegal = false;
            data[index] = data[--size_];
            heapify(index);
        }
    }

    public Iterator iterator() { // the order returned is mostly undefined.
        return new HeapIterator();
    }

    public int size() {
        return size_;
    }

    public Object extractTop() {
        if (size_ == 0)
            throw new NoSuchElementException();

        Object max = data[0];
        data[0] = data[size_ - 1];
        data[--size_] = null;
        heapify(0);

        if (size_ < data.length / 3) {
            Object[] fresh = new Object[data.length / 2];
            System.arraycopy(data, 0, fresh, 0, data.length / 2);
            data = fresh;
        }

        return max;
    }

    public Object top() {
        if (size_ == 0)
            throw new NoSuchElementException();
        return data[0];
    }

    private void swap(int a, int b) {
        Object tmp = data[a];
        data[a] = data[b];
        data[b] = tmp;
    }

    protected void heapify(int i) {
        int higher;

        int l = ((i << 1) + 1);
        int r = ((i << 1) + 2);

        if (l < size_ && isInOrder(data[l], data[i]))
            higher = l;
        else
            higher = i;

        if (r < size_ && isInOrder(data[r], data[higher]))
            higher = r;

        if (higher != i) {
            swap(i, higher);
            heapify(higher);
        }
    }

    protected int compare(Object a, Object b) {
        if (comparator == null)
            return ((Comparable) a).compareTo(b);
        else
            return comparator.compare(a, b);
    }

    static protected int parent(int i) {
        return ((i - 1) >> 1);
    }

    abstract protected boolean isInOrder(Object higher, Object lower);

    protected boolean isInOrderWithParent(int i) {
        int p = parent(i);
        return isInOrder(data[p], data[i]);
    }

};


