package com.general.mclist;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import com.sun.java.util.collections.Iterator;
import com.sun.java.util.collections.List;
import com.sun.java.util.collections.Vector;

/**
 */
public class JMCList extends JTable implements MCList {

    private JScrollPane mScrollPane;

    private List listeners;

    public JMCList() {
        this(1, true);
    }

    /**
     * @param numberOfColumns
     * @param singleselect
     */
    public JMCList(int numberOfColumns, boolean singleselect) {
        listeners = new Vector();
        setModel(new MCListTableModel());
        mScrollPane = new JScrollPane(this);

        setSelectionModel(new MCListSelectionModel(getMCTableModel()));

        setColumnSelectionAllowed(false);
        getTableHeader().setReorderingAllowed(false);

        getTableHeader().addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                JTableHeader header = getTableHeader();
                if (header.getResizingColumn() != null)
                    return;
                int selectedColumn = getMCTableModel().getSortByIndex();
                int clickedColumn = header.columnAtPoint((e.getPoint()));
                if (clickedColumn == selectedColumn) {
                    reverseSortOrder();
                } else {
                    sortBy(clickedColumn);
                }
            }
        });

        getTableHeader().setDefaultRenderer(
                new MCHeaderCellRenderer(getTableHeader().getDefaultRenderer(), getMCTableModel()));

        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    for (Iterator iterator = listeners.iterator(); iterator.hasNext();) {
                        MCListEventListener handler = (MCListEventListener) iterator.next();
                        handler.doubleClick(new MCListEvent(JMCList.this));
                    }
                }
            }
        });

        getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent e) {
                if (listeners == null)
                    return;
                for (Iterator iterator = listeners.iterator(); iterator.hasNext();) {
                    MCListEventListener handler = (MCListEventListener) iterator.next();
                    handler.unselectItem(new MCListEvent(JMCList.this));
                    handler.selectItem(new MCListEvent(JMCList.this));
                }
            }

        });

        setNumberOfColumns(numberOfColumns);
    }

    private MCListTableModel getMCTableModel() {
        return (MCListTableModel) getModel();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#setNumberOfColumns(int)
     */
    public void setNumberOfColumns(int numberOfColumns) {
        String[] columnarray = new String[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++)
            columnarray[i] = "unnamed";

        if (numberOfColumns == 1) {
            setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        } else {
            setAutoResizeMode(AUTO_RESIZE_OFF);
        }
        getMCTableModel().setColumnIdentifiers(columnarray);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#setColumnName(int, java.lang.String)
     */
    public void setColumnName(int columnNumber, String name) {
        getMCTableModel().setColumnName(columnNumber, name);
        getTableHeader().getColumnModel().getColumn(columnNumber).setHeaderValue(name);
        getTableHeader().repaint();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#setColumnWidth(int, int)
     */
    public void setColumnWidth(int index, int size) {
        getColumnModel().getColumn(index).setPreferredWidth(size);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#sortBy(int)
     */
    public void sortBy(int column) {
        getTableHeader().repaint();
        getMCTableModel().sortByIndex(column);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#getPane()
     */
    public Container getPane() {
        return mScrollPane;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#addItem(com.general.mclist.MCListItemInterface)
     */
    public void addItem(MCListItemInterface m) {
        getMCTableModel().addRow(m);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#addItem(com.general.mclist.MCListItemInterface[])
     */
    public void addItem(MCListItemInterface[] m) {
        getMCTableModel().addRows(m);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#setSorted(boolean)
     */
    int lastSort = -1;

    public void setSorted(boolean isSorted) {
        MCListTableModel model = getMCTableModel();
        if (isSorted && model.getSortByIndex() == -1) {
            model.sortByIndex(lastSort);
        } else {
            lastSort = model.getSortByIndex();
            model.sortByIndex(-1);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#isSorted()
     */
    public boolean isSorted() {
        return getMCTableModel().getSortByIndex() != -1;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#isSelected(int)
     */
    public boolean isSelected(int i) {
        return getMCTableModel().getRow(i).isSelected();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#select(int)
     */
    public void select(int i) {
        if (!(i >= 0 && i < getRowCount()))
            return;
        getMCListSelectionModel().addSelectionInterval(i, i);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#unselect(int)
     */
    public void unselect(int i) {
        getMCListSelectionModel().removeIndexInterval(i, i);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#clearAllSelected()
     */
    public void clearAllSelected() {
        getMCTableModel().removeRows(getMCListSelectionModel().getSelectedIndexes());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#toggle(int)
     */
    public void toggle(int i) {
        getMCListSelectionModel().toggle(i);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#isAnythingSelected()
     */
    public boolean isAnythingSelected() {
        return getSelectedIndex() != -1;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#getSelectedIndexes()
     */
    public int[] getSelectedIndexes() {
        return getMCListSelectionModel().getSelectedIndexes();
    }

    /**
     * @return
     */
    private MCListSelectionModel getMCListSelectionModel() {
        return (MCListSelectionModel) getSelectionModel();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#getSelectedIndex()
     */
    public int getSelectedIndex() {
        return getMCListSelectionModel().getSelectedIndex();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#setSingleSelect(boolean)
     */
    public void setSingleSelect(boolean b) {
        setSelectionMode(b ? ListSelectionModel.SINGLE_SELECTION
                : ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#isSingleSelect()
     */
    public boolean isSingleSelect() {
        return getSelectionModel().getSelectionMode() == ListSelectionModel.SINGLE_SELECTION;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#addMCListEventListener(com.general.mclist.MCListEventListener)
     */
    public void addMCListEventListener(MCListEventListener listener) {
        listeners.add(listener);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#clearAll()
     */
    public void clearAll() {
        getMCTableModel().clearAll();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#removeItem(int)
     */
    public void removeItem(int i) {
        getMCTableModel().removeRow(i);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#removeItem(com.general.mclist.MCListItemInterface)
     */
    public void removeItem(MCListItemInterface o) {
        getMCTableModel().removeRow(o);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#removeItem(int[])
     */
    public void removeItem(int[] indexes) {
        getMCTableModel().removeRows(indexes);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#getItem(int)
     */
    public Object getItem(int i) {
        return getMCTableModel().getRow(i).getObject();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#getMCListItem(int)
     */
    public MCListItemInterface getMCListItem(int i) {
        return getMCTableModel().getRow(i);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#reverseSortOrder()
     */
    public void reverseSortOrder() {
        getMCTableModel().reverseSortOrder();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.general.jmclist.MCList#length()
     */
    public int length() {
        return getMCTableModel().getRowCount();
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        final JMCList list = new JMCList();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                JFrame frame = new JFrame("Testing...");

                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                list.setNumberOfColumns(5);
                list.setColumnName(0, "First Column");
                list.setColumnName(1, "Second Column");
                list.setColumnName(2, "Third Colum");
                list.setColumnName(3, "Reeeeeeeeeeeeeeeeeeeeaaaaaaaaaaalllllyyyy loooooong column");
                list.setColumnName(4, "short");

                for (int i = 0; i < 100; i++) {
                    list.addItem(new GenericMCListItem(new Sortable[] {
                            new SortableString("number " + i), new SortableString("number 2"),
                            new SortableString("number 3"), new SortableString("number 4"),
                            new SortableString("number 5") }));
                }

                frame.getContentPane().add(list.getPane());

                frame.show();
            }
        });
        //
        //        for (int i = 0; i < 20; i++) {
        //            Thread.sleep(1000);
        //            final int index = i;
        //
        //            SwingUtilities.invokeAndWait(new Runnable() {
        //                public void run() {
        //                    list.setColumnWidth(index % 5, (index % 4) * 100);
        //                }
        //            });
        //
        //            Thread.sleep(2000);
        //            SwingUtilities.invokeAndWait(new Runnable() {
        //                public void run() {
        //                    list.removeItem(10);
        //                }
        //            });
        //        }

    }
}

class MCListTableModel extends AbstractTableModel {
    Vector columnNames = new Vector();

    Vector rowValues = new Vector();

    int sortByIndex = -1;

    private boolean lessThan = true;

    public String getColumnName(int column) {
        return (String) columnNames.elementAt(column);
    }

    public void setColumnName(int columnIndex, String columnName) {
        columnNames.setElementAt(columnName, columnIndex);
    }

    /**
     * @return
     */
    public int getSortByIndex() {
        return sortByIndex;
    }

    /**
     * @param column
     */
    public void sortByIndex(int column) {
        sortByIndex = column;
        resort();
        fireTableRowsUpdated(0, rowValues.size());
    }

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

        com.sun.java.util.collections.Collections.sort(rowValues,
                new com.sun.java.util.collections.Comparator() {
                    public int compare(Object a, Object b) {
                        Sortable sa = ((MCListItemInterface) a).getValueOfColumn(sortByIndex);
                        Sortable sb = ((MCListItemInterface) b).getValueOfColumn(sortByIndex);

                        if (sa.equals(sb))
                            return 0;

                        int cmp = (sa.isLessThan(sb) ? -1 : 1);
                        return (lessThan ? cmp : -cmp);
                    }
                });
    }

    /**
     *  
     */
    public void clearAll() {
        rowValues = new Vector();
        fireTableRowsDeleted(0, rowValues.size());
    }

    /**
     * @param i
     */
    public void removeRow(int i) {
        rowValues.removeElementAt(i);
        fireTableRowsDeleted(i, i);
    }

    /**
     * @param o
     */
    public void removeRow(MCListItemInterface o) {
        int index = rowValues.indexOf(o);
        removeRow(index);

    }

    /**
     * @param indexes
     */
    public void removeRows(int[] indexes) {
        for (int i = rowValues.size(); i >= 0; i++) {
            rowValues.removeElementAt(i);
        }
        fireTableRowsDeleted(0, rowValues.size());
    }

    public void setColumnIdentifiers(String[] names) {
        columnNames = new Vector(names.length);
        for (int i = 0; i < names.length; i++) {
            columnNames.add(names[i]);
        }
        fireTableStructureChanged();
    }

    public void addRow(MCListItemInterface item) {
        rowValues.add(item);
        fireTableRowsInserted(rowValues.size() - 1, rowValues.size() - 1);
        resort();
    }

    public void addRows(MCListItemInterface[] items) {
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
    public Object getValueAt(int rowIndex, int columnIndex) {
        return getRow(rowIndex).getValueOfColumn(columnIndex);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.table.AbstractTableModel#getColumnCount()
     */
    public int getColumnCount() {
        return columnNames.size();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.table.AbstractTableModel#getRowCount()
     */
    public int getRowCount() {
        return rowValues.size();
    }

    MCListItemInterface getRow(int index) {
        return (MCListItemInterface) rowValues.elementAt(index);
    }

    /**
     * @param item
     * @return
     */
    public int indexOf(MCListItemInterface item) {
        return rowValues.indexOf(item);
    }

}

class MCListSelectionModel implements ListSelectionModel {
    private MCListTableModel tableModel;

    private MCListItemInterface anchorValue;

    private int leadSelectionIndex;

    private EventListenerList listenerList = new EventListenerList();

    private boolean valueIsAdjusting;

    private int selectionMode;

    MCListSelectionModel(MCListTableModel tableModel) {
        this.tableModel = tableModel;
    }

    /**
     * @param i
     */
    public void toggle(int i) {
        tableModel.getRow(i).setSelected(!tableModel.getRow(i).isSelected());
        fireValueChanged(i, i, valueIsAdjusting);
    }

    /**
     * @return
     */
    public int[] getSelectedIndexes() {
        Vector vector = new Vector(tableModel.getRowCount());

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getRow(i).isSelected())
                vector.add(new Integer(i));
        }

        int[] indexes = new int[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            indexes[i] = ((Integer) vector.elementAt(i)).intValue();
        }

        return indexes;
    }

    public int getSelectedIndex() {
        return getMinSelectionIndex();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#setSelectionInterval(int, int)
     */
    public void setSelectionInterval(int index0, int index1) {
        clearSelection();
        addSelectionInterval(index0, index1);
        setAnchorSelectionIndex(index0);
        fireValueChanged();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#addSelectionInterval(int, int)
     */
    public void addSelectionInterval(int index0, int index1) {
        for (int i = index0; i <= index1; i++) {
            tableModel.getRow(i).setSelected(true);
        }
        fireValueChanged();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#removeSelectionInterval(int, int)
     */
    public void removeSelectionInterval(int index0, int index1) {
        for (int i = index0; i <= index1; i++) {
            tableModel.getRow(i).setSelected(false);
        }
        fireValueChanged();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#getMinSelectionIndex()
     */
    public int getMinSelectionIndex() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getRow(i).isSelected()) {
                return i;
            }
        }
        return -1;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#getMaxSelectionIndex()
     */
    public int getMaxSelectionIndex() {
        for (int i = tableModel.getRowCount() - 1; i >= 0; i--) {
            if (tableModel.getRow(i).isSelected())
                return i;
        }
        return -1;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#isSelectedIndex(int)
     */
    public boolean isSelectedIndex(int index) {
        return tableModel.getRow(index).isSelected();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#getAnchorSelectionIndex()
     */
    public int getAnchorSelectionIndex() {
        return tableModel.indexOf(anchorValue);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#setAnchorSelectionIndex(int)
     */
    public void setAnchorSelectionIndex(int index) {
        anchorValue = tableModel.getRow(index);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#getLeadSelectionIndex()
     */
    public int getLeadSelectionIndex() {
        return leadSelectionIndex;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#setLeadSelectionIndex(int)
     */
    public void setLeadSelectionIndex(int index) {
        leadSelectionIndex = index;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#clearSelection()
     */
    public void clearSelection() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.getRow(i).setSelected(false);
        }
        fireValueChanged();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#isSelectionEmpty()
     */
    public boolean isSelectionEmpty() {
        return (getMinSelectionIndex() == -1);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#insertIndexInterval(int, int,
     *      boolean)
     */
    public void insertIndexInterval(int index, int length, boolean before) {
        fireValueChanged();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#removeIndexInterval(int, int)
     */
    public void removeIndexInterval(int index0, int index1) {
        fireValueChanged();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#setValueIsAdjusting(boolean)
     */
    public void setValueIsAdjusting(boolean valueIsAdjusting) {
        this.valueIsAdjusting = valueIsAdjusting;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#getValueIsAdjusting()
     */
    public boolean getValueIsAdjusting() {
        return valueIsAdjusting;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#setSelectionMode(int)
     */
    public void setSelectionMode(int selectionMode) {
        this.selectionMode = selectionMode;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#getSelectionMode()
     */
    public int getSelectionMode() {
        return selectionMode;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#addListSelectionListener(javax.swing.event.ListSelectionListener)
     */
    public void addListSelectionListener(ListSelectionListener listener) {
        listenerList.add(ListSelectionListener.class, listener);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.ListSelectionModel#removeListSelectionListener(javax.swing.event.ListSelectionListener)
     */
    public void removeListSelectionListener(ListSelectionListener listener) {
        listenerList.remove(ListSelectionListener.class, listener);
    }

    /**
     * @param firstIndex
     *            the first index in the interval
     * @param lastIndex
     *            the last index in the interval
     * @param isAdjusting
     *            true if this is the final change in a series of adjustments
     * @see EventListenerList
     */
    protected void fireValueChanged(int firstIndex, int lastIndex, boolean isAdjusting) {
        Object[] listeners = listenerList.getListenerList();
        ListSelectionEvent e = null;

        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ListSelectionListener.class) {
                if (e == null) {
                    e = new ListSelectionEvent(this, firstIndex, lastIndex, isAdjusting);
                }
                ((ListSelectionListener) listeners[i + 1]).valueChanged(e);
            }
        }
    }

    private void fireValueChanged() {
        fireValueChanged(0, tableModel.getRowCount() - 1, valueIsAdjusting);
    }
}

class MCHeaderCellRenderer extends JPanel implements TableCellRenderer {
    private final TableCellRenderer renderer;

    private final MCListTableModel model;

    public MCHeaderCellRenderer(TableCellRenderer renderer, MCListTableModel model) {
        this.renderer = renderer;
        this.model = model;
        setOpaque(false);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.swing.table.TableCellRenderer#getTableCellRendererComponent(javax.swing.JTable,
     *      java.lang.Object, boolean, boolean, int, int)
     */
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {
        Component widget = renderer.getTableCellRendererComponent(table, value, isSelected,
                hasFocus, row, column);
        if (model.getSortByIndex() == column) {
            setOpaque(false);
            setLayout(new BorderLayout());
            add(widget, BorderLayout.CENTER);
            return this;
        }
        return widget;
    }

    public void paintChildren(Graphics g) {
        super.paintChildren(g);
        g.setColor(new Color(0, 0, 0, 25));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

}