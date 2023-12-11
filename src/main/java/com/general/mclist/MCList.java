package com.general.mclist;

import java.awt.Container;
import java.awt.Font;

public interface MCList {
    public void setNumberOfColumns(int c);
    public void setColumnName(int columnnumber, String name);
    public void setColumnWidth(int index, int size);
    public void sortBy(int column);
    public Container getPane();
    public void addItem(MCListItemInterface m);
    public void addItem(MCListItemInterface[] m);
    public void setSorted(boolean isSorted);
    public boolean isSorted() ;
    public boolean isSelected(int i);
    public void select(int i);
    public void unselect(int i);
    public void clearAllSelected();
    public void toggle(int i);
    public boolean isAnythingSelected();
    public int[] getSelectedIndexes();
    public int getSelectedIndex();
    public void setSingleSelect(boolean b);
    public boolean isSingleSelect();
    public void addMCListEventListener(MCListEventListener e);
    public void clearAll();
    public void removeItem(int i);
    public void removeItem(MCListItemInterface o);
    public void removeItem(int[] indexes);
    public Object getItem(int i);
    public MCListItemInterface getMCListItem(int i);
    public void reverseSortOrder();
    public int length();
    public Font getFont();
    public void repaint();
}
