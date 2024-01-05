package com.general.util;

import java.util.Comparator;

public class MaxHeap<E> extends AbstractHeap<E> {
    public MaxHeap() {
    }

    public MaxHeap(int initialMemoryAllocation) {
        super(initialMemoryAllocation);
    }

    public MaxHeap(Comparator<E> comparator) {
        super(comparator);
    }

    public MaxHeap(int initialMemoryAllocation, Comparator<E> comparator) {
        super(initialMemoryAllocation, comparator);
    }

    protected boolean isInOrder(E higher, E lower) {
        return (compare(higher, lower) >= 0);
    }
}

