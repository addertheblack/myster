package com.myster.type.ui;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.table.TableCellRenderer;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.general.mclist.GenericMCListItem;
import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventListener;
import com.general.mclist.MCListFactory;
import com.general.mclist.MCListTableModel;
import com.general.mclist.Sortable;
import com.general.mclist.SortableBoolean;
import com.general.mclist.SortableString;
import com.general.util.GridBagBuilder;
import com.general.util.IconLoader;
import com.general.util.MessagePanel;
import com.myster.pref.ui.PreferencesPanel;
import com.myster.type.CustomTypeDefinition;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeSource;

/**
 * Preferences panel for managing both default and custom MysterTypes.
 *
 * <p>Features:
 * <ul>
 *   <li>Unified view of default (built-in) and custom types</li>
 *   <li>Enable/disable any type via double-click</li>
 *   <li>Add new custom types (saved immediately)</li>
 *   <li>Edit custom types (changes saved on panel save)</li>
 *   <li>Delete custom types (applied on panel save)</li>
 *   <li>Default types cannot be edited or deleted</li>
 * </ul>
 *
 * <p>Replaces the old TypeManagerPreferencesGUI with enhanced functionality.
 */
public class TypeManagerPreferences extends PreferencesPanel {
    private final TypeDescriptionList tdList;
    private MCList<MysterType> mcList;
    private Action addAction;
    private Action editAction;
    private Action deleteAction;

    // CardLayout for switching between list view and editor view
    private final CardLayout cardLayout;
    private final JPanel containerPanel;
    private static final String LIST_VIEW = "list";
    private static final String EDITOR_VIEW = "editor";

    private TypeEditorPanel editorPanel;
    private MysterType editingType = null; // Non-null if we're editing an existing type

    // Track types pending deletion (applied on save)
    private final List<MysterType> pendingDeletions = new ArrayList<>();

    /**
     * Creates a new type manager preferences panel.
     *
     * @param tdList the type description list to manage
     */
    public TypeManagerPreferences(TypeDescriptionList tdList) {
        this.tdList = tdList;
        setLayout(new GridBagLayout());

        cardLayout = new CardLayout();
        containerPanel = new JPanel(cardLayout);

        GridBagBuilder gbc = new GridBagBuilder().withInsets(new Insets(5, 5, 5, 5));

        // Create the list view panel
        JPanel listViewPanel = createListViewPanel();
        containerPanel.add(listViewPanel, LIST_VIEW);

        // Add container to main panel
        add(containerPanel, gbc.withGridLoc(0, 0).withWeight(1.0, 1.0).withFill(GridBagConstraints.BOTH));

        // Initial load
        reset();
    }

    private JPanel createListViewPanel() {
        JPanel listPanel = new JPanel(new GridBagLayout());
        GridBagBuilder gbc = new GridBagBuilder().withInsets(new Insets(5, 5, 5, 5));

        // Help text at top
        var message = MessagePanel.createNew(
            "Myster uses 'types' to organize files into virtual overlay networks. " +
            "Each enabled type adds some CPU and bandwidth overhead, so only enable types you use. " +
            "You can create custom types for your own private networks. " +
            "Use the checkbox to enable/disable types. Double-click custom types to edit them. " +
            "Changes take effect when you save this preferences panel.");
        listPanel.add(message, gbc.withGridLoc(0, 0).withSize(1, 1).withWeight(1.0, 0.0).withFill(GridBagConstraints.HORIZONTAL));

        // Create actions for toolbar
        addAction = new AbstractAction("Add Type") {
            @Override
            public void actionPerformed(ActionEvent e) {
                addType();
            }
        };
        addAction.putValue(Action.SHORT_DESCRIPTION, "Add a new custom type");

        editAction = new AbstractAction("Edit") {
            @Override
            public void actionPerformed(ActionEvent e) {
                editType();
            }
        };
        editAction.putValue(Action.SHORT_DESCRIPTION, "Edit selected custom type");
        editAction.setEnabled(false);

        deleteAction = new AbstractAction("Delete") {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteType();
            }
        };
        deleteAction.putValue(Action.SHORT_DESCRIPTION, "Delete selected custom type");
        deleteAction.setEnabled(false);

