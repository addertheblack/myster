package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestSortableLength {

    @Test
    void toString_negativeMeansUnknown() {
        assertEquals("-", new SortableLength(-1).toString());
    }

    @Test
    void toString_zero() {
        assertEquals("0:00", new SortableLength(0).toString());
    }

    @Test
    void toString_underOneMinute() {
        assertEquals("0:34", new SortableLength(34).toString());
    }

    @Test
    void toString_exactMinutes() {
        assertEquals("3:00", new SortableLength(180).toString());
    }

    @Test
    void toString_typical() {
        assertEquals("3:24", new SortableLength(204).toString());
    }

    @Test
    void toString_longTrack() {
        assertEquals("63:07", new SortableLength(3787).toString());
    }

    @Test
    void sortOrder_shorterLessThanLonger() {
        assertTrue(new SortableLength(60).isLessThan(new SortableLength(120)));
    }

    @Test
    void sortOrder_unknownLessThanZero() {
        assertTrue(new SortableLength(-1).isLessThan(new SortableLength(0)));
    }
}
