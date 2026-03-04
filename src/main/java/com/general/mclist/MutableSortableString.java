package com.general.mclist;

/**
 * A {@link SortableString} whose display value can be updated in-place after the row has been
 * inserted into an {@link MCList}.
 *
 * <p>Call {@link #setValue(String)} on the EDT, then repaint the owning list:
 * {@code mcList.repaint()}. No re-insertion or re-sort is required — this pattern is only safe
 * when the list's sort order is disabled ({@code sortBy(-1)}), which is the case for the type
 * list in {@code ClientWindow}.
 */
public class MutableSortableString extends SortableString {

    public MutableSortableString(String s) {
        super(s);
    }

    /**
     * Updates the display value in-place. Must be called on the EDT.
     *
     * @param s the new display string
     */
    public void setValue(String s) {
        this.string = s;
    }
}

