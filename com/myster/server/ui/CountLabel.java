/*
 * Main.java
 * 
 * Title: Server Stats Window Test App Author: Andrew Trumper Description: An app to test the server
 * stats window
 */

package com.myster.server.ui;

import java.awt.Label;

public class CountLabel extends Label {
    volatile int streamValue = 0;

    volatile int datagramValue = 0;

    public CountLabel(String s) {
        super(s);
    }

    public int getValue() {
        return streamValue;
    }

    public void setValue(int i, boolean isUdp) {
        if (isUdp)
            streamValue = i;
        else
            streamValue = i;
        setUpdateLabel();
    }
    
    public void setValue(int i) {
        setValue(i, false);
    }


    public void increment(boolean isUdp) {
        if (isUdp)
            ++streamValue;
        else
            ++streamValue;
        setUpdateLabel();
    }

    private void setUpdateLabel() {
        setText(streamValue + (datagramValue != 0 ? "/" + datagramValue : ""));
    }

    public void decrement(boolean isUdp) {
        if (isUdp)
            --streamValue;
        else
            --streamValue;
        setUpdateLabel();
    }

}