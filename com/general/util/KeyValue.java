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

import java.util.Vector;

public class KeyValue {
    private Vector keys;

    private Vector values;

    public KeyValue() {
        keys = new Vector(10, 10);
        values = new Vector(10, 10);
    }

    public void addValue(Object key, Object value) {
        keys.addElement(key == null ? "" : key);
        values.addElement(value == null ? "" : value);
    }

    public int length() {
        return keys.size();
    }

    public int getLength() {
        return length();
    }

    public int size() {
        return length();
    }

    public Object getValueFromKey(Object o) {
        for (int i = 0; i < keys.size(); i++) {
            if (o.equals(keys.elementAt(i)))
                return values.elementAt(i);
        }
        return null;
    }

    public Object valueAt(int i) {
        return values.elementAt(i);
    }

    public Object keyAt(int i) {
        return keys.elementAt(i);
    }
}