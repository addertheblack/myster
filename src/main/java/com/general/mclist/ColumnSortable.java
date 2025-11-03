package com.general.mclist;

public interface ColumnSortable<T> {
    public Sortable<?> getValueOfColumn(int i);
    public T getObject();
}
