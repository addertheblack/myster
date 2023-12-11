/*
 * MCList.java
 * 
 * Title: Multi Column List Package Author: Andrew Trumper Description: A
 * MultiColumn List Package
 * 
 * Copyright Andrew Trumper 2001
 */

package com.general.mclist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MCListList {
    public static final boolean ASENDING = true;
    public static final boolean DESCENDING = false;
    
    private final List<MCListItemInterface> list;

    private int sortby = 0;
    private boolean lessthan = true;
    private boolean isSorted = true;

    MCListList() {
        list = new ArrayList<>();
    }

    boolean isSorted() {
        return isSorted;
    }

    /**
     * If isSorted is true the List will try to always remain properly sorted.
     * If it is false, the list will only sort if explicitly told to.
     */
    void setSorted(boolean isSorted) {
        this.isSorted = isSorted;
    }

    synchronized void sort() {
        if (sortby == -1)
            return;

        Collections.sort(list,
                new Comparator() {
                    public int compare(Object a, Object b) {
                        Sortable sa = ((MCListItemInterface) a)
                                .getValueOfColumn(sortby);
                        Sortable sb = ((MCListItemInterface) b)
                                .getValueOfColumn(sortby);

                        if (sa.equals(sb))
                            return 0;

                        int cmp = (sa.isLessThan(sb) ? -1 : 1);
                        return (lessthan ? cmp : -cmp);
                    }
                });
    }

    synchronized boolean getSortOrder() {
        return lessthan;
    }

    synchronized boolean reverseSortOrder() {
        return setSortOrder(!lessthan);
    }

    synchronized boolean setSortOrder(boolean b) {
        if (lessthan != b) {
            lessthan = !lessthan;
            sort();
        }

        return lessthan;
    }

    synchronized void addElement(MCListItemInterface[] o) {
        for (int i = 0; i < o.length; i++)
            list.add(o[i]);
        if (isSorted)
            sort();
    }

    synchronized void removeElement(int index) {
        list.remove(index);
    }

    synchronized void removeElement(Object o) {
        list.remove(o);
    }

    synchronized MCListItemInterface getElement(int index) {
        return list.get(index);
    }

    synchronized void setSortBy(int i) {
        sortby = i;
        sort();
    }

    int size() {
        return list.size();
    }

    synchronized void removeAllElements() {
        list.clear();
    }

    synchronized void removeIndexes(int[] indexes) {
        MCListItemInterface[] objectsToRemove = new MCListItemInterface[indexes.length];

        for (int i = 0; i < indexes.length; i++) {
            objectsToRemove[i] = getElement(indexes[i]);
        }

        removeElements(objectsToRemove);
    }

    synchronized void removeElements(MCListItemInterface[] objectsToRemove) {
        for (int i = 0; i < objectsToRemove.length; i++) {
            removeElement(objectsToRemove[i]);
        }
    }

    synchronized boolean isAnythingSelected() {
        for (int i = 0; i < size(); i++) {
            if (getElement(i).isSelected())
                return true;
        }

        return false;
    }

    synchronized int[] getSelectedIndexes() {
        int counter = 0;
        for (int i = 0; i < size(); i++) {
            if (getElement(i).isSelected())
                counter++;
        }

        int[] temp = new int[counter];

        int j = 0;
        for (int i = 0; i < size(); i++) {
            if (getElement(i).isSelected()) {
                temp[j] = i;
                j++;
            }
        }
        return temp;
    }

    /**
     * Returns the index of the first selected item form the top of the list.
     * 
     * @return the first selected item or -1 if there is no item currently selected.
     */
    synchronized int getSelectedIndex() {
        int workingindex = -1;
        for (int i = 0; i < size(); i++) {
            if (getElement(i).isSelected()) {
                if (workingindex == -1)
                    workingindex = i;
                else
                    return -1;
            }
        }
        return workingindex;
    }
}