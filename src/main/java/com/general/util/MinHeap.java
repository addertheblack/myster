package com.general.util;

import java.util.Comparator;

public class MinHeap<E> extends AbstractHeap<E> {
    public MinHeap() {
    }

    public MinHeap(int initialMemoryAllocation) {
        super(initialMemoryAllocation);
    }

    public MinHeap(Comparator<E> comparator) {
        super(comparator);
    }

    public MinHeap(int initialMemoryAllocation, Comparator<E> comparator) {
        super(initialMemoryAllocation, comparator);
    }

    protected boolean isInOrder(E higher, E lower) {
        return (compare(higher, lower) <= 0);
    }
}