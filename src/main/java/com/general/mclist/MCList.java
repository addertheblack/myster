package com.general.mclist;

import java.awt.Container;
import java.awt.Font;

public interface MCList<E> {
    void setNumberOfColumns(int c);
    void setColumnName(int columnnumber, String name);
    void setColumnWidth(int index, int size);
    void sortBy(int column);
    Container getPane();
    void addItem(MCListItemInterface<E> m);
    void addItem(MCListItemInterface<E>[] m);
    void setSorted(boolean isSorted);
    boolean isSorted() ;
    boolean isSelected(int i);
    void select(int i);
    void unselect(int i);
    void toggle(int i);
    boolean isAnythingSelected();
    int[] getSelectedIndexes();
    int getSelectedIndex();
    void setSingleSelect(boolean b);
    boolean isSingleSelect();
    void addMCListEventListener(MCListEventListener e);
    void clearAll();
    void removeItem(int i);
    void removeItem(int[] indexes);
    E getItem(int i);
    MCListItemInterface<E> getMCListItem(int i);
    void reverseSortOrder();
    int length();
    Font getFont();
    void repaint();
}
