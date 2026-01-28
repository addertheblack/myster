package com.general.mclist;

import javax.swing.table.AbstractTableModel;

/**
 * Table model contract for MCList.
 *
 * Implementations back a multi-column list of MCListItemInterface rows and are
 * expected to:
 * - Maintain a collection of rows addressable by model row index
 * - Support optional sorting by a single column (or "unsorted")
 * - Fire the appropriate AbstractTableModel events when data or structure changes
 *
 * Unless otherwise noted, the semantics below reflect the default behavior in
 * DefaultMCListTableModel.
 *
 * Type parameters:
 * - E: the underlying object type carried by each MCListItemInterface row
 */
public abstract class MCListTableModel<E> extends AbstractTableModel {
    private final java.util.Set<Integer> editableColumns = new java.util.HashSet<>();

    /**
     * Sets the display name for a column header at the given model column index.
     *
     * Responsibilities and side effects:
     * - Updates the column header text used by getColumnName(int)
     * - Does not modify any row data or sorting state
     * - Default implementation does not fire an event; callers that need header
     *   refresh should expect implementations to optionally fire
     *   fireTableStructureChanged()
     *
     * Contract:
     * - columnIndex must be a valid model column index (0 <= index < columnCount)
     * - columnName must be non-null
     */
    public abstract void setColumnName(int columnIndex, String columnName);

    /**
     * Returns the model column index currently used for sorting, or -1 when the
     * model is not in a sorted state.
     *
     * Notes based on the default implementation:
     * - The value is set by sortByIndex(int) and reset to -1 when an invalid
     *   index is supplied to sortByIndex(int)
     */
    public abstract int getSortByIndex();

    /**
     * Enables sorting by the specified model column and immediately resorts the
     * current rows.
     *
     * Responsibilities and side effects (default behavior):
     * - Stores the sort column index
     * - Resorts the backing row collection using per-row Sortable values from
     *   MCListItemInterface.getValueOfColumn(column)
     * - Sorting order is controlled by reverseSortOrder(); by default it is
     *   ascending until toggled
     * - Fires a single fireTableRowsUpdated(0, rowCount-1) after resorting
     *
     * Contract:
     * - If column is out of range, the model becomes unsorted (equivalent to
     *   getSortByIndex() == -1). In the default implementation a
     *   fireTableRowsUpdated(0, rowCount-1) is still issued even though no
     *   resort occurs.
     */
    public abstract void sortByIndex(int column);

    /**
     * Toggles the current sort order (ascending <-> descending) and immediately
     * resorts using the active sort column.
     *
     * Responsibilities and side effects (default behavior):
     * - Flips the internal sort order flag
     * - Resorts the backing rows
     * - Fires a single fireTableRowsUpdated(0, rowCount-1) after resorting
     *
     * Contract:
     * - If no sort column is active (getSortByIndex() == -1) the call is a
     *   no-op other than flipping the internal flag for the next sortByIndex()
     */
    public abstract void reverseSortOrder();

    /**
     * Removes all rows from the model.
     *
     * Responsibilities and side effects (default behavior):
     * - Clears the backing row collection
     * - Fires fireTableRowsDeleted(0, rowCountAfterClear) where rowCountAfterClear
     *   is 0 in the default implementation
     * - Does not change column structure or headers
     *
     * Recommended semantics:
     * - Consider firing rows-deleted over the previously occupied range
     *   (0..previousRowCount-1) for consumers that care about accurate ranges
     */
    public abstract void clearAll();

    /**
     * Removes the row at the specified model row index.
     *
     * Responsibilities and side effects (default behavior):
     * - Removes the row at index i from the backing collection
     * - Fires fireTableRowsDeleted(i, i)
     *
     * Contract:
     * - i must be a valid model row index (0 <= i < rowCount)
     */
    public abstract void removeRow(int i);

    /**
     * Removes multiple rows addressed by their model row indices.
     *
     * Performance intent:
     * - Designed to avoid O(n^2) behavior when removing many rows
     *
     * Recommended semantics (consistent with typical JTable models):
     * - Accepts a set of row indices referencing the current model
     * - Implementations should remove in descending index order to keep indices
     *   valid during removal, and should fire the minimal number of events
     *   (ideally a single consolidated rows-deleted event per contiguous block)
     */
    public abstract void removeRows(int[] indexes);

    /**
     * Replaces the column identifiers for this model.
     *
     * Responsibilities and side effects (default behavior):
     * - Updates the internal list of column names and the reported column count
     * - Fires fireTableStructureChanged() to notify JTable of the structural
     *   change
     * - Does not alter row data
     */
    public abstract void setColumnIdentifiers(String[] names);

    /**
     * Adds a single row to the model.
     *
     * Responsibilities and side effects (default behavior):
     * - Appends the item to the backing collection
     * - Fires fireTableRowsInserted(lastIndex, lastIndex)
     * - If a sort is active, immediately resorts the entire collection; callers
     *   should not rely on the initially inserted index to remain stable after
     *   the resort
     */
    public abstract void addRow(MCListItemInterface<E> item);

    /**
     * Adds multiple rows to the model in a single operation.
     *
     * Responsibilities and side effects (default behavior):
     * - Appends all items to the backing collection
     * - Fires one fireTableRowsInserted(startIndex, endIndex) spanning the newly
     *   added range
     * - If a sort is active, immediately resorts the entire collection; callers
     *   should not rely on original insertion positions post-sort
     */
    public abstract void addRows(MCListItemInterface<E>[] items);

    /**
     * Returns the model row index of the first occurrence of the specified item,
     * or -1 if the item is not present.
     */
    public abstract int indexOf(MCListItemInterface<E> item);
    
    /**
     * Returns the MCListItemInterface instance at the specified model row index.
     *
     * Contract:
     * - index must be a valid model row index (0 <= index < rowCount)
     */
    abstract MCListItemInterface<E> getRow(int index);

    public abstract boolean removeItems(MCListItemInterface<E>[] m);

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return editableColumns.contains(columnIndex);
    }

    public void setColumnEditable(int columnIndex, boolean editable) {
        if (editable) {
            if (!editableColumns.contains(columnIndex)) {
                editableColumns.add(columnIndex);
            }
        } else {
            editableColumns.remove(Integer.valueOf(columnIndex));
        }
    }
}