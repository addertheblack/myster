package com.general.mclist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TreeMCListTableModel<E> extends MCListTableModel<E> {
    private final List<String> columnNames = new ArrayList<>();
    private final List<TreeMCListItem<E>> renderedList = new ArrayList<>();
    
    private final Map<TreePath, List<TreeMCListItem<E>>> parentToRowsMap = new HashMap<>();
    private final TreePath root;
    
    private int sortByIndex = -1;

    private boolean lessThan = true;

    public TreeMCListTableModel(TreePath root) {
        this.root = root;
    }
    
    @Override
    public String getColumnName(int column) {
        return columnNames.get(column);
    }

    @Override
    public void setColumnName(int columnIndex, String columnName) {
        columnNames.set(columnIndex, columnName);
    }
    
    @Override
    public int getRowCount() {
        return renderedList.size();
    }
    
    /**
     * Package protected for unit tests
     * 
     * @return the count of every element ignoring whether it's on screen at the moment
     */
    int getAllElementsCount() {
        return countElementsRecursively(root);
    }
    
    private int countElementsRecursively(TreePath path) {
        List<TreeMCListItem<E>> children = parentToRowsMap.get(path);
        if (children == null) {
            return 0;
        }
        
        int count = children.size();
        
        // Recursively count children of containers
        for (TreeMCListItem<E> item : children) {
            if (item.isContainer()) {
                count += countElementsRecursively(item.getMyPathOrFail());
            }
        }
        
        return count;
    }

    @Override
    public int getColumnCount() {
        return columnNames.size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return getRow(rowIndex).getValueOfColumn(columnIndex);
    }

    @Override
    public void sortByIndex(int column) {
        sortByIndex = column;
        resortAndRebuild();
    }
    
    @Override
    public int getSortByIndex() {
        return sortByIndex;
    }

    @Override
    public void reverseSortOrder() {
        lessThan = !lessThan;
        resortAndRebuild();
    }

    @Override
    public void clearAll() {
        renderedList.clear();
        parentToRowsMap.clear();
        fireTableRowsDeleted(0, renderedList.size());
    }

    @Override
    public void removeRow(int i) {
        removeRows(new int[] { i });
    }

    @Override
    public void removeRows(int[] indexes) {
        if (removeTreeItems(Arrays.stream(indexes).mapToObj(i -> renderedList.get(i)).toList())) {
            resortAndRebuild();
        }
    }
    
    /**
     * Recursively deletes all items under the given tree path.
     */
    private void deleteTreePath(TreePath path) {
        List<TreeMCListItem<E>> itemsToDelete = parentToRowsMap.remove(path);

        if (itemsToDelete == null) {
            return;
        }
        
        // For each item that was a container, recursively delete its
        // children
        for (TreeMCListItem<E> item : itemsToDelete) {
            if (item.isContainer()) {
                deleteTreePath(item.getMyPathOrFail());
            }
        }
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
    public boolean removeItems(MCListItemInterface<E>[] m) {
        if (m == null || m.length == 0) {
            return false;
        }
        
        // Filter to only TreeMCListItem instances
        List<TreeMCListItem<E>> itemsToRemove = new ArrayList<>();
        for (MCListItemInterface<E> item : m) {
            if (item instanceof TreeMCListItem<E> treeItem) {
                itemsToRemove.add(treeItem);
            }
        }
        
        if (itemsToRemove.isEmpty()) {
            return false;
        }
        
        boolean modified = removeTreeItems(itemsToRemove);
        if (modified) {
            resortAndRebuild();
        }
        
        return modified;
    }
    
    /**
     * Common method to remove tree items from the parentToRowsMap.
     * Does not trigger rebuild - caller is responsible for calling resortAndRebuild().
     */
    private boolean removeTreeItems(List<TreeMCListItem<E>> itemsToRemove) {
        var modified = false;
        
        for (TreeMCListItem<E> item : itemsToRemove) {

            // Step 2: Find the list in the pathToRowsMap
            TreePath parentPath = item.getParent();
            List<TreeMCListItem<E>> siblings = parentToRowsMap.get(parentPath);

            if (siblings == null) {
                return false;
            }
            
            // Step 3: Use identity to delete that row from the map
            // If siblings is null, that's a bug - let it NPE
            if (siblings.remove(item)) {
                modified = true;
            }

            // Clean up empty lists
            if (siblings.isEmpty()) {
                parentToRowsMap.remove(parentPath);
            }

            // Delete all children recursively if this is a container
            if (item.isContainer()) {
                deleteTreePath(item.getMyPathOrFail());
            }
        }
        
        return modified;
    }
    
    public void addRow(MCListItemInterface<E> item) {
        addRows(new MCListItemInterface[] {item });
    }

    @Override
    public void addRows(MCListItemInterface<E>[] items) {
        for (MCListItemInterface<E> mcListItemInterface : items) {
            if (mcListItemInterface instanceof TreeMCListItem<E> item) {
                parentToRowsMap.computeIfAbsent(item.getParent(), _ -> new ArrayList<>()).add(item);
                
                // this is just here so that our insert event below can happen without causing a crash from listeners that immediately try
                // and read those extra rows. In reality the visual display of the list is going to look fucked because the items
                // might be deep inside and exiting path. This is only temporary and should be fixed by the resort before the next repaint()
                // can happen
                renderedList.add(item);
            } else {
                throw new IllegalArgumentException("MCListItemInterface must be an instance of TreeMCListItem");
            }
        }
        
        // we can't notify this because the items are not available until after a resort so we have no idea what's happening
        fireTableRowsInserted(renderedList.size() - items.length, renderedList.size() - 1);
        resortAndRebuild();
    }
    
    public void resortAndRebuild() {
        // Clear and rebuild the rendered list
        renderedList.clear();
        
        // Start with root and recursively build the display order
        buildRenderedListRecursively(root);
        
        // Fire update event
        fireTableDataChanged();
    }
    
    /**
     * @param path - must be a container path
     * @return a copy of the children at Path
     * @throws NullPointerException if path does not exist or does not represent a container
     */
    public List<TreeMCListItem<E>> getChildrenAtPath(TreePath path) {
        return new ArrayList<TreeMCListItem<E>>(parentToRowsMap.get(path));
    }

    private void buildRenderedListRecursively(TreePath path) {
        List<TreeMCListItem<E>> children = parentToRowsMap.get(path);
        if (children == null) return;
        
        sortChildren(children);
        
        for (TreeMCListItem<E> item : children) {
            // Add the container/item first
            renderedList.add(item);

            // If it's an open container, recursively add its children
            if (item.isContainer()) {
                if (item.isOpen()) {
                    buildRenderedListRecursively(item.getMyPathOrFail());
                } else {
                    // need to make sure there's nothing still selected under that tree
                    unselectRecursively(item.getMyPathOrFail());
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void sortChildren(List<TreeMCListItem<E>> children) {
        if (sortByIndex == -1) {
            return; // this doesn't work well with this impl because we destroy insertion order.. meh whatever
        }
        Collections.sort(children, (a, b) -> {
            Sortable sa = a.getValueOfColumn(sortByIndex);
            Sortable sb = b.getValueOfColumn(sortByIndex);

            if (sa.equals(sb)) {
                return 0;
            }
            
            var bool = lessThan ? sa.isLessThan(sb) : sa.isGreaterThan(sb);
            return bool ? -1 : 1;
        });
    }
    
    private void unselectRecursively(TreePath path) {
        List<TreeMCListItem<E>> children = parentToRowsMap.get(path);
        if (children == null) return;


        for (TreeMCListItem<E> item : children) {
            item.setSelected(false);

            if (item.isContainer()) {
                unselectRecursively(item.getMyPathOrFail());
            }
        }
    }

    @Override
    public int indexOf(MCListItemInterface<E> item) {
        return renderedList.indexOf(item);
    }

    @Override
    MCListItemInterface<E> getRow(int index) {
        return renderedList.get(index);
    }
    
    /// ====== hierarchy
    
    public interface TreePath {
        int getIndentLevel();
        
        // you also NEED to implement these correctly!
        int hashCode();
        boolean equals(Object obj);
    }
    
    public static class TreePathString implements TreePath {
        private final String[] path;

        /**
         * An empty path is the root element
         */
        public TreePathString(String[] path) {
            this.path = path.clone();
        }
        
        public int getIndentLevel() {
            return path.length;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(path);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            TreePathString other = (TreePathString) obj;
            return Arrays.equals(path, other.path);
        }
    }
    
    public static class TreeMCListItem<E> extends AbstractMCListItemInterface<E> {
        private final TreePath parent;
        private final ColumnSortable<E> delegate;
        private final Optional<TreePath> myPath;
        
        private boolean open = false; 
        
        /**
         * @param myPath present if this is a container/folder. Empty if this is a lead node.
         */
        public TreeMCListItem(TreePath parent, ColumnSortable<E> delegate, Optional<TreePath> myPath) {
            this.parent = parent;
            this.delegate = delegate;
            this.myPath = myPath;
        }
        
        public TreePath getParent() {
            return parent;
        }
        
        public Sortable<?> getValueOfColumn(int i) {
            return delegate.getValueOfColumn(i);
        }

        public E getObject() {
            return delegate.getObject();
        }

        /**
         * @return true if this item represents something that contains other nodes. 
         */
        public boolean isContainer() {
            return myPath.isPresent();
        }

        public TreePath getMyPathOrFail() {
            return myPath.get();
        }

        public boolean isOpen() {
            return open;
        }

        /**
         * Don't forget to rebuild the model to pick up this change. 
         */
        public void setOpen(boolean open) {
            this.open = open;
        }
    }
}
