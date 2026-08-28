package com.myster.type.ui;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
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
import java.util.ArrayList;
import java.util.Comparator;
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
import com.myster.access.SetMetadataTypeOp;
import com.myster.cid.ServerCid;
import com.myster.filemanager.DefaultMetadataTypeRegistry;
import com.myster.filemanager.MetadataType;
import com.myster.filemanager.MetadataTypeRegistry;
import com.myster.type.CustomTypeDefinition;
import com.myster.type.MetadataTypeId;
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
 * <p>The Metadata Profile selector is populated from {@link MetadataTypeRegistry}. Known values
 * use friendly labels. A profile introduced by a newer Myster version is retained as one
 * contextual non-canonical choice and displayed with a safe Unknown label; local runtime behavior
 * remains Generic until that profile is supported.
 *
 * <p>{@code serverSource} is optional. When empty (create mode, tests), the Members tab is
 * simply omitted.
 */
public class TypeEditorPanel extends JPanel {
    private final TypeDescriptionList typeList;
    private final CustomTypeDefinition existingType;
    private final AccessListManager accessListManager;
    private final Optional<TypeEditorServerSource> serverSource;
    private final Optional<ServerCid> localServerCid;
    private final MetadataTypeRegistry metadataTypeRegistry;

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
    private final JComboBox<MetadataTypeId> metadataTypeSelector;
    private final JButton saveButton;

    // members tab — only present in edit mode with admin key and a serverSource
    private final MCList<ServerCid> membersTable;

    /**
     * Creates a panel for creating a new custom type (no Members tab, no self-seeding).
     * Intended for tests only.
     */
    public TypeEditorPanel(TypeDescriptionList typeList,
                           AccessListManager accessListManager,
                           Runnable onSave,
                           Runnable onCancel) {
        this(typeList, accessListManager, null, Optional.empty(), Optional.empty(), onSave, onCancel);
    }

    /**
     * Creates a panel for creating or editing a custom type.
     *
     * @param existingType  the type to edit, or {@code null} to create a new type
     * @param serverSource  server source used to populate the Members tab;
     *                      empty Optional omits the tab
     * @param localServerCid the ServerCid of this server; when present and creating a new type,
     *                       it is automatically added as an {@code ADMIN} member in the genesis
     *                       block so the creator is always in the member list
     */
    public TypeEditorPanel(TypeDescriptionList typeList,
                           AccessListManager accessListManager,
                           CustomTypeDefinition existingType,
                           Optional<TypeEditorServerSource> serverSource,
                           Optional<ServerCid> localServerCid,
                           Runnable onSave,
                           Runnable onCancel) {
        this(typeList, accessListManager, existingType, serverSource, localServerCid,
                new DefaultMetadataTypeRegistry(), onSave, onCancel);
    }