        // Try to load icons (16x16 for toolbar)
        try {
            FlatSVGIcon addIcon = IconLoader.loadSvg(TypeManagerPreferences.class, "add-icon", 16);
            FlatSVGIcon editIcon = IconLoader.loadSvg(TypeManagerPreferences.class, "edit-icon", 16);
            FlatSVGIcon deleteIcon = IconLoader.loadSvg(TypeManagerPreferences.class, "delete-icon", 16);

            // FlatLaf automatically substitutes #6E6E6E with theme colors - no ColorFilter needed

            addAction.putValue(Action.SMALL_ICON, addIcon);
            editAction.putValue(Action.SMALL_ICON, editIcon);
            deleteAction.putValue(Action.SMALL_ICON, deleteIcon);
        } catch (Exception ex) {
            // Icons not available yet, will use text-only buttons
        }

        // Create toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(addAction);
        toolbar.add(editAction);
        toolbar.add(deleteAction);

        listPanel.add(toolbar, gbc.withGridLoc(0, 1).withWeight(1.0, 0.0).withFill(GridBagConstraints.HORIZONTAL));

        // MCList with 4 columns: Name, Description, Enabled, Source
        mcList = MCListFactory.buildMCList(4, true, this);
        mcList.setColumnName(0, "Name");
        mcList.setColumnName(1, "Description");
        mcList.setColumnName(2, "Enabled");
        mcList.setColumnName(3, "Source");

        mcList.setColumnWidth(0, 200);
        mcList.setColumnWidth(1, 150);
        mcList.setColumnWidth(2, 80);
        mcList.setColumnWidth(3, 80);

        // Double-click to toggle enabled state
        mcList.addMCListEventListener(new MCListEventListener() {
            public void doubleClick(MCListEvent e) {
                toggleEnabled();
            }

            public void selectItem(MCListEvent e) {
                updateButtonStates();
            }

            public void unselectItem(MCListEvent e) {
                updateButtonStates();
            }
        });

        // Also add a mouse listener as a fallback to ensure buttons update on click
        JTable jTableMcList = (JTable) mcList;
//        jTableMcList.addMouseListener(new java.awt.event.MouseAdapter() {
//            @Override
//            public void mouseClicked(java.awt.event.MouseEvent e) {
//                // Small delay to ensure selection has been processed
//                javax.swing.SwingUtilities.invokeLater(() -> updateButtonStates());
//            }
//        });
        TableCellRenderer defaultRendererBoolean = jTableMcList.getDefaultRenderer(Boolean.class);

