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

import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;

public class TypeChoice extends JComboBox<String> {
    private static final String LOCAL_NETWORK = "Local Network";
    private static final String SEPRARATOR = "---------------------------------";
    private final TypeDescription[] types;

    public TypeChoice(TypeDescriptionList typeDescriptionList, boolean addLocalNetwork) {
        types = typeDescriptionList.getEnabledTypes();
        setEditable(false);
        addItemsToChoice(typeDescriptionList);

        if (addLocalNetwork) {
            // now add a separator
            addItem(SEPRARATOR);

            // Basically it involves inserting a known placeholder (SEPRARATOR)
            // in your list model and
            // when you detect the placeholder in the ListCellRenderer you
            // return an
            // instance of 'new JSeparator(JSeparator.HORIZONTAL)'
            setRenderer(new TypeChoiceRenderer());

            addItem(LOCAL_NETWORK);
        }
    }
    
    private static class TypeChoiceRenderer extends javax.swing.plaf.basic.BasicComboBoxRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            if (SEPRARATOR.equals(value)) {
                javax.swing.JSeparator separator = new javax.swing.JSeparator(javax.swing.JSeparator.HORIZONTAL);
                separator.setEnabled(false); // make the separator unselectable
                return separator;
            }
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
    }
    
    public boolean isLan() {
        if (LOCAL_NETWORK.equals(getSelectedItem())) {
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