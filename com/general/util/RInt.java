/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.general.util;

public class RInt {
    private int val = 0;

    private int maxval = 10;

    private int minval = 0;

    public RInt(int maxval) {
        this.maxval = maxval;
    }

    public RInt(int maxval, int minval) {
        this.maxval = maxval;
        this.val = minval;
        this.minval = minval;
    }

    public int getVal() {
        return val;
    }

    public int setValue(int i) {
        if (i >= minval && i <= maxval) {
            val = i;
        }
        return val;
    }

    public int inc() {
        if (val + 1 > maxval)
            val = minval - 1;
        val++;
        return val;
    }

    public int add(int n) {
        for (int i = 0; i < n; i++) {
            inc();
        }
        return val;
    }
}