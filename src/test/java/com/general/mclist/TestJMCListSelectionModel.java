package com.general.mclist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TestJMCListSelectionModel {
    @Test
    void unselectedPopulatedListReportsZeroSelectedItems() {
        JMCList<String> list = new JMCList<>(1, true);
        list.addItem(new GenericMCListItem<>(
                new Sortable<?>[] { new SortableString("Server") }, "server"));

        assertFalse(list.getSelectionModel().isSelectedIndex(-1));
        assertFalse(list.getSelectionModel().isSelectedIndex(list.getRowCount()));
        assertEquals(0, list.getSelectionModel().getSelectedItemsCount());
    }
}
