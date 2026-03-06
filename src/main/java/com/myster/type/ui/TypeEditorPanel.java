package com.myster.type.ui;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.general.mclist.GenericMCListItem;
import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventListener;
import com.general.mclist.MCListFactory;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.general.util.AnswerDialog;
import com.general.util.GridBagBuilder;
import com.myster.access.AccessList;
import com.myster.access.AccessListKeyUtils;
import com.myster.access.AccessListManager;
import com.myster.access.AccessListState;
import com.myster.access.AddMemberOp;
import com.myster.access.Policy;
import com.myster.access.RemoveMemberOp;
import com.myster.access.Role;
import com.myster.access.SetDescriptionOp;
import com.myster.access.SetExtensionsOp;
import com.myster.access.SetNameOp;
import com.myster.access.SetPolicyOp;
import com.myster.access.SetSearchInArchivesOp;
import com.myster.identity.Cid128;
import com.myster.type.CustomTypeDefinition;
import com.myster.type.MysterType;
import com.myster.type.TypeDescriptionList;

/**
 * Panel for creating or editing custom MysterTypes backed by an {@link AccessList}.
 *
 * <p><b>Create mode</b> (when {@code existingType} is null): generates a fresh RSA keypair for
 * the type identity and a fresh Ed25519 keypair for signing access list blocks. On save, writes
 * the genesis access list and the admin keypair to disk via {@link AccessListManager} and
 * {@link AccessListKeyUtils}, then registers the type with the {@link TypeDescriptionList}.
 *
 * <p><b>Edit mode</b> (when {@code existingType} is non-null): checks for the presence of an
 * admin key file. If absent, all fields are read-only and Save is disabled — this covers both
 * types that were imported from the network and types created on another machine. If the admin key
 * is present, the panel wraps the form in a {@link JTabbedPane} and adds a <b>Members tab</b>
 * that shows the current member list and provides Add, Remove, and Change Role operations, each
 * backed by a signed block appended to the access list.
 *
 * <p>{@code serverSource} is optional. When empty (create mode, tests), the Members tab is
 * simply omitted.
 */
public class TypeEditorPanel extends JPanel {
    private final TypeDescriptionList typeList;
    private final CustomTypeDefinition existingType;
    private final AccessListManager accessListManager;
    private final Optional<TypeEditorServerSource> serverSource;

    private final Runnable onSave;
    private final Runnable onCancel;

    // populated in create mode only
    private final Optional<KeyPair> rsaKeyPair;
    private final Optional<KeyPair> adminKeyPair;

    // populated in edit mode only
    private final Optional<KeyPair> editAdminKeyPair;
    private final Optional<AccessList> editAccessList;

    private final JTextField nameField;
    private final JTextArea descriptionArea;
    private final JTextField extensionsField;
    private final JCheckBox searchInArchivesCheckbox;
    private final JRadioButton publicRadio;
    private final JRadioButton privateRadio;
    private final JButton saveButton;

    // members tab — only present in edit mode with admin key and a serverSource
    private final MCList<Cid128> membersTable;

    /**
     * Creates a panel for creating a new custom type (no Members tab).
     */
    public TypeEditorPanel(TypeDescriptionList typeList,
                           AccessListManager accessListManager,
                           Runnable onSave,
                           Runnable onCancel) {
        this(typeList, accessListManager, null, Optional.empty(), onSave, onCancel);
    }

