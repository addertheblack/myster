package com.general.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * NOTE: This class is very similar to {@link MaxHeap} so most of the tests are in there.
 * 
 * @see MaxHeap
 */
class TestMaxHeap {
    @Test
    void testIsInOrder() {
        MaxHeap<Integer>  maxHeap = new MaxHeap<>();
        assertTrue(maxHeap.isInOrder(5, 3), "5 is greater than 3, so isInOrder should return true");
        assertFalse(maxHeap.isInOrder(3, 5), "3 is less than 5, so isInOrder should return false");
        assertTrue(maxHeap.isInOrder(5, 5), "5 is equal to 5, so isInOrder should return true");
    }

    /**
     * Basic smoke test of add/extractTop from min heap including 10 ranmly
     * added strings. Make sure the heap sorts things correctly and pull off
     * elements correctly
     */
    @Test
    public void testAddExtractTop() {
        MaxHeap<String> heap = new MaxHeap<>();
        String[] strings = new String[10];
        for (int i = 0; i < 10; i++) {
            strings[i] = UUID.randomUUID().toString();
            heap.add(strings[i]);
        }
        Arrays.sort(strings, Collections.reverseOrder());


        for (int i = 0; i < 10; i++) {
            assertEquals(strings[i], heap.top());
            assertEquals(strings[i], heap.extractTop());
        }
        assertEquals(heap.size(), 0);
    }
}