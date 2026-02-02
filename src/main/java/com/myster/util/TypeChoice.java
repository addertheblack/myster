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
import java.util.Arrays;
import java.util.Optional;

import com.general.util.Util;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionEvent;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeListener;

/**
 * A combo box for selecting MysterTypes.
 * Automatically updates when types are enabled or disabled in the TypeDescriptionList.
 * <p>
 * This component registers a TypeListener and rebuilds its dropdown items
 * whenever types are added, removed, or their enabled state changes.
 */
public class TypeChoice extends JComboBox<String> {
    private static final String LOCAL_NETWORK = "Local Network";

    private static final String BOOKMARKS= "Bookmarks";
    
    private TypeDescription[] types;
    private final TypeDescriptionList tdList;
    private final boolean addExtras;

    private final TypeListenerImpl typeListenerImpl;

    public TypeChoice(TypeDescriptionList typeDescriptionList, boolean addExtras) {
        Util.addSeparatorSupport(this);
        
        this.tdList = typeDescriptionList;
        this.addExtras = addExtras;
        this.types = typeDescriptionList.getEnabledTypes();
        setEditable(false);

        buildTypeList();

        typeListenerImpl = new TypeListenerImpl();

        // Register as a listener for type changes
        tdList.addTypeListener(typeListenerImpl);
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
    
    public void selectLan() {
        setSelectedItem(LOCAL_NETWORK);
    }
    
    public void selectBookmarks() {
        setSelectedItem(BOOKMARKS);
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

    /**
     * Sets the selected type in the combo box.
     *
     * @param type the type to select
     */
    public void setType(MysterType type) {
        if (type == null) {
            return;
        }

        for (int i = 0; i < types.length; i++) {
            if (types[i].getType().equals(type)) {
                setSelectedIndex(i);
                return;
            }
        }

        setSelectedIndex(0);
    }

    public String getSelectedDescription() {
        return types[getSelectedIndex()].getDescription();
    }

    /**
     * Builds the type list from scratch.
     * Called during construction.
     */
    private void buildTypeList() {
        for (int i = 0; i < types.length; i++) {
            addItem(types[i].getDescription());
        }

        if (addExtras) {
            // now add a separator
            addItem(Util.SEPARATOR);

            addItem(LOCAL_NETWORK);

            addItem(Util.SEPARATOR);

            addItem(BOOKMARKS);
        }
    }

    /**
     * Rebuilds the type list when types are enabled or disabled.
     * Preserves the current selection if possible, otherwise selects the first type.
     */
    private void rebuildTypeList() {
        // Save current selection
        MysterType currentSelection = null;
        if (!isLan() && !isBookmark()) {
            currentSelection = getType().orElse(null);
        }
        boolean wasLan = isLan();
        boolean wasBookmark = isBookmark();

        var listeners = getItemListeners();

        Arrays.stream(listeners).forEach(this::removeItemListener);

        // Clear all items
        removeAllItems();

        // Reload types from TypeDescriptionList
        types = tdList.getEnabledTypes();

        // Rebuild list
        for (int i = 0; i < types.length; i++) {
            addItem(types[i].getDescription());
        }

        if (addExtras) {
            addItem(Util.SEPARATOR);
            addItem(LOCAL_NETWORK);
            addItem(Util.SEPARATOR);
            addItem(BOOKMARKS);
        }

        Arrays.stream(listeners).forEach(this::addItemListener);

        // Restore selection
        if (wasLan) {
            selectLan();
        } else if (wasBookmark) {
            selectBookmarks();
        } else if (currentSelection != null) {
            // Try to restore previous selection
            setType(currentSelection);
            // If type no longer exists, setType will do nothing and we'll stay at index 0
        }
    }

    /**
     * Called when a type is enabled in the TypeDescriptionList.
     * Rebuilds the dropdown to include the newly enabled type.
     */
    private void typeEnabled(TypeDescriptionEvent e) {
        // Run on EDT since this affects UI
        javax.swing.SwingUtilities.invokeLater(this::rebuildTypeList);
    }

    /**
     * Called when a type is disabled in the TypeDescriptionList.
     * Rebuilds the dropdown to exclude the disabled type.
     */
    private void typeDisabled(TypeDescriptionEvent e) {
        // Run on EDT since this affects UI
        javax.swing.SwingUtilities.invokeLater(this::rebuildTypeList);
    }

    public void dispose() {
        tdList.removeTypeListener(typeListenerImpl);
    }

    private class TypeListenerImpl implements TypeListener {
        @Override
        public void typeEnabled(TypeDescriptionEvent e) {
            TypeChoice.this.typeEnabled(e);
        }

        @Override
        public void typeDisabled(TypeDescriptionEvent e) {
            TypeChoice.this.typeDisabled(e);
        }
    }
}
