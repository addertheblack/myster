package com.general.mclist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.general.mclist.TreeMCListTableModel.TreePath;
import com.general.mclist.TreeMCListTableModel.TreePathString;

class TestTreeMCListTableModel {
    private TreeMCListTableModel<String> model;
    private TreePath rootPath;
    private TreePath folderAPath;
    private TreePath folderBPath;
    
    @BeforeEach
    void setUp() {
        rootPath = new TreePathString(new String[] {});
        folderAPath = new TreePathString(new String[] { "FolderA" });
        folderBPath = new TreePathString(new String[] { "FolderB" });
        
        model = new TreeMCListTableModel<>(rootPath);
        model.setColumnIdentifiers(new String[] { "Name", "Size", "Date" });
    }
    
    @Test
    void testInitialState() {
        assertEquals(0, model.getRowCount(), "Initial row count should be 0");
        assertEquals(3, model.getColumnCount(), "Should have 3 columns");
        assertEquals("Name", model.getColumnName(0));
        assertEquals("Size", model.getColumnName(1));
        assertEquals("Date", model.getColumnName(2));
    }
    
    @Test
    void testAddSingleItem() {
        TreeMCListItem<String> item = createFileItem(rootPath, "file.txt");
        model.addRow(item);
        
        assertEquals(1, model.getRowCount(), "Should have 1 row after adding item");
        assertEquals(item, model.getRow(0), "Should retrieve the same item");
    }
    
    @Test
    void testAddMultipleItems() {
        TreeMCListItem<String>[] items = new TreeMCListItem[] {
            createFileItem(rootPath, "file1.txt"),
            createFileItem(rootPath, "file2.txt"),
            createFileItem(rootPath, "file3.txt")
        };
        
        model.addRows(items);
        
        assertEquals(3, model.getRowCount(), "Should have 3 rows");
    }
    
    @Test
    void testAddFolderAndFiles() {
        // Add folder
        TreeMCListItem<String> folder = createFolderItem(rootPath, "FolderA", folderAPath);
        model.addRow(folder);
        
        // Add file in folder
        TreeMCListItem<String> fileInFolder = createFileItem(folderAPath, "file.txt");
        model.addRow(fileInFolder);
        
        // Folder is closed by default, so only folder should be visible
        assertEquals(1, model.getRowCount(), "Should only show folder when closed");
        
        // Open folder
        folder.setOpen(true);
        model.resortAndRebuild();
        
        assertEquals(2, model.getRowCount(), "Should show both folder and file when open");
    }
    
    @Test
    void testRemoveItem() {
        TreeMCListItem<String> item1 = createFileItem(rootPath, "file1.txt");
        TreeMCListItem<String> item2 = createFileItem(rootPath, "file2.txt");
        
        model.addRows(new TreeMCListItem[] { item1, item2 });
        assertEquals(2, model.getRowCount());
        
        model.removeRow(0);
        assertEquals(1, model.getRowCount(), "Should have 1 row after removal");
    }
    
    @Test
    void testRemoveItems() {
        TreeMCListItem<String> item1 = createFileItem(rootPath, "file1.txt");
        TreeMCListItem<String> item2 = createFileItem(rootPath, "file2.txt");
        TreeMCListItem<String> item3 = createFileItem(rootPath, "file3.txt");
        
        model.addRows(new TreeMCListItem[] { item1, item2, item3 });
        assertEquals(3, model.getRowCount());
        
        boolean result = model.removeItems(new MCListItemInterface[] { item1, item3 });
        
        assertTrue(result, "removeItems should return true when items are removed");
        assertEquals(1, model.getRowCount(), "Should have 1 row after removing 2 items");
        assertEquals(item2, model.getRow(0), "Remaining item should be item2");
    }
    
