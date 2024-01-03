/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.util;

import javax.swing.JComboBox;

import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;

public class TypeChoice extends JComboBox<String> {
    TypeDescription[] types;

    public TypeChoice() {
        addItemsToChoice();
        setEditable(false);
    }

    public MysterType getType() {
        return getType(getSelectedIndex());
    }

    public MysterType getType(int i) {
        return types[i].getType();
    }

    private void addItemsToChoice() {
        types = TypeDescriptionList.getDefault().getEnabledTypes();
        for (int i = 0; i < types.length; i++) {
            addItem(types[i].getDescription() + " (" + types[i].getType() + ")");
        }
    }
}