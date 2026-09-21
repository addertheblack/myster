package com.general.mclist;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.Optional;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.general.mclist.TreeMCListTableModel.TreePath;
import com.general.util.IconLoader;
import com.myster.thumbnail.ui.ThumbnailUiUtils;

public class TreeMCList {
    /** Passive per-item icon lookup used by the tree renderer on the EDT. */
    @FunctionalInterface
    public interface IconProvider<E> {
        Optional<Icon> getIcon(TreeMCListItem<E> item, int logicalSize);
    }
    private static final FlatSVGIcon downChevron = IconLoader.loadSvg(IconLoader.class, "chevron-down-svgrepo-com");
    private static final FlatSVGIcon rightChevron = IconLoader.loadSvg(IconLoader.class, "chevron-right-svgrepo-com");
    
    private static final FlatSVGIcon folderIcon = IconLoader.loadSvg(IconLoader.class,"folder-svgrepo-com");
    private static final FlatSVGIcon fileIcon = IconLoader.loadSvg(IconLoader.class,"file-svgrepo-com");
    
    public static <E> JMCList<E> create(String[] columns, TreePath root) {
        return create(columns, root, folderIcon, fileIcon, (_, _) -> Optional.empty());
    }

    public static <E> JMCList<E> create(String[] columns, TreePath root, IconProvider<E> provider) {
        return create(columns, root, folderIcon, fileIcon, provider);
    }
    
    public static <E> JMCList<E> create(String[] columns, TreePath root, FlatSVGIcon customFolderIcon, FlatSVGIcon customFileIcon) {
        return create(columns, root, customFolderIcon, customFileIcon, (_, _) -> Optional.empty());
    }

    public static <E> JMCList<E> create(String[] columns, TreePath root,
                                        FlatSVGIcon customFolderIcon, FlatSVGIcon customFileIcon,
                                        IconProvider<E> provider) {
        Objects.requireNonNull(provider, "provider");
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
        list.getTableHeader().getColumnModel().getColumn(0)
                .setCellRenderer(createTreeFirstColumnRenderer(containerIcon, itemIcon, provider));
        
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
    private static <E> DefaultTableCellRenderer createTreeFirstColumnRenderer(
            FlatSVGIcon containerIcon, FlatSVGIcon itemIcon, IconProvider<E> provider) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable l,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                final JMCList<E> mcList = (JMCList<E>) l;
                final TreeMCListTableModel<E> treeModel = (TreeMCListTableModel<E>) mcList.getModel();
                final var treeRow = (TreeMCListItem<E>) treeModel.getRow(l.convertRowIndexToModel(row));
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

                Icon fOrFIcon = provider.getIcon(treeRow, iconSize)
                        .map(icon -> fitIcon(icon, iconSize))
                        .orElseGet(() -> {
                            FlatSVGIcon fallback = (treeRow.isContainer() ? containerIcon : itemIcon)
                                    .derive(iconSize, iconSize);
                            fallback.setColorFilter(new FlatSVGIcon.ColorFilter(color -> this.getForeground()));

                            return fallback;
                        });

                setIcon(mergeIcons(chevIcon, fOrFIcon, 4));
                setBorder(new EmptyBorder(new Insets(0, 10 * indentLevel, 0, 0)));
                setText(String.valueOf(value));

                return this;
            }

            private Icon buildIcon(TreeMCListItem<E> treeRow, int iconSize) {
                Icon chevIcon;
                if (treeRow.isContainer()) {
                    FlatSVGIcon icon = treeRow.isOpen() ? downChevron : rightChevron;

                    icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> this
                            .getForeground()));
                    chevIcon = icon.derive(iconSize, iconSize);
                } else {
                    chevIcon = emptyIcon(iconSize, iconSize);
                }
                return chevIcon;
            }
        };
    }

    private static Icon fitIcon(Icon source, int side) {
        java.awt.Rectangle target = ThumbnailUiUtils.aspectFit(source,
                new java.awt.Rectangle(0, 0, side, side));
        if (target.isEmpty()) {
            return emptyIcon(side, side);
        }
        return new Icon() {
            public int getIconWidth() { return side; }
            public int getIconHeight() { return side; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics copy = g.create();
                try {
                    Graphics2D scaled = (Graphics2D) copy;
                    scaled.translate(x + target.x, y + target.y);
                    scaled.scale((double) target.width / source.getIconWidth(),
                                 (double) target.height / source.getIconHeight());
                    source.paintIcon(c, scaled, 0, 0);
                } finally {
                    copy.dispose();
                }
            }
        };
    }

    private static Icon emptyIcon(int width, int height) {
        return new Icon() {
            public int getIconWidth() { return width; }
            public int getIconHeight() { return height; }
            public void paintIcon(Component c, Graphics g, int x, int y) {}
        };
    }

    /** Returns the logical bounds occupied by a row's complete tree icon. */
    public static java.awt.Rectangle getItemIconBounds(JMCList<?> list, int viewRow) {
        if (list.getColumnCount() == 0 || viewRow < 0 || viewRow >= list.getRowCount()) {
            return new java.awt.Rectangle();
        }
        TreeMCListItem<?> item = (TreeMCListItem<?>) list.getMCListItem(viewRow);
        int indent = 10 * item.getParent().getIndentLevel();
        int size = list.getRowHeight();
        return new java.awt.Rectangle(list.getCellRect(viewRow, 0, false).x + indent,
                list.getCellRect(viewRow, 0, false).y, size * 2 + 4, size);
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

    /**
     * Composes two icons horizontally, centered vertically, with spacing in logical units.
     * Children are painted with the supplied component and current graphics transform, so
     * the result follows the destination's display scale without a fixed-resolution buffer.
     *
     * @return the composition, the other icon when one is null, or null when both are null
     */
    public static Icon mergeIcons(Icon icon1, Icon icon2, int spacing) {
        if (icon1 == null) return icon2;
        if (icon2 == null) return icon1;
        
        int width = icon1.getIconWidth() + icon2.getIconWidth() + spacing;
        int height = Math.max(icon1.getIconHeight(), icon2.getIconHeight());
        
        return new Icon() {
            @Override
            public int getIconWidth() {
                return width;
            }
            
            @Override
            public int getIconHeight() {
                return height;
            }
            
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics first = g.create();
                Graphics second = g.create();
                try {
                    icon1.paintIcon(c, first, x, y + (height - icon1.getIconHeight()) / 2);
                    icon2.paintIcon(c, second, x + icon1.getIconWidth() + spacing,
                                    y + (height - icon2.getIconHeight()) / 2);
                } finally {
                    first.dispose();
                    second.dispose();
                }
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