        var renderer = new TableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                var b = (value instanceof SortableBoolean) ? ((SortableBoolean)value).getValue() : false;
                return defaultRendererBoolean.getTableCellRendererComponent(table, b, isSelected, hasFocus, row, column);
            }
        };

        jTableMcList.getColumnModel().getColumn(2).setCellRenderer(renderer);

        // now set cell editor for boolean column
        // note that the column is not Boolean.class, but SortableBoolean.class
        JCheckBox editorCheckBox = new JCheckBox();
        editorCheckBox.setHorizontalAlignment(JCheckBox.CENTER);
        jTableMcList.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(editorCheckBox) {
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                    boolean isSelected, int row, int column) {

                // Extract the boolean value from SortableBoolean
                if (value instanceof SortableBoolean) {
                    Boolean boolValue = (Boolean) ((SortableBoolean) value).getValue();

                    // if we're editing the damn cell then it's selected
                    return super.getTableCellEditorComponent(table, boolValue, true, row, column);
                }
                return super.getTableCellEditorComponent(table, value, isSelected, row, column);
            }

            @Override
            public Object getCellEditorValue() {
                Object value = super.getCellEditorValue();
                if (value instanceof Boolean b) {
                    // Update the underlying item data when checkbox is toggled
                    int row = jTableMcList.getEditingRow();
                    if (row >= 0) {
                        TypeMCListItem item = (TypeMCListItem) mcList.getMCListItem(row);
                        item.setEnabled(b);
                    }
                    return new SortableBoolean(b);
                }
                return value;
            }
        });

        // now make the cells in that column editable
        MCListTableModel<MysterType> model = (MCListTableModel<MysterType>) jTableMcList.getModel();
        model.setColumnEditable(2, true);

        listPanel.add(mcList.getPane(), gbc.withGridLoc(0, 2).withWeight(1.0, 1.0).withFill(GridBagConstraints.BOTH));

        return listPanel;
    }

    private void updateButtonStates() {
        int selectedIndex = mcList.getSelectedIndex();
        boolean hasSelection = selectedIndex >= 0;

        if (hasSelection) {
            TypeMCListItem item = (TypeMCListItem) mcList.getMCListItem(selectedIndex);
            boolean isEditable = item.getTypeDescription().isEditable();
            editAction.setEnabled(isEditable);
            deleteAction.setEnabled(isEditable);
        } else {
            editAction.setEnabled(false);
            deleteAction.setEnabled(false);
        }
    }

    private void toggleEnabled() {
        int index = mcList.getSelectedIndex();
        if (index == -1) return;

        TypeMCListItem item = (TypeMCListItem) mcList.getMCListItem(index);

        // If it's a custom type, open the editor instead of toggling
        if (item.getTypeDescription().isEditable()) {
            editType();
        } else {
            // For default types, just toggle enabled/disabled
//            item.setEnabled(!item.getEnabled());
            mcList.repaint();
        }
    }

    private void addType() {
        showEditor(null);
    }

    private void showEditor(CustomTypeDefinition existingType) {
        // Track if we're editing
        if (existingType != null) {
            editingType = existingType.toMysterType();
        } else {
            editingType = null;
        }

        // Create editor panel with callbacks
        editorPanel = new TypeEditorPanel(tdList, existingType,
            this::onEditorSave,
            this::onEditorCancel
        );

        // Add editor panel to card layout if not already added
        containerPanel.add(editorPanel, EDITOR_VIEW);

        // Switch to editor view
        cardLayout.show(containerPanel, EDITOR_VIEW);
    }

    private void onEditorSave() {
        CustomTypeDefinition newType = editorPanel.getResult();
        if (newType != null) {
            try {
                if (editingType != null) {
                    // Editing existing type
                    tdList.updateCustomType(editingType, newType);
                } else {
                    // Adding new type
                    tdList.addCustomType(newType);
                }

                // Switch back to list view
                cardLayout.show(containerPanel, LIST_VIEW);

                // Reload the list
                reset();

                // Clear editing state
                editingType = null;
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to save type: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onEditorCancel() {
        // Clear editing state
        editingType = null;

        // Switch back to list view
        cardLayout.show(containerPanel, LIST_VIEW);
    }

    private void editType() {
        int selectedIndex = mcList.getSelectedIndex();
        if (selectedIndex < 0) return;

        TypeMCListItem item = (TypeMCListItem) mcList.getMCListItem(selectedIndex);

        if (!item.getTypeDescription().isEditable()) {
            return;
        }

        // Get the CustomTypeDefinition from the type list
        MysterType type = item.getTypeDescription().getType();
        tdList.getCustomTypeDefinition(type).ifPresentOrElse(
            customDef -> showEditor(customDef),
            () -> JOptionPane.showMessageDialog(this,
                "Could not find custom type definition for editing.",
                "Error",
                JOptionPane.ERROR_MESSAGE)
        );
    }

    private void deleteType() {
        int selectedIndex = mcList.getSelectedIndex();
        if (selectedIndex < 0) return;

        TypeMCListItem item = (TypeMCListItem) mcList.getMCListItem(selectedIndex);
        TypeDescription desc = item.getTypeDescription();

        if (!desc.isEditable()) {
            return;
        }

        try {
            // Mark for deletion (applied on save)
            pendingDeletions.add(desc.getType());

            // Reload the list to hide the deleted type (but don't clear pending deletions)
            loadList();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to mark type for deletion: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void save() {
        // Apply pending deletions first
        for (MysterType type : pendingDeletions) {
            try {
                tdList.removeCustomType(type);
            } catch (Exception ex) {
                // whatever
            }
        }
        pendingDeletions.clear();

        // Save enabled/disabled states
        for (int i = 0; i < mcList.length(); i++) {
            TypeMCListItem item = (TypeMCListItem) mcList.getMCListItem(i);
            tdList.setEnabledType(item.getObject(), item.getEnabled());
        }

        // Reload to reflect changes
        reset();
    }

    @Override
    public void reset() {
        // Clear pending deletions (cancel operation)
        pendingDeletions.clear();

        // Reload the list
        loadList();
    }

    /**
     * Loads the type list from TypeDescriptionList, filtering out pending deletions.
     */
    private void loadList() {
        mcList.clearAll();

        TypeDescription[] types = tdList.getAllTypes();
        List<TypeMCListItem> items = new ArrayList<>();

        for (TypeDescription desc : types) {
            // Skip types marked for deletion
            if (pendingDeletions.contains(desc.getType())) {
                continue;
            }

            boolean enabled = tdList.isTypeEnabledInPrefs(desc.getType());
            items.add(new TypeMCListItem(desc, enabled));
        }

        mcList.addItem(items.toArray(new com.general.mclist.MCListItemInterface[0]));

        // Update button states after loading list
        updateButtonStates();
    }

    @Override
    public String getKey() {
        return "Type Manager";
    }

    /**
     * MCListItem for displaying type information.
     */
    private static class TypeMCListItem extends GenericMCListItem<MysterType> {
        private final TypeDescription typeDesc;
        private boolean enabled;

        public TypeMCListItem(TypeDescription typeDesc, boolean enabled) {
            super(new Sortable[0], typeDesc.getType());
            this.typeDesc = typeDesc;
            this.enabled = enabled;
        }

        @Override
        public Sortable<?> getValueOfColumn(int i) {
            return switch (i) {
                case 0 -> new SortableString(typeDesc.getDescription()); // Name (user-readable)
                case 1 -> new SortableString(typeDesc.getInternalName()); // Description (internal identifier)
                case 2 -> new SortableBoolean(enabled); // Enabled
                case 3 -> new SortableString(typeDesc.getSource() == TypeSource.DEFAULT ? "Built-in" : "Custom"); // Source
                default -> new SortableString("");
            };
        }

        public TypeDescription getTypeDescription() {
            return typeDesc;
        }

        public boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Standalone test main method to preview the preferences panel.
     */
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame testFrame = new JFrame("Type Manager V2 Test");
            testFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Create the panel with a real type list
            TypeDescriptionList typeList = new com.myster.type.DefaultTypeDescriptionList(java.util.prefs.Preferences.userRoot().node("MysterTypes"));
            TypeManagerPreferences panel = new TypeManagerPreferences(typeList);

            // Add to frame and set it on the panel
            testFrame.add(panel);
            panel.addFrame(testFrame);

            // Add save/reset buttons for testing
            JPanel buttonPanel = new JPanel(new FlowLayout());
            JButton saveButton = new JButton("Save");
            saveButton.addActionListener(e -> {
                panel.save();
                JOptionPane.showMessageDialog(testFrame, "Settings saved!");
            });
            JButton resetButton = new JButton("Reset");
            resetButton.addActionListener(e -> {
                panel.reset();
                JOptionPane.showMessageDialog(testFrame, "Settings reset!");
            });

            buttonPanel.add(saveButton);
            buttonPanel.add(resetButton);

            testFrame.setLayout(new java.awt.BorderLayout());
            testFrame.add(panel, java.awt.BorderLayout.CENTER);
            testFrame.add(buttonPanel, java.awt.BorderLayout.SOUTH);

            testFrame.setSize(600, 500);
            testFrame.setLocationRelativeTo(null);
            testFrame.setVisible(true);
        });
    }
}