    @Test
    void testRemoveItemsWithNonTreeItems() {
        TreeMCListItem<String> item1 = createFileItem(rootPath, "file1.txt");
        model.addRow(item1);
        
        // Try to remove with a non-TreeMCListItem (should be filtered out)
        MCListItemInterface<String> nonTreeItem = new AbstractMCListItemInterface<String>() {
            @Override
            public Sortable<?> getValueOfColumn(int i) {
                return new SortableString("dummy");
            }
            
            @Override
            public String getObject() {
                return "dummy";
            }
        };
        
        boolean result = model.removeItems(new MCListItemInterface[] { nonTreeItem });
        
        assertFalse(result, "Should return false when no valid items to remove");
        assertEquals(1, model.getRowCount(), "Row count should be unchanged");
    }
    
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void testRemoveFolderRemovesChildren(boolean folderOpen) {
        // Create folder with children
        TreeMCListItem<String> folder = createFolderItem(rootPath, "FolderA", folderAPath);
        TreeMCListItem<String> file1 = createFileItem(folderAPath, "file1.txt");
        TreeMCListItem<String> file2 = createFileItem(folderAPath, "file2.txt");
        
        model.addRows(new TreeMCListItem[] { folder, file1, file2 });
        folder.setOpen(folderOpen);
        model.resortAndRebuild();
        
        // When folder is open, all 3 items are visible; when closed, only folder is visible
        int expectedVisibleRows = folderOpen ? 3 : 1;
        assertEquals(expectedVisibleRows, model.getRowCount(), 
                     "Should have " + expectedVisibleRows + " visible row(s) when folder is " + 
                     (folderOpen ? "open" : "closed"));
        
        // All 3 items exist in the model regardless of folder open/closed state
        assertEquals(3, model.getAllElementsCount(), "Should have 3 total elements in the model");
        
        // Remove folder - should remove children too regardless of open/closed state
        model.removeItems(new MCListItemInterface[] { folder });
        
        assertEquals(0, model.getRowCount(), "All items should be removed from rendered list");
        assertEquals(0, model.getAllElementsCount(), "All items should be removed from the model");
    }
    
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void testRemoveNestedFolders(boolean foldersOpen) {
        TreePath subFolderPath = new TreePathString(new String[] { "FolderA", "SubFolder" });
        
        TreeMCListItem<String> folder = createFolderItem(rootPath, "FolderA", folderAPath);
        TreeMCListItem<String> subFolder = createFolderItem(folderAPath, "SubFolder", subFolderPath);
        TreeMCListItem<String> fileInSubFolder = createFileItem(subFolderPath, "file.txt");
        
        model.addRows(new TreeMCListItem[] { folder, subFolder, fileInSubFolder });
        folder.setOpen(foldersOpen);
        subFolder.setOpen(foldersOpen);
        model.resortAndRebuild();
        
        // When folders are open, all 3 items are visible; when closed, only parent folder is visible
        int expectedVisibleRows = foldersOpen ? 3 : 1;
        assertEquals(expectedVisibleRows, model.getRowCount(), 
                     "Should show " + expectedVisibleRows + " visible row(s) when folders are " + 
                     (foldersOpen ? "open" : "closed"));
        
        // All 3 items exist in the model regardless of folder open/closed state
        assertEquals(3, model.getAllElementsCount(), "Should have 3 total elements in the model");
        
        // Remove parent folder - should remove all nested items regardless of open/closed state
        model.removeItems(new MCListItemInterface[] { folder });
        
        assertEquals(0, model.getRowCount(), "All nested items should be removed from rendered list");
        assertEquals(0, model.getAllElementsCount(), "All nested items should be removed from the model");
    }
    
    @Test
    void testClearAll() {
        TreeMCListItem<String>[] items = new TreeMCListItem[] {
            createFileItem(rootPath, "file1.txt"),
            createFileItem(rootPath, "file2.txt"),
            createFileItem(rootPath, "file3.txt")
        };
        
        model.addRows(items);
        assertEquals(3, model.getRowCount());
        
        model.clearAll();
        assertEquals(0, model.getRowCount(), "All items should be cleared");
    }
    
    @Test
    void testSorting() {
        model.sortByIndex(0); // Sort by name
        
        TreeMCListItem<String> itemC = createFileItem(rootPath, "C-file.txt");
        TreeMCListItem<String> itemA = createFileItem(rootPath, "A-file.txt");
        TreeMCListItem<String> itemB = createFileItem(rootPath, "B-file.txt");
        
        // Add in random order
        model.addRows(new TreeMCListItem[] { itemC, itemA, itemB });
        
        // Should be sorted alphabetically
        assertEquals("A-file.txt", ((SortableString) model.getRow(0).getValueOfColumn(0)).getValue());
        assertEquals("B-file.txt", ((SortableString) model.getRow(1).getValueOfColumn(0)).getValue());
        assertEquals("C-file.txt", ((SortableString) model.getRow(2).getValueOfColumn(0)).getValue());
    }
    