    /**
     * Creates a panel for creating or editing a custom type.
     *
     * @param existingType the type to edit, or {@code null} to create a new type
     * @param serverSource server source used to populate the Members tab;
     *                     empty Optional omits the tab
     */
    public TypeEditorPanel(TypeDescriptionList typeList,
                           AccessListManager accessListManager,
                           CustomTypeDefinition existingType,
                           Optional<TypeEditorServerSource> serverSource,
                           Runnable onSave,
                           Runnable onCancel) {
        this.typeList = typeList;
        this.accessListManager = accessListManager;
        this.existingType = existingType;
        this.serverSource = serverSource;
        this.onSave = onSave;
        this.onCancel = onCancel;

        if (existingType == null) {
            rsaKeyPair = Optional.of(generateRsaKeyPair());
            adminKeyPair = Optional.of(generateEd25519KeyPair());
            editAdminKeyPair = Optional.empty();
            editAccessList = Optional.empty();
        } else {
            rsaKeyPair = Optional.empty();
            adminKeyPair = Optional.empty();
            MysterType type = existingType.toMysterType();

            Optional<KeyPair> keyPair = Optional.empty();
            Optional<AccessList> accessList = Optional.empty();

            if (AccessListKeyUtils.hasKeyPair(type)) {
                try {
                    keyPair = AccessListKeyUtils.loadKeyPair(type);
                } catch (IOException e) {
                    keyPair = Optional.empty();
                }
                accessList = accessListManager.loadAccessList(type);
            }
            editAdminKeyPair = keyPair;
            editAccessList = accessList;
        }

        nameField = new JTextField(30);
        descriptionArea = new JTextArea(3, 30);
        extensionsField = new JTextField(30);
        searchInArchivesCheckbox = new JCheckBox("Search inside ZIP/archive files");
        publicRadio  = new JRadioButton("Public", true);
        privateRadio = new JRadioButton("Members only");
        saveButton = new JButton("Save");

        initComponents();
        layoutComponents();

        membersTable = MCListFactory.buildMCList(3, true, this);

        if (existingType != null) {
            populateFromAccessList();
            if (editAdminKeyPair.isEmpty()) {
                setReadOnly();
            }
        }
    }

    private void initComponents() {
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);


