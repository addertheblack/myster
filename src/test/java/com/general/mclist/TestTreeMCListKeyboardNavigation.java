package com.general.mclist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.general.mclist.TreeMCListTableModel.TreeMCListItem;
import com.general.mclist.TreeMCListTableModel.TreePath;
import com.general.mclist.TreeMCListTableModel.TreePathString;

class TestTreeMCListKeyboardNavigation {
    @Test
    void rightThenLeftPreservesNoncontiguousSelectionThroughRowShifts() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture f = new Fixture();
            var first = f.folder(f.root, "First", "First");
            var middle = f.folder(f.root, "Middle", "Middle");
            var last = f.folder(f.root, "Last", "Last");
            var firstFile = f.file(first.getMyPathOrFail(), "first.txt");
            var lastFile = f.file(last.getMyPathOrFail(), "last.txt");
            var nested = f.folder(first.getMyPathOrFail(), "Nested", "First", "Nested");
            var nestedFile = f.file(nested.getMyPathOrFail(), "nested.txt");
            f.select(first, last);
            var updates = f.countUpdates();

            assertTrue(f.press(KeyEvent.VK_RIGHT).isConsumed());

            assertTrue(first.isOpen());
            assertTrue(last.isOpen());
            assertFalse(middle.isOpen());
            assertFalse(nested.isOpen());
            assertTrue(f.model.indexOf(firstFile) >= 0);
            assertTrue(f.model.indexOf(lastFile) >= 0);
            assertEquals(-1, f.model.indexOf(nestedFile));
            assertFalse(firstFile.isSelected());
            assertFalse(nested.isSelected());
            assertFalse(lastFile.isSelected());
            assertEquals(List.of(first, last), f.selectedItems());
            assertEquals(1, updates.get());

            assertTrue(f.press(KeyEvent.VK_LEFT).isConsumed());

