package com.myster.type.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import javax.swing.SwingUtilities;

import com.myster.access.AccessList;
import com.myster.access.AccessListKeyUtils;
import com.myster.access.AccessListManager;
import com.myster.access.Policy;
import com.myster.application.MysterGlobals;
import com.myster.filemanager.MetadataType;
import com.myster.filemanager.MetadataTypeRegistry;
import com.myster.type.CustomTypeDefinition;
import com.myster.type.MetadataTypeId;
import com.myster.type.TypeDescriptionList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class TestTypeEditorPanelMetadataType {
    private static KeyPair rsaKeyPair;
    private static KeyPair adminKeyPair;

    @TempDir
    File tempDir;

    @BeforeAll
    static void generateKey() throws Exception {
        rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        adminKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void createChoicesComeFromRegistryAndDefaultToGeneric() {
        TypeEditorPanel panel = new TypeEditorPanel(mock(TypeDescriptionList.class),
                new AccessListManager(), null, Optional.empty(), Optional.empty(), imageRegistry(),
                () -> {}, () -> {});

        JComboBox<MetadataTypeId> selector = metadataSelector(panel);
        assertEquals(2, selector.getItemCount());
        assertEquals(MetadataTypeId.GENERIC, selector.getItemAt(0));
        assertEquals(MetadataTypeId.IMAGE, selector.getItemAt(1));
        assertEquals(MetadataTypeId.GENERIC, selector.getSelectedItem());
        assertEquals("Image", renderedText(selector, MetadataTypeId.IMAGE));
    }

    @Test
    void unknownCurrentValueGetsFriendlyReadOnlyChoice() {
        MetadataTypeId future = MetadataTypeId.fromString("spatial_audio");
        CustomTypeDefinition existing = new CustomTypeDefinition(rsaKeyPair.getPublic(),
                "Future", "Future metadata", new String[] {"spa"}, false, true, future);

        try (MockedStatic<MysterGlobals> globals = mockStatic(MysterGlobals.class)) {
            globals.when(MysterGlobals::getPrivateDataPath).thenReturn(tempDir);
            TypeEditorPanel panel = new TypeEditorPanel(mock(TypeDescriptionList.class),
                    new AccessListManager(), existing, Optional.empty(), Optional.empty(),
                    imageRegistry(), () -> {}, () -> {});

            JComboBox<MetadataTypeId> selector = metadataSelector(panel);
            assertEquals(3, selector.getItemCount());
            assertEquals(future, selector.getSelectedItem());
            assertEquals("Unknown metadata type — Spatial Audio", renderedText(selector, future));
            assertFalse(selector.isEnabled());
        }
    }

    @Test
    void changingImageToGenericAppendsExplicitClearOperation() throws Exception {
        try (MockedStatic<MysterGlobals> globals = mockStatic(MysterGlobals.class)) {
            globals.when(MysterGlobals::getPrivateDataPath).thenReturn(tempDir);
            globals.when(MysterGlobals::getAccessListPath).thenReturn(tempDir);

            AccessListManager manager = new AccessListManager();
            AccessList accessList = accessList(MetadataTypeId.IMAGE);
            manager.saveAccessList(accessList);
            AccessListKeyUtils.saveKeyPair(adminKeyPair, accessList.getMysterType());
            TypeDescriptionList typeList = mock(TypeDescriptionList.class);
            CustomTypeDefinition existing = definition(MetadataTypeId.IMAGE);

            TypeEditorPanel panel = new TypeEditorPanel(typeList, manager, existing,
                    Optional.empty(), Optional.empty(), imageRegistry(), () -> {}, () -> {});
            metadataSelector(panel).setSelectedItem(MetadataTypeId.GENERIC);
            SwingUtilities.invokeAndWait(() -> saveButton(panel).doClick());

            AccessList saved = manager.loadAccessList(accessList.getMysterType()).orElseThrow();
            assertEquals(1, saved.getHeight());
            assertEquals(MetadataTypeId.GENERIC, saved.getState().getMetadataTypeId());
            verify(typeList).updateCustomType(
                    org.mockito.ArgumentMatchers.eq(accessList.getMysterType()),
                    org.mockito.ArgumentMatchers.argThat(definition ->
                            MetadataTypeId.GENERIC.equals(definition.getMetadataTypeId())));
        }
    }

    @Test
    void savingUnchangedGenericAppendsNoOperation() throws Exception {
        try (MockedStatic<MysterGlobals> globals = mockStatic(MysterGlobals.class)) {
            globals.when(MysterGlobals::getPrivateDataPath).thenReturn(tempDir);
            globals.when(MysterGlobals::getAccessListPath).thenReturn(tempDir);

            AccessListManager manager = new AccessListManager();
            AccessList accessList = accessList(MetadataTypeId.GENERIC);
            manager.saveAccessList(accessList);
            AccessListKeyUtils.saveKeyPair(adminKeyPair, accessList.getMysterType());
            TypeDescriptionList typeList = mock(TypeDescriptionList.class);

            TypeEditorPanel panel = new TypeEditorPanel(typeList, manager,
                    definition(MetadataTypeId.GENERIC), Optional.empty(), Optional.empty(),
                    imageRegistry(), () -> {}, () -> {});
            SwingUtilities.invokeAndWait(() -> saveButton(panel).doClick());

            AccessList saved = manager.loadAccessList(accessList.getMysterType()).orElseThrow();
            assertEquals(0, saved.getHeight());
            assertEquals(MetadataTypeId.GENERIC, saved.getState().getMetadataTypeId());
        }
    }

    private static AccessList accessList(MetadataTypeId metadataTypeId) throws Exception {
        return AccessList.createGenesis(rsaKeyPair.getPublic(), adminKeyPair, List.of(), List.of(),
                Policy.defaultPermissive(), "Future", "Future metadata",
                new String[] {"spa"}, false, metadataTypeId);
    }

    private static CustomTypeDefinition definition(MetadataTypeId metadataTypeId) {
        return new CustomTypeDefinition(rsaKeyPair.getPublic(), "Future", "Future metadata",
                new String[] {"spa"}, false, true, metadataTypeId);
    }

    private static MetadataTypeRegistry imageRegistry() {
        return new MetadataTypeRegistry() {
            @Override
            public MetadataType get(MetadataTypeId metadataTypeId) {
                return MetadataTypeId.IMAGE.equals(metadataTypeId)
                        ? MetadataType.IMAGE
                        : MetadataType.GENERIC;
            }

            @Override
            public MetadataType generic() {
                return MetadataType.GENERIC;
            }

            @Override
            public Collection<MetadataType> supportedTypes() {
                return List.of(MetadataType.GENERIC, MetadataType.IMAGE);
            }
        };
    }

    private static JButton saveButton(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && "Save".equals(button.getText())) {
                return button;
            }
            if (component instanceof Container child) {
                JButton result = findSaveButton(child);
                if (result != null) {
                    return result;
                }
            }
        }
        throw new AssertionError("Save button not found");
    }

    private static JButton findSaveButton(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && "Save".equals(button.getText())) {
                return button;
            }
            if (component instanceof Container child) {
                JButton result = findSaveButton(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<MetadataTypeId> metadataSelector(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JComboBox<?> comboBox) {
                return (JComboBox<MetadataTypeId>) comboBox;
            }
            if (component instanceof Container child) {
                JComboBox<MetadataTypeId> result = findMetadataSelector(child);
                if (result != null) {
                    return result;
                }
            }
        }
        throw new AssertionError("Metadata profile selector not found");
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<MetadataTypeId> findMetadataSelector(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JComboBox<?> comboBox) {
                return (JComboBox<MetadataTypeId>) comboBox;
            }
            if (component instanceof Container child) {
                JComboBox<MetadataTypeId> result = findMetadataSelector(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String renderedText(JComboBox<MetadataTypeId> selector, MetadataTypeId id) {
        ListCellRenderer renderer = selector.getRenderer();
        Component rendered = renderer.getListCellRendererComponent(
                new JList<>(), id, 0, false, false);
        assertTrue(rendered instanceof JLabel);
        return ((JLabel) rendered).getText();
    }
}
