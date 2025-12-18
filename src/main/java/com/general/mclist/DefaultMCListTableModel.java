package com.general.mclist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class DefaultMCListTableModel<E> extends MCListTableModel<E> {
    private final List<String> columnNames = new ArrayList<>();
    private final List<MCListItemInterface<E>> rowValues = new ArrayList<>();

    private int sortByIndex = -1;

    private boolean lessThan = true;

    @Override
    public String getColumnName(int column) {
        return columnNames.get(column);
    }

    @Override
    public void setColumnName(int columnIndex, String columnName) {
        columnNames.set(columnIndex, columnName);
    }
    
    @Override
    public int getSortByIndex() {
        return sortByIndex;
    }

    @Override
    public void sortByIndex(int column) {
        sortByIndex = column;
        resort();
        fireTableRowsUpdated(0, rowValues.size());
    }

    @Override
    public void reverseSortOrder() {
        lessThan = !lessThan;
        resort();
        fireTableRowsUpdated(0, rowValues.size());
    }

    private void resort() {
        if (sortByIndex == -1 || sortByIndex >= columnNames.size()) {
            sortByIndex = -1;
            return;
        }

        Collections.sort(rowValues,
                new Comparator<MCListItemInterface<E>>() {
                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    public int compare(MCListItemInterface<E> a, MCListItemInterface<E> b) {
                        Sortable sa = a.getValueOfColumn(sortByIndex);
                        Sortable sb = b.getValueOfColumn(sortByIndex);

                        if (sa.equals(sb))
                            return 0;

                        int cmp = (sa.isLessThan(sb) ? -1 : 1);
                        return (lessThan ? cmp : -cmp);
                    }
                });
    }

    @Override
    public void clearAll() {
        rowValues.clear();
        fireTableRowsDeleted(0, rowValues.size());
    }

    @Override
    public void removeRow(int i) {
        rowValues.remove(i);
        fireTableRowsDeleted(i, i);
    }

    @Override
    public void removeRows(int[] indexes) {
        Arrays.sort(indexes);
        for (int i = indexes.length - 1; i >= 0; i--) {
            rowValues.remove(indexes[i]);
        }
        fireTableRowsDeleted(0, rowValues.size());
    }
    

    @Override
    public boolean removeItems(MCListItemInterface<E>[] m) {
        if (m == null || m.length == 0) {
            return false;
        }
        
        boolean modified = rowValues.removeAll(Arrays.asList(m));
        
        if (modified) {
            fireTableDataChanged();
        }
        
        return modified;
    }

    @Override
    public void setColumnIdentifiers(String[] names) {
        columnNames.clear();
        for (int i = 0; i < names.length; i++) {
            columnNames.add(names[i]);
        }
        fireTableStructureChanged();
    }

    @Override
    public void addRow(MCListItemInterface<E> item) {
        rowValues.add(item);
        fireTableRowsInserted(rowValues.size() - 1, rowValues.size() - 1);
        resort();
    }

    @Override
    public void addRows(MCListItemInterface<E>[] items) {
        for (int i = 0; i < items.length; i++) {
            rowValues.add(items[i]);
        }
        fireTableRowsInserted(rowValues.size() - items.length, rowValues.size() - 1);
        resort();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.table.AbstractTableModel#getValueAt(int, int)
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return getRow(rowIndex).getValueOfColumn(columnIndex);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.table.AbstractTableModel#getColumnCount()
     */
    @Override
    public int getColumnCount() {
        return columnNames.size();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.table.AbstractTableModel#getRowCount()
     */
    @Override
    public int getRowCount() {
        return rowValues.size();
    }

    @Override
    MCListItemInterface<E> getRow(int index) {
        return rowValues.get(index);
    }

    @Override
    public int indexOf(MCListItemInterface<E> item) {
        return rowValues.indexOf(item);
    }
}
