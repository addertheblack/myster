package com.general.mclist;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.general.mclist.TreeMCListTableModel.TreePath;
import com.general.mclist.TreeMCListTableModel.TreePathString;
import com.general.util.IconLoader;

public class TreeMCList {
    
    public static <E> JMCList<E> create(String[] columns, TreePath root) {
        // Create the tree model
        TreeMCListTableModel<E> model = new TreeMCListTableModel<>(root);
        
        // Create the JMCList with our tree model
        JMCList<E> list = new JMCList<E>(columns.length, true, model) {
            int lastChev=-1;
            
            protected void processMouseEvent(java.awt.event.MouseEvent e) {
                if (e.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED
                        || e.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED
                        || e.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED) {
                    int row = rowAtPoint(e.getPoint());
                    int col = columnAtPoint(e.getPoint());

                    if (row >= 0 && col == 0) {
                        TreeMCListTableModel<String> treeModel = (TreeMCListTableModel<String>) getModel();
                        TreeMCListItem<String> treeRow = (TreeMCListItem<String>) treeModel.getRow(row);

                        if (treeRow.isContainer()) {
                            int indentLevel = treeRow.getParent().getIndentLevel();
                            int leftIndent = 10 * indentLevel;
                            int iconSize = getRowHeight();

                            int chevronLeft = leftIndent;
                            int chevronRight = leftIndent + iconSize;

                            java.awt.Rectangle cellRect = getCellRect(row, col, false);
                            int relativeX = e.getX() - cellRect.x;

                            if (relativeX >= chevronLeft && relativeX <= chevronRight) {
                                if (e.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED) {
                                    lastChev = row;
                                }
                                if (e.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED) {
                                    if (row != lastChev) {
                                        lastChev = -1;
                                    } else {
                                        // Handle chevron click - don't call
                                        // super
                                        treeRow.setOpen(!treeRow.isOpen());
                                        treeModel.resortAndRebuild();
                                        lastChev = -1;
                                    }
                                }
                                if (e.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED) {
                                    // NOTHING!
                                    // this is so that double clicking the triangle doesn't 
                                    // cause the double click event to fire
                                }

                                e.consume();
                                return; // Exit without calling super.processMouseEvent()
                            }
                        }
                    }
                }
                
                // Only call super if we didn't handle the chevron click
                super.processMouseEvent(e);
            }
            
            @Override
            protected void processMouseMotionEvent(java.awt.event.MouseEvent e) {
                // this clause is not executed before the table grabs it
                if (e.getID() == java.awt.event.MouseEvent.MOUSE_DRAGGED && lastChev != -1) {
                    e.consume();
                    return; // Exit without calling super.processMouseEvent()
                }
                
                // sometimes we don't get a mouse released so this fixes the var..
                if (e.getID() == java.awt.event.MouseEvent.MOUSE_MOVED && lastChev != -1) {
                    lastChev = -1;
                }
                
                super.processMouseMotionEvent(e);
                
            }
        };

        for (int i = 0; i < columns.length; i++) {
            list.setColumnName(i, columns[i]);
        }
        
        // list.setRowHeight(24);
        list.getTableHeader().getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable l,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                
                var v = (SortableString)value;
                
                JMCList<String> mcList = (JMCList<String>) l;
                
                TreeMCListTableModel<String> treeModel = (TreeMCListTableModel<String>) mcList.getModel();
                
                
                var treeRow = (TreeMCListItem<String>)treeModel.getRow(row);
                
                var indentLevel = treeRow.getParent().getIndentLevel();


                final var iconSize = l.getRowHeight();

                // this returns myself so it's pointless to capture the
                // result.
                super.getTableCellRendererComponent(l,
                                                    value,
                                                    isSelected,
                                                    hasFocus,
                                                    row,
                                                    column);
                
                Icon chevIcon = null;
                if (treeRow.isContainer()) {
                    FlatSVGIcon icon = treeRow.isOpen()
                            ? IconLoader.loadSvg(IconLoader.class, "chevron-down-svgrepo-com")
                            : IconLoader.loadSvg(IconLoader.class, "chevron-right-svgrepo-com");
                    chevIcon = icon.derive(iconSize, iconSize);
                } else {
                    BufferedImage emptyImage = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
                    chevIcon = new ImageIcon(emptyImage);
                }
                
                var fileIcon = treeRow.isContainer() ? 
                        IconLoader.loadSvg(IconLoader.class,"folder-svgrepo-com") :
                            IconLoader.loadSvg(IconLoader.class,"file-svgrepo-com");
                fileIcon = fileIcon.derive(iconSize, iconSize);
                
                
                setIcon(mergeIcons(chevIcon, fileIcon, 4));
                setBorder(new EmptyBorder(new Insets(0,  10 * indentLevel, 0, 0)));
                setText("" + v);

                return this;
            }
        });
        
        return list;
    }
    
    
    public static Icon mergeIcons(Icon icon1, Icon icon2, int spacing) {
        if (icon1 == null) return icon2;
        if (icon2 == null) return icon1;
        
        int width = icon1.getIconWidth() + icon2.getIconWidth() + spacing;
        int height = Math.max(icon1.getIconHeight(), icon2.getIconHeight());
        
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = combined.createGraphics();
        
        // Enable antialiasing for better quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw first icon
        icon1.paintIcon(null, g2d, 0, (height - icon1.getIconHeight()) / 2);
        
        // Draw second icon with spacing
        icon2.paintIcon(null, g2d, icon1.getIconWidth() + spacing, (height - icon2.getIconHeight()) / 2);
        
        g2d.dispose();
        
        return new ImageIcon(combined);
    }
}