            assertFalse(first.isOpen());
            assertFalse(last.isOpen());
            assertEquals(-1, f.model.indexOf(firstFile));
            assertEquals(-1, f.model.indexOf(lastFile));
            assertEquals(List.of(first, last), f.selectedItems());
            assertEquals(2, updates.get());
        });
    }

    @Test
    void leftClosesSelectedSiblingsAndPreservesUnselectedOpenFolder() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture f = new Fixture();
            var first = f.folder(f.root, "First", "First");
            var middle = f.folder(f.root, "Middle", "Middle");
            var last = f.folder(f.root, "Last", "Last");
            var firstFile = f.file(first.getMyPathOrFail(), "first.txt");
            var middleFile = f.file(middle.getMyPathOrFail(), "middle.txt");
            var lastFile = f.file(last.getMyPathOrFail(), "last.txt");
            first.setOpen(true);
            middle.setOpen(true);
            last.setOpen(true);
            f.model.resortAndRebuild();
            f.select(first, last);
            var updates = f.countUpdates();

            assertTrue(f.press(KeyEvent.VK_LEFT).isConsumed());

            assertFalse(first.isOpen());
            assertFalse(last.isOpen());
            assertTrue(middle.isOpen());
            assertEquals(-1, f.model.indexOf(firstFile));
            assertEquals(-1, f.model.indexOf(lastFile));
            assertTrue(f.model.indexOf(middleFile) >= 0);
            assertEquals(List.of(first, last), f.selectedItems());
            assertEquals(1, updates.get());
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void mixedStatesConvergeAndRepeatedArrowDoesNotRebuild(boolean open) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture f = new Fixture();
            var first = f.folder(f.root, "First", "First");
            var empty = f.folder(f.root, "Empty", "Empty");
            f.file(first.getMyPathOrFail(), "first.txt");
            first.setOpen(true);
            f.model.resortAndRebuild();
            f.select(first, empty);
            var updates = f.countUpdates();
            int key = open ? KeyEvent.VK_RIGHT : KeyEvent.VK_LEFT;

            assertTrue(f.press(key).isConsumed());

            assertEquals(open, first.isOpen());
            assertEquals(open, empty.isOpen());
            assertEquals(List.of(first, empty), f.selectedItems());
            assertEquals(1, updates.get());

            assertTrue(f.press(key).isConsumed());
            assertEquals(1, updates.get());
            assertEquals(List.of(first, empty), f.selectedItems());
        });
    }

    @Test
    void selectedLeafBeforeFoldersDoesNotBlockEitherArrow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture f = new Fixture();
            var leaf = f.file(f.root, "leaf.txt");
            var first = f.folder(f.root, "First", "First");
            var last = f.folder(f.root, "Last", "Last");
            f.file(first.getMyPathOrFail(), "first.txt");
            f.select(leaf, first, last);

            assertTrue(f.press(KeyEvent.VK_RIGHT).isConsumed());
            assertTrue(first.isOpen());
            assertTrue(last.isOpen());
            assertFalse(leaf.isOpen());
            assertEquals(List.of(leaf, first, last), f.selectedItems());

            assertTrue(f.press(KeyEvent.VK_LEFT).isConsumed());
            assertFalse(first.isOpen());
            assertFalse(last.isOpen());
            assertEquals(List.of(leaf, first, last), f.selectedItems());
        });
    }

    @Test
    void leftClosesSelectedParentAndChildBeforeHiddenDescendantsAreDeselected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture f = new Fixture();
            var parent = f.folder(f.root, "Parent", "Parent");
            var child = f.folder(parent.getMyPathOrFail(), "Child", "Parent", "Child");
            var other = f.folder(parent.getMyPathOrFail(), "Other", "Parent", "Other");
            var leaf = f.file(child.getMyPathOrFail(), "leaf.txt");
            parent.setOpen(true);
            child.setOpen(true);
            other.setOpen(true);
            f.model.resortAndRebuild();
            f.select(parent, child, leaf);
            var updates = f.countUpdates();

            assertTrue(f.press(KeyEvent.VK_LEFT).isConsumed());

            assertFalse(parent.isOpen());
            assertFalse(child.isOpen());
            assertTrue(other.isOpen());
            assertFalse(child.isSelected());
            assertFalse(leaf.isSelected());
            assertEquals(List.of(parent), f.selectedItems());
            assertEquals(1, f.list.getRowCount());
            assertEquals(1, updates.get());

            assertTrue(f.press(KeyEvent.VK_RIGHT).isConsumed());
            assertFalse(child.isOpen());
            assertTrue(other.isOpen());
            assertTrue(f.model.indexOf(child) >= 0);
            assertEquals(-1, f.model.indexOf(leaf));
            assertEquals(List.of(parent), f.selectedItems());
        });
    }

    @ParameterizedTest
    @ValueSource(ints = { KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT })
    void noSelectionAndLeafOnlySelectionLeaveArrowUnconsumed(int key) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture f = new Fixture();
            var leaf = f.file(f.root, "leaf.txt");
            var folder = f.folder(f.root, "Folder", "Folder");
            var updates = f.countUpdates();

            assertFalse(f.press(key).isConsumed());
            f.select(leaf);
            assertFalse(f.press(key).isConsumed());

            assertFalse(folder.isOpen());
            assertFalse(leaf.isOpen());
            assertEquals(List.of(leaf), f.selectedItems());
            assertEquals(0, updates.get());
        });
    }

    @Test
    void singleSelectionStillOpensAndClosesAndOtherKeysPassThrough() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture f = new Fixture();
            f.list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            var folder = f.folder(f.root, "Folder", "Folder");
            f.select(folder);
            var updates = f.countUpdates();

            assertFalse(f.press(KeyEvent.VK_UP).isConsumed());
            assertFalse(folder.isOpen());
            assertEquals(0, updates.get());
            assertTrue(f.press(KeyEvent.VK_RIGHT).isConsumed());
            assertTrue(folder.isOpen());
            assertTrue(f.press(KeyEvent.VK_LEFT).isConsumed());
            assertFalse(folder.isOpen());
            assertEquals(List.of(folder), f.selectedItems());
            assertEquals(2, updates.get());
        });
    }

    private static class Fixture {
        private final TreePath root = new TreePathString(new String[0]);
        private final JMCList<String> list = TreeMCList.create(new String[] { "Name" }, root);
        private final TreeMCListTableModel<String> model;

        @SuppressWarnings("unchecked")
        Fixture() {
            list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            model = (TreeMCListTableModel<String>) list.getModel();
        }

        TreeMCListItem<String> folder(TreePath parent, String name, String... path) {
            return add(parent, name, Optional.of(new TreePathString(path)));
        }

        TreeMCListItem<String> file(TreePath parent, String name) {
            return add(parent, name, Optional.empty());
        }

        private TreeMCListItem<String> add(TreePath parent, String name, Optional<TreePath> path) {
            var delegate = new ColumnSortable<String>() {
                @Override
                public Sortable<?> getValueOfColumn(int i) {
                    return new SortableString(name);
                }

                @Override
                public String getObject() {
                    return name;
                }
            };
            var item = new TreeMCListItem<>(parent, delegate, path);
            model.addRow(item);
            return item;
        }

        void select(TreeMCListItem<?>... items) {
            for (var item : items) {
                int index = list.findRowIndexOfItem(item);
                assertTrue(index >= 0, "Selected fixture item must be visible");
                list.addRowSelectionInterval(index, index);
            }
        }

        List<MCListItemInterface<String>> selectedItems() {
            return Arrays.stream(list.getSelectedIndexes()).mapToObj(model::getRow).toList();
        }

        AtomicInteger countUpdates() {
            var updates = new AtomicInteger();
            model.addTableModelListener(_ -> updates.incrementAndGet());
            return updates;
        }

        KeyEvent press(int key) {
            var event = new KeyEvent(list, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED);
            for (var listener : list.getKeyListeners()) {
                listener.keyPressed(event);
                if (event.isConsumed()) {
                    break;
                }
            }
            return event;
        }
    }
}
