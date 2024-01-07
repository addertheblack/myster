package com.general.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("myster")
public class TestMinHeap {
    /** Simple test of {@link MinHeap#remove(Object)} remove function. Make sure it can remove something that is there and doens't remove something that isn't*/
    @Test
    public void testRemove() {
        MinHeap<String> heap = new MinHeap<String>();
        String randomString = UUID.randomUUID().toString();
        heap.add(randomString);
        assertTrue(heap.remove(randomString));
        assertFalse(heap.remove(randomString));
    }

    /**
     * Test {@link MinHeap#top()} on an empty heap. Should throw a
     * {@link NoSuchElementException}
     */
    @Test
    public void testTopEmpty() {
        MinHeap<String> heap = new MinHeap<String>();
        try {
            heap.top();
            throw new AssertionError("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // perfect it works
        }
    }



    public static class TestMinHeapWithInitializedData {
                                    private MinHeap<String> heap;
        private String[] strings;

        @BeforeEach
        public void setup() {
            heap = new MinHeap<String>();
            strings = new String[10];
            for (int i = 0; i < 10; i++) {
                strings[i] = UUID.randomUUID().toString();
                heap.add(strings[i]);
            }
            Arrays.sort(strings);
        }

        /**
         * Test {@link MinHeap#top()} to make sure it returns the topmost items
         * without removing it
         */
        @Test
        public void testTop() {
            assertEquals(strings[0], heap.top());
            assertEquals(strings[0], heap.top());
            assertEquals(heap.size(), 10);
        }

    /**
     * Basic smoke test of add/extractTop from min heap including 10 ranmly
     * added strings. Make sure the heap sorts things correctly and pull off
     * elements correctly
     */
        @Test
        public void testAddRemove() {
            for (int i = 0; i < 10; i++) {
                assertEquals(strings[i], heap.extractTop());
            }
        }

        /**
         * Basic smoke test of {@link MinHeap#contains(Object)} including the
         * negative case where a string is not contained
         */
        @Test
        public void testContains() {
            assertFalse(heap.contains(UUID.randomUUID().toString()));

            for (int i = 0; i < 10; i++) {
                assertTrue(heap.contains(strings[i]));
            }
        }

        /** Test MinHeap#iterator() produces output in the correct order */
        @Test
        public void testIterator() {
            assertEquals(heap.iterator().next(), strings[0]);
        }

        /** Test {@link MinHeap#iterator()} remove() works */
        @Test
        public void testIteratorRemove() {
            for (int i = 0; i < 1; i++) {
                Iterator<String> iterator = heap.iterator();
                assertEquals(iterator.next(), strings[0]);
                iterator.remove();
            }
        }
    }
}