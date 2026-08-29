package com.myster.tracker.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.Test;

class TestServerPickerKeyBindings {
    @Test
    void enterOnFocusedTableInvokesConfirmationAction() {
        JTable table = new JTable();
        AtomicInteger confirmations = new AtomicInteger();
        ServerPickerDialog.bindEnterAction(table, confirmations::incrementAndGet);

        Object actionKey = table.getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        Action action = table.getActionMap().get(actionKey);

        assertNotNull(action);
        action.actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, "enter"));
        assertEquals(1, confirmations.get());
    }

    @Test
    void arrowsOnFocusedSearchFieldInvokeSelectionActions() {
        JTextField searchField = new JTextField();
        AtomicInteger movement = new AtomicInteger();
        ServerPickerDialog.bindVerticalSelectionActions(
                searchField, () -> movement.decrementAndGet(), () -> movement.incrementAndGet());

        performFocusedKeyAction(searchField, KeyEvent.VK_DOWN);
        assertEquals(1, movement.get());
        performFocusedKeyAction(searchField, KeyEvent.VK_UP);
        assertEquals(0, movement.get());
    }

    @Test
    void selectionMovementStopsAtFirstAndLastRows() {
        assertEquals(-1, ServerPickerDialog.nextSelectionIndex(-1, 0, 1));
        assertEquals(0, ServerPickerDialog.nextSelectionIndex(-1, 3, 1));
        assertEquals(0, ServerPickerDialog.nextSelectionIndex(0, 3, -1));
        assertEquals(1, ServerPickerDialog.nextSelectionIndex(0, 3, 1));
        assertEquals(1, ServerPickerDialog.nextSelectionIndex(2, 3, -1));
        assertEquals(2, ServerPickerDialog.nextSelectionIndex(2, 3, 1));
    }

    private static void performFocusedKeyAction(JComponent component, int keyCode) {
        Object actionKey = component.getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(keyCode, 0));
        Action action = component.getActionMap().get(actionKey);
        assertNotNull(action);
        action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, "key"));
    }
}
