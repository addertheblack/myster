package com.general.mclist;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.general.mclist.TreeMCListTableModel.TreePath;
import com.general.util.IconLoader;

public class TreeMCList {
    private static final FlatSVGIcon downChevron = IconLoader.loadSvg(IconLoader.class, "chevron-down-svgrepo-com");
    private static final FlatSVGIcon rightChevron = IconLoader.loadSvg(IconLoader.class, "chevron-right-svgrepo-com");
    
    private static final FlatSVGIcon folderIcon = IconLoader.loadSvg(IconLoader.class,"folder-svgrepo-com");
    private static final FlatSVGIcon fileIcon = IconLoader.loadSvg(IconLoader.class,"file-svgrepo-com");
    
    public static <E> JMCList<E> create(String[] columns, TreePath root) {
        return create(columns, root, folderIcon, fileIcon);
    }
    
    public static <E> JMCList<E> create(String[] columns, TreePath root, FlatSVGIcon customFolderIcon, FlatSVGIcon customFileIcon) {
        // Use custom icons if provided, otherwise fall back to defaults
        final FlatSVGIcon containerIcon = customFolderIcon != null ? customFolderIcon : folderIcon;
        final FlatSVGIcon itemIcon = customFileIcon != null ? customFileIcon : fileIcon;
        
        // Create the tree model
        TreeMCListTableModel<E> model = new TreeMCListTableModel<>(root);
        
        // Create the JMCList with our tree model
        JMCList<E> list = new TreeMCListImpl<E>(columns.length, true, model);

        for (int i = 0; i < columns.length; i++) {
            list.setColumnName(i, columns[i]);
        }
        
        // list.setRowHeight(24);
        list.getTableHeader().getColumnModel().getColumn(0).setCellRenderer(createTreeFirstColumnRenderer(containerIcon, itemIcon));
        
        // Enable type-to-select functionality on the first column by default
        // todo, move to better spot
        TypeToSelectKeyListener.enableSearch(list,  0);
        
        // Add keyboard support for opening/closing tree items with arrow keys
        list.addKeyListener(createTreeKeyboardNavigationHandler(list));
        
        return list;
    }

    /**
     * Creates the cell renderer for column 0 of a tree list: draws the indent,
     * chevron (open/closed), and folder/file icon merged into a single cell.
     */
    private static DefaultTableCellRenderer createTreeFirstColumnRenderer(FlatSVGIcon containerIcon, FlatSVGIcon itemIcon) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable l,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                final var v = (SortableString) value;
                final JMCList<String> mcList = (JMCList<String>) l;
                final TreeMCListTableModel<String> treeModel = (TreeMCListTableModel<String>) mcList.getModel();
                final var treeRow = (TreeMCListItem<String>) treeModel.getRow(row);
                final var indentLevel = treeRow.getParent().getIndentLevel();
                final var iconSize = l.getRowHeight();

                // this returns myself so it's pointless to capture the
                // result.
                super.getTableCellRendererComponent(l,
                        value,
                        isSelected,
                        hasFocus,
                        row,
                        column);
                Icon chevIcon = buildIcon(treeRow, iconSize);

                var fOrFIcon = treeRow.isContainer() ? containerIcon : itemIcon;
                fOrFIcon = fOrFIcon.derive(iconSize, iconSize);
                fOrFIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> this
                        .getForeground()));

                setIcon(mergeIcons(chevIcon, fOrFIcon, 4));
                setBorder(new EmptyBorder(new Insets(0, 10 * indentLevel, 0, 0)));
                setText("" + v);

                return this;
            }

            private Icon buildIcon(TreeMCListItem<String> treeRow, int iconSize) {
                Icon chevIcon;
                if (treeRow.isContainer()) {
                    FlatSVGIcon icon = treeRow.isOpen() ? downChevron : rightChevron;

                    icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> this
                            .getForeground()));
                    chevIcon = icon.derive(iconSize, iconSize);
                } else {
                    BufferedImage emptyImage =
                            new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
                    chevIcon = new ImageIcon(emptyImage);
                }
                return chevIcon;
            }
        };
    }

    /** Key handler that opens/closes container rows with the left and right arrow keys. */
    private static <E> KeyAdapter createTreeKeyboardNavigationHandler(JMCList<E> list) {
        return new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int selectedRow = list.getSelectedRow();
                if (selectedRow < 0) {
                    return;
                }

                TreeMCListTableModel<E> treeModel = (TreeMCListTableModel<E>) list.getModel();
                TreeMCListItem<E> treeRow = (TreeMCListItem<E>) treeModel.getRow(selectedRow);

                if (!treeRow.isContainer()) {
                    return; // Only handle containers
                }

                if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    // Right arrow: open the item if it's closed
                    if (!treeRow.isOpen()) {
                        treeRow.setOpen(true);
                        treeModel.resortAndRebuild();
                    }
                    e.consume(); // Always consume to prevent JTable's default horizontal navigation
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    // Left arrow: close the item if it's open
                    if (treeRow.isOpen()) {
                        treeRow.setOpen(false);
                        treeModel.resortAndRebuild();
                    }
                    e.consume(); // Always consume to prevent JTable's default horizontal navigation
                }
            }
        };
    }

    public static Icon mergeIcons(Icon icon1, Icon icon2, int spacing) {
        if (icon1 == null) return icon2;
        if (icon2 == null) return icon1;
        
        int width = icon1.getIconWidth() + icon2.getIconWidth() + spacing;
        int height = Math.max(icon1.getIconHeight(), icon2.getIconHeight());
        
        // Create a 2x resolution buffer for crisp HiDPI rendering
        int scale = 2;
        int scaledWidth = width * scale;
        int scaledHeight = height * scale;
        
        BufferedImage combined = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = combined.createGraphics();
        
        // Scale the graphics context to 2x
        g2d.scale(scale, scale);
        
        // Enable high-quality rendering hints
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        
        // Draw first icon
        icon1.paintIcon(null, g2d, 0, (height - icon1.getIconHeight()) / 2);
        
        // Draw second icon with spacing
        icon2.paintIcon(null, g2d, icon1.getIconWidth() + spacing, (height - icon2.getIconHeight()) / 2);
        
        g2d.dispose();
        
        // Create an ImageIcon that will scale the 2x image appropriately
        return new ImageIcon(combined) {
            @Override
            public int getIconWidth() {
                return width;
            }
            
            @Override
            public int getIconHeight() {
                return height;
            }
            
            @Override
            public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(getImage(), x, y, width, height, null);
                g2.dispose();
            }
        };
    }

    private static class TreeMCListImpl<E> extends JMCList<E> {
        private int lastChev=-1;

        public TreeMCListImpl(int length, boolean b, TreeMCListTableModel<E> model) {
            super(length, b, model);
        }

        @Override
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

        @Override
        public void tableChanged(javax.swing.event.TableModelEvent e) {
            // A structure change (HEADER_ROW) causes JTable to recreate all TableColumn
            // objects via createDefaultColumnsFromModel(), wiping any custom cell renderers.
            // Column 0 carries the tree renderer (icons, indentation, chevrons), so save
            // and restore it around the super call.
            javax.swing.table.TableCellRenderer col0Renderer = null;
            if (e.getFirstRow() == javax.swing.event.TableModelEvent.HEADER_ROW
                    && getColumnCount() > 0) {
                col0Renderer = getColumnModel().getColumn(0).getCellRenderer();
            }
            super.tableChanged(e);
            if (col0Renderer != null) {
                getColumnModel().getColumn(0).setCellRenderer(col0Renderer);
            }
        }
    }
}
