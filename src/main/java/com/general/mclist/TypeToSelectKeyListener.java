package com.general.mclist;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

/**
 * Provides Mac Finder-style type-to-select functionality for JMCList.
 * 
 * Users can type characters to quickly navigate to matching entries:
 * - Typing accumulates characters in a search buffer
 * - Selection jumps to first matching item (case-insensitive prefix match)
 * - After 1 second of inactivity, the search buffer resets
 * - Tab key advances to the next matching item alphabetically
 * 
 * This class maintains its own alphabetically sorted index of the search column
 * to enable efficient Tab navigation. The index is automatically rebuilt when
 * the table model changes (items added, removed, or resorted).
 */
public class TypeToSelectKeyListener {

    public static void enableSearch(JMCList<?> list, int column) {
        TypeToSelectKeyListener listener = new TypeToSelectKeyListener(list, column);
        
        // Listen for table model changes
        list.getModel().addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                // Any change to the table requires rebuilding our sorted index
                listener.indexValid = false;
            }
        });
        
        list.addKeyListener(new KeyListener() {
            @Override
            public void keyReleased(KeyEvent e) {
                // nothing
            }
            
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    e.consume(); // Prevent default tab behavior
                    listener.selectNextMatch();
                    // Set timestamp to distant past so next letter starts a new search
                    listener.lastKeystrokeTime = 0;
                }
            }
            
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                
                // Ignore non-printable characters and Tab (handled in keyPressed)
                if (Character.isISOControl(c)) {
                    return;
                }
                
                // Check if enough time has passed to reset the search buffer
                long currentTime = System.currentTimeMillis();
                if (currentTime - listener.lastKeystrokeTime > RESET_DELAY_MS) {
                    listener.searchBuffer.setLength(0);
                }
                
                // Add character to search buffer
                listener.searchBuffer.append(c);
                
                // Update timestamp of last keystroke
                listener.lastKeystrokeTime = currentTime;
                
                // Search from the beginning of the alphabetically sorted list
                listener.selectFirstMatch();
            }
        });
    }
    
    private final JMCList<?> list;
    private final StringBuilder searchBuffer;
    private long lastKeystrokeTime = 0; // Timestamp of last keystroke that contributed to search buffer
    
    // Sorted index for efficient searching
    private List<MCListItemInterface<?>> sortedIndex;
    private boolean indexValid = false;
    private int searchColumnIndex = 0; // Column to search (default: first column)
    
    private static final long RESET_DELAY_MS = 1000;
    
    public TypeToSelectKeyListener(JMCList<?> list) {
        this(list, 0);
    }
    
    public TypeToSelectKeyListener(JMCList<?> list, int searchColumnIndex) {
        this.list = list;
        this.searchColumnIndex = searchColumnIndex;
        this.searchBuffer = new StringBuilder();
        this.sortedIndex = new ArrayList<>();
    }
    
    /**
     * Rebuilds the sorted index if it's invalid
     */
    private void ensureSortedIndexValid() {
        if (indexValid && sortedIndex.size() == list.length()) {
            return; // Index is still valid
        }
        
        // Rebuild sorted index
        sortedIndex = new ArrayList<>(list.length());
        for (int i = 0; i < list.length(); i++) {
            sortedIndex.add(list.getMCListItem(i));
        }

        // Sort alphabetically by the search column
        Collections.sort(sortedIndex, new Comparator<MCListItemInterface<?>>() {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            public int compare(MCListItemInterface<?> a, MCListItemInterface<?> b) {
                Sortable sa = a.getValueOfColumn(searchColumnIndex);
                Sortable sb = b.getValueOfColumn(searchColumnIndex);

                return sa.equals(sb) ? 0 : sa.isLessThan(sb) ? -1 : 1;
            }
        });

        indexValid = true;
    }
    
    /**
     * Selects the first item (alphabetically) that matches the current search buffer.
     */
    private void selectFirstMatch() {
        if (searchBuffer.length() == 0 || list.length() == 0) {
            return;
        }
        
        ensureSortedIndexValid();
        
        String searchText = searchBuffer.toString().toLowerCase();
        
        // Search through sorted index to find first match
        for (MCListItemInterface<?> item : sortedIndex) {
            if (matchesItem(item, searchText)) {
                selectAndScrollToItem(item);
                return;
            }
        }
    }
    
    /**
     * Selects the next item (alphabetically) after the current selection that matches the search buffer.
     * Wraps around to the beginning if necessary.
     */
    private void selectNextMatch() {
        if (searchBuffer.length() == 0 || list.length() == 0) {
            return;
        }
        
        ensureSortedIndexValid();
        
        // Get currently selected item
        MCListItemInterface<?> currentItem = null;
        int currentSelectedIndex = list.getSelectedIndex();
        if (currentSelectedIndex >= 0 && currentSelectedIndex < list.length()) {
            currentItem = list.getMCListItem(currentSelectedIndex);
        }
        
        // Find current item's position in sorted index
        int startPos = 0;
        if (currentItem != null) {
            for (int i = 0; i < sortedIndex.size(); i++) {
                if (sortedIndex.get(i) == currentItem) {
                    startPos = i + 1; // Start searching from next item
                    break;
                }
            }
        }

        startPos = startPos < sortedIndex.size() ? startPos : 0;

        // Search from startPos to end
        MCListItemInterface<?> item = sortedIndex.get(startPos);
        selectAndScrollToItem(item);
    }
    
    /**
     * Checks if the item matches the search text.
     * Performs case-insensitive prefix matching on the search column.
     */
    private boolean matchesItem(MCListItemInterface<?> item, String searchText) {
        try {
            Sortable<?> columnValue = item.getValueOfColumn(searchColumnIndex);
            String itemText = columnValue.toString().toLowerCase();
            return itemText.startsWith(searchText);
        } catch (Exception _) {
            // If we can't get the text for any reason, it's not a match
            return false;
        }
    }
    
    /**
     * Finds the current row index of an item and selects it.
     */
    private void selectAndScrollToItem(MCListItemInterface<?> item) {
        // Find the current row index of this item
        int rowIndex = findRowIndexOfItem(item);
        if (rowIndex < 0) {
            return;
        }

        list.clearSelection();
        list.select(rowIndex);
        list.scrollRectToVisible(list.getCellRect(rowIndex, 0, true));
    }
    
    /**
     * Finds the current row index of an item in the table.
     * Returns -1 if not found.
     */
    private int findRowIndexOfItem(MCListItemInterface<?> item) {
        for (int i = 0; i < list.length(); i++) {
            if (list.getMCListItem(i) == item) {
                return i;
            }
        }
        return -1;
    }
//    
//    /**
//     * Sets which column to use for searching.
//     * Invalidates the sorted index, which will be rebuilt on next search.
//     */
//    public void setSearchColumn(int columnIndex) {
//        this.searchColumnIndex = columnIndex;
//        indexValid = false;
//    }
//    
//    /**
//     * Gets the current search column index.
//     */
//    public int getSearchColumn() {
//        return searchColumnIndex;
//    }
//    
//    /**
//     * Gets the current search buffer content (for testing).
//     */
//    String getSearchBuffer() {
//        return searchBuffer.toString();
//    }
//    
//    /**
//     * Clears the search buffer (for testing).
//     */
//    void clearSearchBuffer() {
//        searchBuffer.setLength(0);
//    }
}
