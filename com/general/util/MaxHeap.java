package com.general.util;


public class MaxHeap extends AbstractHeap {


    public MaxHeap() { }

    public MaxHeap(int initialMemoryAllocation) { super(initialMemoryAllocation); }

    public MaxHeap(Comparator comparator) { super(comparator); }

    public MaxHeap(int initialMemoryAllocation, Comparator comparator) { super(initialMemoryAllocation, comparator); }


    protected boolean isInOrder(Object higher, Object lower) {

        return (compare(higher, lower) >= 0);

    }


    

}

