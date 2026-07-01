package com.myster.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.myster.access.AccessList;
import com.myster.type.CustomTypeDefinition;
import com.myster.type.MysterType;
import com.myster.type.StandardTypes;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionEvent;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeListener;

@Tag("myster")
class TestTypeChoiceThreeDns {
    @Test
    void threeDnsIsSelectableExtraWithoutMysterType() {
        TypeChoice choice = new TypeChoice(new TestTypeDescriptionList(), true);

        choice.selectThreeDns();

        assertTrue(choice.isThreeDns());
        assertFalse(choice.isLan());
        assertFalse(choice.isBookmark());
        assertTrue(choice.getType().isEmpty());
        choice.dispose();
    }

    @Test
    void rebuildPreservesExtraSelections() throws Exception {
        TestTypeDescriptionList list = new TestTypeDescriptionList();
        TypeChoice choice = new TypeChoice(list, true);

        choice.selectThreeDns();
        list.fireTypeEnabled();
        waitForEventQueue();
        assertTrue(choice.isThreeDns());

        choice.selectLan();
        list.fireTypeEnabled();
        waitForEventQueue();
        assertTrue(choice.isLan());

        choice.selectBookmarks();
        list.fireTypeEnabled();
        waitForEventQueue();
        assertTrue(choice.isBookmark());
        choice.dispose();
    }

    private static void waitForEventQueue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {});
    }

    private static class TestTypeDescriptionList implements TypeDescriptionList {
        private final TypeDescription type =
                new TypeDescription(new MysterType(new byte[16]),
                                    "TEST",
                                    "Test Type",
                                    new String[] { "test" },
                                    false,
                                    true);
        private final List<TypeListener> listeners = new ArrayList<>();

        void fireTypeEnabled() {
            TypeDescriptionEvent event = new TypeDescriptionEvent(this, type.getType());
            listeners.forEach(listener -> listener.typeEnabled(event));
        }

        @Override
        public MysterType getType(StandardTypes t) {
            return type.getType();
        }

        @Override
        public Optional<TypeDescription> get(MysterType type) {
            return this.type.getType().equals(type) ? Optional.of(this.type) : Optional.empty();
        }

        @Override
        public TypeDescription[] getAllTypes() {
            return new TypeDescription[] { type };
        }

        @Override
        public TypeDescription[] getEnabledTypes() {
            return new TypeDescription[] { type };
        }

        @Override
        public boolean isTypeEnabled(MysterType type) {
            return this.type.getType().equals(type);
        }

        @Override
        public boolean isTypeEnabledInPrefs(MysterType type) {
            return isTypeEnabled(type);
        }

        @Override
        public void addTypeListener(TypeListener l) {
            listeners.add(l);
        }

        @Override
        public void removeTypeListener(TypeListener l) {
            listeners.remove(l);
        }

        @Override
        public void setEnabledType(MysterType type, boolean enable) {}

        @Override
        public void addCustomType(CustomTypeDefinition def) {}

        @Override
        public void removeCustomType(MysterType type) {}

        @Override
        public void updateCustomType(MysterType type, CustomTypeDefinition def) {}

        @Override
        public Optional<CustomTypeDefinition> getCustomTypeDefinition(MysterType type) {
            return Optional.empty();
        }

        @Override
        public void importType(AccessList accessList) throws IOException {}
    }
}
