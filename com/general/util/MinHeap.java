package com.general.util;

public class MinHeap extends AbstractHeap {

    public MinHeap() {
    }

    public MinHeap(int initialMemoryAllocation) {
        super(initialMemoryAllocation);
    }

    public MinHeap(Comparator comparator) {
        super(comparator);
    }

    public MinHeap(int initialMemoryAllocation, Comparator comparator) {
        super(initialMemoryAllocation, comparator);
    }

    protected boolean isInOrder(Object higher, Object lower) {

        return (compare(higher, lower) <= 0);

    }

    /*
     * 
     * 
     * 
     * 
     * 
     * static public void main(String[] args) {
     * 
     * 
     * 
     * 
     * 
     * Heap heap = new MinHeap();
     * 
     * 
     * 
     * 
     * 
     * System.out.println("Test add");
     * 
     * 
     * 
     * for(int i=0; i <100; i++)
     * 
     * 
     * 
     * heap.add(new Integer(i));
     * 
     * 
     * 
     * 
     * 
     * while(!heap.isEmpty())
     * 
     * 
     * 
     * System.out.print(heap.extractTop() + " ");
     * 
     * 
     * 
     * 
     * 
     * System.out.println("Test remove 0, [10, 19], 99");
     * 
     * 
     * 
     * for(int i=0; i <100; i++)
     * 
     * 
     * 
     * heap.add(new Integer(i));
     * 
     * 
     * 
     * 
     * 
     * for(int i=10; i <20; i++)
     * 
     * 
     * 
     * heap.remove(new Integer(i));
     * 
     * 
     * 
     * heap.remove(new Integer(99));
     * 
     * 
     * 
     * heap.remove(new Integer(0));
     * 
     * 
     * 
     * 
     * 
     * 
     * 
     * while(!heap.isEmpty())
     * 
     * 
     * 
     * System.out.print(heap.extractTop() + " ");
     * 
     * 
     *  }
     * 
     * 
     *  
     */

}