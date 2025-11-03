package com.general.mclist;

public interface MCListItemInterface<T> extends ColumnSortable<T> {
    public void setSelected(boolean b);
    public boolean isSelected();
}
