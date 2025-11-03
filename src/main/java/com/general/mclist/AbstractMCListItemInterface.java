package com.general.mclist;

public abstract class AbstractMCListItemInterface<T> implements MCListItemInterface<T> {
    private boolean selected = false;

    public final void setSelected(boolean b) {
        selected = b;
    }

    public final boolean isSelected() {
        return selected;
    }

    protected final void toggleSelection() {
        if (selected)
            selected = false;
        else
            selected = true;
    }
}
