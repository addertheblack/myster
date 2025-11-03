package com.general.mclist;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.general.mclist.TreeMCListTableModel.TreePath;
import com.general.mclist.TreeMCListTableModel.TreePathString;

/**
 * Test class for TreeMCListTableModel to verify basic functionality.
 */
public class TreeMCListTableModelTest {
    
    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            TreePathString root = new TreePathString(new String[] {});
            JMCList<String> jmcList = TreeMCList.create(new String[] {"Name", "Type", "Size"}, root);

            
            // Set up columns
//            jmcList.setColumnName(0, "Name");
//            jmcList.setColumnName(1, "Type");
//            jmcList.setColumnName(2, "Size");
            
            // Create test data structure:
            // Root/
            //   Documents/
            //     file1.txt
            //     file2.doc
            //     Projects/
            //       project1.java
            //       project2.py
            //   Pictures/
            //     photo1.jpg
            //     photo2.png
            //   music.mp3
      
            try {
                // Root level items
                TreePath documentsPath = new TreePathString(new String[]{"Documents"});
                TreePath picturesPath = new TreePathString(new String[]{"Pictures"});
                
                // Add root level folder: Documents
                TreeMCListItem<String> treeItem = new TreeMCListItem<>(
                    root,
                    new TestColumnSortable<>("Documents", "Folder", ""),
                    java.util.Optional.of(documentsPath)
                );
                treeItem.setOpen(true);
                jmcList.addItem(treeItem);
                
                // Add root level folder: Pictures  
                jmcList.addItem(new TreeMCListItem<>(
                    root,
                    new TestColumnSortable<>("Pictures", "Folder", ""),
                    java.util.Optional.of(picturesPath)
                ));
                
                // Add root level file
                jmcList.addItem(new TreeMCListItem<>(
                    root,
                    new TestColumnSortable<>("music.mp3", "Audio File", "3.2 MB"),
                    java.util.Optional.empty()
                ));
                
                // Add files in Documents folder
                jmcList.addItem(new TreeMCListItem<>(
                    documentsPath,
                    new TestColumnSortable<>("file1.txt", "Text File", "1.2 KB"),
                    java.util.Optional.empty()
                ));
                
                jmcList.addItem(new TreeMCListItem<>(
                    documentsPath,
                    new TestColumnSortable<>("file2.doc", "Word Document", "24.5 KB"),
                    java.util.Optional.empty()
                ));
                
                // Add Projects subfolder in Documents
                TreePath projectsPath = new TreePathString(new String[]{"Documents", "Projects"});
                TreeMCListItem<String> projectItem = new TreeMCListItem<>(
                    documentsPath,
                    new TestColumnSortable<>("Projects", "Folder", ""),
                    java.util.Optional.of(projectsPath)
                );
                projectItem.setOpen(true);
                jmcList.addItem(projectItem);
                
                // Add files in Projects folder
                jmcList.addItem(new TreeMCListItem<>(
                    projectsPath,
                    new TestColumnSortable<>("project1.java", "Java File", "8.7 KB"),
                    java.util.Optional.empty()
                ));
                
                jmcList.addItem(new TreeMCListItem<>(
                    projectsPath,
                    new TestColumnSortable<>("project2.py", "Python File", "4.3 KB"),
                    java.util.Optional.empty()
                ));
                
                // Add files in Pictures folder
                jmcList.addItem(new TreeMCListItem<>(
                    picturesPath,
                    new TestColumnSortable<>("photo1.jpg", "JPEG Image", "2.1 MB"),
                    java.util.Optional.empty()
                ));
                
                jmcList.addItem(new TreeMCListItem<>(
                    picturesPath,
                    new TestColumnSortable<>("photo2.png", "PNG Image", "1.8 MB"),
                    java.util.Optional.empty()
                ));
                
                // Create and show the window
                JFrame frame = new JFrame("TreeMCListTableModel Test");
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.setLayout(new BorderLayout());
                frame.add(jmcList.getPane(), BorderLayout.CENTER);
                frame.setSize(new Dimension(600, 400));
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                
                System.out.println("Tree model test window created.");
                System.out.println("Total items in rendered list: " + jmcList.getRowCount());
                System.out.println("Try clicking column headers to sort, and try expanding/collapsing folders.");
                
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Error creating test data: " + e.getMessage());
            }
        });
    }
    
    /**
     * Test implementation of ColumnSortable for demo purposes.
     */
    private static class TestColumnSortable<E> implements ColumnSortable<E> {
        private final String name;
        private final String type;
        private final String size;
        
        public TestColumnSortable(String name, String type, String size) {
            this.name = name;
            this.type = type;
            this.size = size;
        }
        
        @Override
        public Sortable<?> getValueOfColumn(int column) {
            switch (column) {
                case 0: return new SortableString(name);
                case 1: return new SortableString(type);
                case 2: return new SortableString(size);
                default: return new SortableString("");
            }
        }
        
        @Override
        public E getObject() {
            return null; // Not needed for this test
        }
    }
}