    @Test
    void testReverseSortOrder() {
        model.sortByIndex(0); // Sort by name ascending
        
        TreeMCListItem<String> itemA = createFileItem(rootPath, "A-file.txt");
        TreeMCListItem<String> itemB = createFileItem(rootPath, "B-file.txt");
        
        model.addRows(new TreeMCListItem[] { itemB, itemA });
        
        assertEquals("A-file.txt", ((SortableString) model.getRow(0).getValueOfColumn(0)).getValue());
        
        model.reverseSortOrder(); // Reverse to descending
        
        assertEquals("B-file.txt", ((SortableString) model.getRow(0).getValueOfColumn(0)).getValue());
    }
    
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void testGetChildrenAtPath(boolean folderOpen) {
        TreeMCListItem<String> folder = createFolderItem(rootPath, "FolderA", folderAPath);
        TreeMCListItem<String> file1 = createFileItem(folderAPath, "file1.txt");
        TreeMCListItem<String> file2 = createFileItem(folderAPath, "file2.txt");
        
        model.addRows(new TreeMCListItem[] { folder, file1, file2 });
        folder.setOpen(folderOpen);
        model.resortAndRebuild();
        
        // getChildrenAtPath returns children from the model, regardless of folder open/closed state
        List<TreeMCListItem<String>> children = model.getChildrenAtPath(folderAPath);
        
        assertNotNull(children, "Children list should not be null");
        assertEquals(2, children.size(), "Should have 2 children in model regardless of folder state");
        assertTrue(children.contains(file1), "Should contain file1");
        assertTrue(children.contains(file2), "Should contain file2");
        
        // Verify rendered list shows correct number based on folder state
        int expectedVisibleRows = folderOpen ? 3 : 1;
        assertEquals(expectedVisibleRows, model.getRowCount(), 
                     "Should have " + expectedVisibleRows + " visible row(s) when folder is " + 
                     (folderOpen ? "open" : "closed"));
    }
    
    @Test
    void testIndexOf() {
        TreeMCListItem<String> item1 = createFileItem(rootPath, "file1.txt");
        TreeMCListItem<String> item2 = createFileItem(rootPath, "file2.txt");
        
        model.addRows(new TreeMCListItem[] { item1, item2 });
        
        assertEquals(0, model.indexOf(item1), "item1 should be at index 0");
        assertEquals(1, model.indexOf(item2), "item2 should be at index 1");
    }
    
    @Test
    void testFolderOpenCloseUnselectsChildren() {
        TreeMCListItem<String> folder = createFolderItem(rootPath, "FolderA", folderAPath);
        TreeMCListItem<String> file = createFileItem(folderAPath, "file.txt");
        
        model.addRows(new TreeMCListItem[] { folder, file });
        
        folder.setOpen(true);
        model.resortAndRebuild();
        
        // Select the file
        file.setSelected(true);
        assertTrue(file.isSelected());
        
        // Close folder - should unselect children
        folder.setOpen(false);
        model.resortAndRebuild();
        
        assertFalse(file.isSelected(), "Child should be unselected when folder closes");
    }
    
    @Test
    void testTreePathEquality() {
        TreePath path1 = new TreePathString(new String[] { "folder", "subfolder" });
        TreePath path2 = new TreePathString(new String[] { "folder", "subfolder" });
        TreePath path3 = new TreePathString(new String[] { "folder", "other" });
        
        assertEquals(path1, path2, "Identical paths should be equal");
        assertEquals(path1.hashCode(), path2.hashCode(), "Identical paths should have same hash");
        assertFalse(path1.equals(path3), "Different paths should not be equal");
    }
    
    @Test
    void testTreePathIndentLevel() {
        TreePath root = new TreePathString(new String[] {});
        TreePath level1 = new TreePathString(new String[] { "folder" });
        TreePath level2 = new TreePathString(new String[] { "folder", "subfolder" });
        
        assertEquals(0, root.getIndentLevel(), "Root should have indent 0");
        assertEquals(1, level1.getIndentLevel(), "First level should have indent 1");
        assertEquals(2, level2.getIndentLevel(), "Second level should have indent 2");
    }
    
    // Helper methods
    
    private TreeMCListItem<String> createFileItem(TreePath parent, String name) {
        ColumnSortable<String> delegate = new ColumnSortable<String>() {
            @Override
            public Sortable<?> getValueOfColumn(int i) {
                if (i == 0) return new SortableString(name);
                if (i == 1) return new SortableString("100");
                if (i == 2) return new SortableString("2024-01-01");
                return new SortableString("");
            }
            
            @Override
            public String getObject() {
                return name;
            }
        };
        
        return new TreeMCListItem<>(parent, delegate, Optional.empty());
    }
    
    private TreeMCListItem<String> createFolderItem(TreePath parent, String name, TreePath myPath) {
        ColumnSortable<String> delegate = new ColumnSortable<String>() {
            @Override
            public Sortable<?> getValueOfColumn(int i) {
                if (i == 0) return new SortableString(name);
                if (i == 1) return new SortableString("--");
                if (i == 2) return new SortableString("2024-01-01");
                return new SortableString("");
            }
            
            @Override
            public String getObject() {
                return name;
            }
        };
        
        return new TreeMCListItem<>(parent, delegate, Optional.of(myPath));
    }
}
