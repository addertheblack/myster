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

import java.util.Optional;

import javax.swing.JComboBox;

import com.general.util.Util;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;

public class TypeChoice extends JComboBox<String> {
    private static final String LOCAL_NETWORK = "Local Network";
    
    private static final String BOOKMARKS= "Bookmarks";
    
    private final TypeDescription[] types;

    public TypeChoice(TypeDescriptionList typeDescriptionList, boolean addExtras) {
        Util.addSeparatorSupport(this);
        
        types = typeDescriptionList.getEnabledTypes();
        setEditable(false);
        addItemsToChoice(typeDescriptionList);

        if (addExtras) {
            // now add a separator
            addItem(Util.SEPARATOR);

            addItem(LOCAL_NETWORK);
            
            addItem(Util.SEPARATOR);

            addItem(BOOKMARKS);
        }
    }
    
    public boolean isLan() {
        if (LOCAL_NETWORK.equals(getSelectedItem())) {
            return true;
        }
        
        return false;
    }
    
    public boolean isBookmark() {
        if (BOOKMARKS.equals(getSelectedItem())) {
            return true;
        }
        
        return false;
    }

    public Optional<MysterType> getType() {
        return getType(getSelectedIndex());
    }

    public Optional<MysterType> getType(int i) {
        if (i > types.length || i < 0) {
            return Optional.empty();
        }
        
        return Optional.of(types[i].getType());
    }
    
    public String getSelectedDescription() {
        return types[getSelectedIndex()].getDescription();
    }

    private void addItemsToChoice(TypeDescriptionList typeDescriptionList) {
        for (int i = 0; i < types.length; i++) {
            addItem(types[i].getDescription());
        }
    }
}