    /**
     * Creates a type editor whose metadata choices come from the supplied runtime registry.
     *
     * <p>An existing non-canonical association is added as one contextual choice and rendered
     * with its safe friendly Unknown label. The exact backing value is preserved unless an
     * authorized user deliberately chooses another profile.
     *
     * @param typeList mutable type-description registry
     * @param accessListManager canonical access-list persistence
     * @param existingType type being edited, or null in create mode
     * @param serverSource optional source for member selection
     * @param localServerCid optional local member identity
     * @param metadataTypeRegistry source of locally supported metadata profiles
     * @param onSave callback after successful persistence
     * @param onCancel callback when editing is cancelled
     */
    public TypeEditorPanel(TypeDescriptionList typeList,
                           AccessListManager accessListManager,
                           CustomTypeDefinition existingType,
                           Optional<TypeEditorServerSource> serverSource,
                           Optional<ServerCid> localServerCid,
                           MetadataTypeRegistry metadataTypeRegistry,
                           Runnable onSave,
                           Runnable onCancel) {
        this.typeList = typeList;
        this.accessListManager = accessListManager;
        this.existingType = existingType;
        this.serverSource = serverSource;
        this.localServerCid = localServerCid;
        this.metadataTypeRegistry = java.util.Objects.requireNonNull(metadataTypeRegistry);
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
        metadataTypeSelector = buildMetadataTypeSelector();
        saveButton = new JButton("Save");

        membersTable = MCListFactory.buildMCList(3, true, this);

        initComponents();
        layoutComponents();


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

        formPanel.add(new JLabel("Metadata Profile:"),
            gbc.withGridLoc(0, row).withWeight(0, 0));
        formPanel.add(metadataTypeSelector,
            gbc.withGridLoc(1, row++).withWeight(1.0, 0));

        JLabel metadataHelp = new JLabel(
                "<html><i>Controls extracted details and file-list columns, not file matching.</i></html>");
        metadataHelp.setFont(metadataHelp.getFont().deriveFont(10f));
        formPanel.add(metadataHelp, gbc.withGridLoc(1, row++).withWeight(1.0, 0));

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
        // "Change Role" is disabled until ADMIN→writer linkage and multi-node
        // consensus are implemented. The role flag has no enforcement yet.
        JButton changeRoleBtn   = new JButton("Change Role");
        removeMemberBtn.setEnabled(false);
        changeRoleBtn.setEnabled(false);
        changeRoleBtn.setToolTipText("Role management is not yet implemented.");

        membersTable.addMCListEventListener(new MCListEventListener() {
            public void selectItem(MCListEvent e) {
                removeMemberBtn.setEnabled(true);
                // changeRoleBtn intentionally stays disabled
            }
            public void unselectItem(MCListEvent e) {
                removeMemberBtn.setEnabled(false);
            }
            public void doubleClick(MCListEvent e) {}
        });

        addMemberBtn.addActionListener(e -> addMember());
        removeMemberBtn.addActionListener(e -> removeMember());

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
        Map<ServerCid, Role> members = editAccessList.get().getState().getMembers();
        for (Map.Entry<ServerCid, Role> entry : members.entrySet()) {
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
        ServerCid cid = membersTable.getItem(idx);
        try {
            editAccessList.get().appendBlock(
                    new RemoveMemberOp(cid), editAdminKeyPair.get());
            accessListManager.saveAccessList(editAccessList.get());
            populateMembers();
        } catch (IOException e) {
            AnswerDialog.simpleAlert("Could not remove member: " + e.getMessage());
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
        metadataTypeSelector.setEnabled(false);
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
            metadataTypeSelector.setSelectedItem(state.getMetadataTypeId());
        } else {
            // Fallback to existingType if no access list (shouldn't normally happen)
            nameField.setText(existingType.getName());
            descriptionArea.setText(existingType.getDescription());
            extensionsField.setText(String.join(", ", existingType.getExtensions()));
            searchInArchivesCheckbox.setSelected(existingType.isSearchInArchives());
            publicRadio.setSelected(existingType.isPublic());
            privateRadio.setSelected(!existingType.isPublic());
            metadataTypeSelector.setSelectedItem(existingType.getMetadataTypeId());
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
        MetadataTypeId metadataTypeId = selectedMetadataTypeId();
        Policy policy        = publicRadio.isSelected()
                               ? Policy.defaultPermissive()
                               : Policy.defaultRestrictive();

        // Disable save immediately to prevent double-click double-genesis
        saveButton.setEnabled(false);

        if (existingType == null) {
            handleCreate(name, description, extensions, searchInArch, policy, metadataTypeId);
        } else {
            handleEdit(name, description, extensions, searchInArch, policy, metadataTypeId);
        }
    }

    private void handleCreate(String name, String description, String[] extensions,
                               boolean searchInArchives, Policy policy,
                               MetadataTypeId metadataTypeId) {
        try {
            KeyPair rsa = rsaKeyPair.get();
            KeyPair admin = adminKeyPair.get();

            // Seed the creating server as an ADMIN member so it is always present
            // in its own member list and cannot be locked out of its own type.
            List<AddMemberOp> initialMembers = localServerCid
                    .map(cid -> List.of(new AddMemberOp(cid, Role.ADMIN)))
                    .orElse(Collections.emptyList());

            AccessList accessList = AccessList.createGenesis(
                    rsa.getPublic(),
                    admin,
                    initialMembers,
                    Collections.emptyList(),
                    policy,
                    name,
                    description,
                    extensions,
                    searchInArchives,
                    metadataTypeId);

            accessListManager.saveAccessList(accessList);

            MysterType mysterType = accessList.getMysterType();
            AccessListKeyUtils.saveKeyPair(admin, mysterType);

            CustomTypeDefinition def = new CustomTypeDefinition(
                    rsa.getPublic(), name, description, extensions,
                    searchInArchives, policy.isListFilesPublic(), metadataTypeId);

            typeList.addCustomType(def);

            if (onSave != null) onSave.run();
        } catch (IOException e) {
            saveButton.setEnabled(true);
            AnswerDialog.simpleAlert("Failed to save type: " + e.getMessage());
        }
    }

    private void handleEdit(String name, String description, String[] extensions,
                             boolean searchInArchives, Policy policy,
                             MetadataTypeId metadataTypeId) {
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
            if (!metadataTypeId.equals(state.getMetadataTypeId())) {
                accessList.appendBlock(new SetMetadataTypeOp(metadataTypeId), kp);
                changed = true;
            }

            if (changed) {
                accessListManager.saveAccessList(accessList);
            }

            MysterType type = existingType.toMysterType();
            CustomTypeDefinition updatedDef = new CustomTypeDefinition(
                    existingType.getPublicKey(), name, description, extensions,
                    searchInArchives, policy.isListFilesPublic(), metadataTypeId);
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

    private JComboBox<MetadataTypeId> buildMetadataTypeSelector() {
        List<MetadataTypeId> ids = new ArrayList<>();
        addMetadataTypeId(ids, metadataTypeRegistry.generic().id());
        for (MetadataType metadataType : metadataTypeRegistry.supportedTypes()) {
            addMetadataTypeId(ids, metadataType.id());
        }

        MetadataTypeId current = existingType == null
                ? MetadataTypeId.GENERIC
                : existingType.getMetadataTypeId();
        addMetadataTypeId(ids, current);

        ids.remove(MetadataTypeId.GENERIC);
        ids.sort(Comparator.comparing(MetadataTypeId::getDisplayName)
                .thenComparing(MetadataTypeId::getIdentifier));
        ids.add(0, MetadataTypeId.GENERIC);

        JComboBox<MetadataTypeId> selector =
                new JComboBox<>(ids.toArray(MetadataTypeId[]::new));
        selector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Component component = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (component instanceof JLabel label && value instanceof MetadataTypeId id) {
                    label.setText(id.getDisplayName());
                }
                return component;
            }
        });
        selector.setSelectedItem(current);
        return selector;
    }

    private static void addMetadataTypeId(List<MetadataTypeId> ids, MetadataTypeId id) {
        if (!ids.contains(id)) {
            ids.add(id);
        }
    }

    private MetadataTypeId selectedMetadataTypeId() {
        Object selected = metadataTypeSelector.getSelectedItem();
        return selected instanceof MetadataTypeId id ? id : MetadataTypeId.GENERIC;
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
    private static class MemberItem extends GenericMCListItem<ServerCid> {
        MemberItem(ServerCid cid, Role role, TypeEditorServerSource serverSource) {
            super(new Sortable[0], cid);
            this.cid = cid;
            this.role = role;
            this.displayName = serverSource.resolveDisplayName(cid)
                    .orElse(cid.asHex().substring(0, 12) + "…");
        }

        private final ServerCid cid;
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