        extensionsField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                normalizeExtensionsField();
            }
        });

        ButtonGroup group = new ButtonGroup();
        group.add(publicRadio);
        group.add(privateRadio);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10, 10, 10, 10),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                    javax.swing.UIManager.getColor("Component.borderColor"), 2),
                BorderFactory.createEmptyBorder(5, 5, 5, 5))));

        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JLabel titleLabel = new JLabel(existingType == null ? "Add Custom Type" : "Edit Custom Type");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleBar.add(titleLabel, BorderLayout.WEST);

        JButton closeButton = new JButton("×");
        closeButton.setFont(closeButton.getFont().deriveFont(20f));
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusPainted(false);
        closeButton.setPreferredSize(new java.awt.Dimension(30, 30));
        closeButton.setToolTipText("Cancel and return to list");
        closeButton.addActionListener(e -> handleCancel());
        titleBar.add(closeButton, BorderLayout.EAST);
        add(titleBar, BorderLayout.NORTH);

        JPanel formPanel = buildMetadataForm();

        // In edit mode with an admin key and a serverSource, wrap in a tabbed pane
        if (existingType != null && editAdminKeyPair.isPresent() && serverSource.isPresent()) {
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Metadata", formPanel);
            tabs.addTab("Members", buildMembersTab());
            add(tabs, BorderLayout.CENTER);
        } else {
            add(formPanel, BorderLayout.CENTER);
        }

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));
        saveButton.addActionListener(e -> handleOk());
        buttonPanel.add(saveButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /** Builds the metadata form panel (the same layout as the old flat form). */
    private JPanel buildMetadataForm() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        var gbc = new GridBagBuilder()
            .withInsets(new Insets(5, 5, 5, 5))
            .withFill(GridBagConstraints.HORIZONTAL)
            .withAnchor(GridBagConstraints.WEST);

        int row = 0;
        formPanel.add(new JLabel("Name:"), gbc.withGridLoc(0, row).withWeight(0, 0));
        formPanel.add(nameField, gbc.withGridLoc(1, row++).withWeight(1.0, 0));

        formPanel.add(new JLabel("Description:"),
            gbc.withGridLoc(0, row).withWeight(0, 0).withAnchor(GridBagConstraints.NORTHWEST));
        formPanel.add(new JScrollPane(descriptionArea),
            gbc.withGridLoc(1, row++).withWeight(1.0, 0).withFill(GridBagConstraints.BOTH));

        formPanel.add(new JLabel("File Extensions:"), gbc.withGridLoc(0, row).withWeight(0, 0)
            .withFill(GridBagConstraints.HORIZONTAL).withAnchor(GridBagConstraints.WEST));
        formPanel.add(extensionsField, gbc.withGridLoc(1, row++).withWeight(1.0, 0));

        JLabel extHelp = new JLabel("<html><i>Comma-separated, e.g.: exe, avi, mp3</i></html>");
        extHelp.setFont(extHelp.getFont().deriveFont(10f));
        formPanel.add(extHelp, gbc.withGridLoc(1, row++).withWeight(1.0, 0));

        formPanel.add(searchInArchivesCheckbox,
            gbc.withGridLoc(0, row++).withSize(2, 1).withWeight(1.0, 0));

        JPanel networkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        networkPanel.add(new JLabel("Network Type:"));
        networkPanel.add(publicRadio);
        networkPanel.add(privateRadio);
        formPanel.add(networkPanel, gbc.withGridLoc(0, row++).withSize(2, 1).withWeight(1.0, 0));

        return formPanel;
    }

    /**
     * Builds the Members tab panel. Only called when edit mode + admin key + serverSource are all present.
     */
    private JPanel buildMembersTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        membersTable.setColumnName(0, "Server Name");
        membersTable.setColumnName(1, "Role");
        membersTable.setColumnName(2, "Identity");
        membersTable.setColumnWidth(0, 200);
        membersTable.setColumnWidth(1, 80);
        membersTable.setColumnWidth(2, 140);
        membersTable.sortBy(-1);
        panel.add(membersTable.getPane(), BorderLayout.CENTER);

        // Toolbar buttons
        JButton addMemberBtn    = new JButton("Add Member…");
        JButton removeMemberBtn = new JButton("Remove Member");
        JButton changeRoleBtn   = new JButton("Change Role");
        removeMemberBtn.setEnabled(false);
        changeRoleBtn.setEnabled(false);

        membersTable.addMCListEventListener(new MCListEventListener() {
            public void selectItem(MCListEvent e) {
                removeMemberBtn.setEnabled(true);
                changeRoleBtn.setEnabled(true);
            }
            public void unselectItem(MCListEvent e) {
                removeMemberBtn.setEnabled(false);
                changeRoleBtn.setEnabled(false);
            }
            public void doubleClick(MCListEvent e) {}
        });

        addMemberBtn.addActionListener(e -> addMember());
        removeMemberBtn.addActionListener(e -> removeMember());
        changeRoleBtn.addActionListener(e -> changeRole());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        toolbar.add(addMemberBtn);
        toolbar.add(removeMemberBtn);
        toolbar.add(changeRoleBtn);
        panel.add(toolbar, BorderLayout.SOUTH);

        populateMembers();
        return panel;
    }

    /** Reloads the members table from the current access list state. */
    private void populateMembers() {
        if (membersTable == null || editAccessList.isEmpty()) return;
        membersTable.clearAll();
        Map<Cid128, Role> members = editAccessList.get().getState().getMembers();
        for (Map.Entry<Cid128, Role> entry : members.entrySet()) {
            membersTable.addItem(new MemberItem(entry.getKey(), entry.getValue(), serverSource.get()));
        }
    }

    private void addMember() {
        if (serverSource.isEmpty() || editAdminKeyPair.isEmpty() || editAccessList.isEmpty()) return;
        ServerPickerDialog dialog = new ServerPickerDialog(
                SwingUtilities.getWindowAncestor(this), serverSource.get());
        ServerPickerDialog.PickedServer picked = dialog.showAndWait();
        if (picked == null) return;
        try {
            editAccessList.get().appendBlock(
                    new AddMemberOp(picked.cid(), Role.MEMBER), editAdminKeyPair.get());
            accessListManager.saveAccessList(editAccessList.get());
            populateMembers();
        } catch (IOException e) {
            AnswerDialog.simpleAlert("Could not add member: " + e.getMessage());
        }
    }

    private void removeMember() {
        if (editAdminKeyPair.isEmpty() || editAccessList.isEmpty()) return;
        int idx = membersTable.getSelectedIndex();
        if (idx < 0) return;
        Cid128 cid = membersTable.getItem(idx);
        try {
            editAccessList.get().appendBlock(
                    new RemoveMemberOp(cid), editAdminKeyPair.get());
            accessListManager.saveAccessList(editAccessList.get());
            populateMembers();
        } catch (IOException e) {
            AnswerDialog.simpleAlert("Could not remove member: " + e.getMessage());
        }
    }

    private void changeRole() {
        if (editAdminKeyPair.isEmpty() || editAccessList.isEmpty()) return;
        int idx = membersTable.getSelectedIndex();
        if (idx < 0) return;
        Cid128 cid = membersTable.getItem(idx);
        Role current = editAccessList.get().getState().getRole(cid);
        if (current == null) return;
        Role toggled = current.equals(Role.ADMIN) ? Role.MEMBER : Role.ADMIN;
        try {
            editAccessList.get().appendBlock(
                    new AddMemberOp(cid, toggled), editAdminKeyPair.get());
            accessListManager.saveAccessList(editAccessList.get());
            populateMembers();
        } catch (IOException e) {
            AnswerDialog.simpleAlert("Could not change role: " + e.getMessage());
        }
    }

    /** Sets all form fields to read-only and disables Save. */
    private void setReadOnly() {
        nameField.setEditable(false);
        descriptionArea.setEditable(false);
        extensionsField.setEditable(false);
        searchInArchivesCheckbox.setEnabled(false);
        publicRadio.setEnabled(false);
        privateRadio.setEnabled(false);
        saveButton.setEnabled(false);
        saveButton.setToolTipText("Read-only: this type was not created on this machine.");
    }

    /** Populates form from the current access list state (edit mode). */
    private void populateFromAccessList() {
        if (editAccessList.isPresent()) {
            AccessListState state = editAccessList.get().getState();
            nameField.setText(state.getName() != null ? state.getName() : "");
            descriptionArea.setText(state.getDescription() != null ? state.getDescription() : "");
            extensionsField.setText(String.join(", ", state.getExtensions()));
            searchInArchivesCheckbox.setSelected(state.isSearchInArchives());
            publicRadio.setSelected(state.getPolicy().isListFilesPublic());
            privateRadio.setSelected(!state.getPolicy().isListFilesPublic());
        } else {
            // Fallback to existingType if no access list (shouldn't normally happen)
            nameField.setText(existingType.getName());
            descriptionArea.setText(existingType.getDescription());
            extensionsField.setText(String.join(", ", existingType.getExtensions()));
            searchInArchivesCheckbox.setSelected(existingType.isSearchInArchives());
            publicRadio.setSelected(existingType.isPublic());
            privateRadio.setSelected(!existingType.isPublic());
        }
    }

    private void handleOk() {
        normalizeExtensionsField();

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            AnswerDialog.simpleAlert("Name is required.");
            return;
        }

        // Duplicate name check (skip if name unchanged in edit mode)
        boolean nameUnchanged = existingType != null && name.equals(existingType.getName());
        if (!nameUnchanged) {
            for (var td : typeList.getAllTypes()) {
                if (td.getDescription().equals(name)) {
                    AnswerDialog.simpleAlert("A type with this name already exists.");
                    return;
                }
            }
        }

        List<String> extList = ExtensionNormalizer.parseToList(extensionsField.getText());

        String[] extensions  = extList.toArray(new String[0]);
        String description   = descriptionArea.getText().trim();
        boolean searchInArch = searchInArchivesCheckbox.isSelected();
        Policy policy        = publicRadio.isSelected()
                               ? Policy.defaultPermissive()
                               : Policy.defaultRestrictive();

        // Disable save immediately to prevent double-click double-genesis
        saveButton.setEnabled(false);

        if (existingType == null) {
            handleCreate(name, description, extensions, searchInArch, policy);
        } else {
            handleEdit(name, description, extensions, searchInArch, policy);
        }
    }

    private void handleCreate(String name, String description, String[] extensions,
                               boolean searchInArchives, Policy policy) {
        try {
            KeyPair rsa = rsaKeyPair.get();
            KeyPair admin = adminKeyPair.get();

            AccessList accessList = AccessList.createGenesis(
                    rsa.getPublic(),
                    admin,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    policy,
                    name,
                    description,
                    extensions,
                    searchInArchives);

            accessListManager.saveAccessList(accessList);

            MysterType mysterType = accessList.getMysterType();
            AccessListKeyUtils.saveKeyPair(admin, mysterType);

            CustomTypeDefinition def = new CustomTypeDefinition(
                    rsa.getPublic(), name, description, extensions,
                    searchInArchives, policy.isListFilesPublic());

            typeList.addCustomType(def);

            if (onSave != null) onSave.run();
        } catch (IOException e) {
            saveButton.setEnabled(true);
            AnswerDialog.simpleAlert("Failed to save type: " + e.getMessage());
        }
    }

    private void handleEdit(String name, String description, String[] extensions,
                             boolean searchInArchives, Policy policy) {
        if (editAdminKeyPair.isEmpty() || editAccessList.isEmpty()) {
            saveButton.setEnabled(true);
            return;
        }

        try {
            AccessList accessList = editAccessList.get();
            AccessListState state = accessList.getState();
            KeyPair kp = editAdminKeyPair.get();

            boolean changed = false;

            if (!name.equals(state.getName() != null ? state.getName() : "")) {
                accessList.appendBlock(new SetNameOp(name), kp);
                changed = true;
            }
            if (!description.equals(state.getDescription() != null ? state.getDescription() : "")) {
                accessList.appendBlock(new SetDescriptionOp(description), kp);
                changed = true;
            }
            if (!java.util.Arrays.equals(extensions, state.getExtensions())) {
                accessList.appendBlock(new SetExtensionsOp(extensions), kp);
                changed = true;
            }
            if (searchInArchives != state.isSearchInArchives()) {
                accessList.appendBlock(new SetSearchInArchivesOp(searchInArchives), kp);
                changed = true;
            }
            if (!policy.equals(state.getPolicy())) {
                accessList.appendBlock(new SetPolicyOp(policy), kp);
                changed = true;
            }

            if (changed) {
                accessListManager.saveAccessList(accessList);
            }

            MysterType type = existingType.toMysterType();
            CustomTypeDefinition updatedDef = new CustomTypeDefinition(
                    existingType.getPublicKey(), name, description, extensions,
                    searchInArchives, policy.isListFilesPublic());
            typeList.updateCustomType(type, updatedDef);

            if (onSave != null) onSave.run();
        } catch (IOException e) {
            saveButton.setEnabled(true);
            AnswerDialog.simpleAlert("Failed to save changes: " + e.getMessage());
        }
    }

    private void handleCancel() {
        if (onCancel != null) onCancel.run();
    }

    private void normalizeExtensionsField() {
        String text = extensionsField.getText();
        if (text == null || text.trim().isEmpty()) return;
        String normalized = ExtensionNormalizer.normalize(text);
        if (!normalized.equals(text)) extensionsField.setText(normalized);
    }


    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    private static KeyPair generateEd25519KeyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            try {
                return KeyPairGenerator.getInstance("EdDSA").generateKeyPair();
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("Ed25519/EdDSA not available", ex);
            }
        }
    }

    /** MCList item representing one member row in the Members tab. */
    private static class MemberItem extends GenericMCListItem<Cid128> {
        MemberItem(Cid128 cid, Role role, TypeEditorServerSource serverSource) {
            super(new Sortable[0], cid);
            this.cid = cid;
            this.role = role;
            this.displayName = serverSource.resolveDisplayName(cid)
                    .orElse(cid.asHex().substring(0, 12) + "…");
        }

        private final Cid128 cid;
        private final Role role;
        private final String displayName;

        @Override
        public Sortable<?> getValueOfColumn(int col) {
            return switch (col) {
                case 0 -> new SortableString(displayName);
                case 1 -> new SortableString(role.getIdentifier());
                case 2 -> new SortableString(cid.asHex().substring(0, 12) + "…");
                default -> new SortableString("");
            };
        }
    }
}
