package com.myster.type.ui;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
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
import java.util.Optional;

import com.general.util.GridBagBuilder;
import com.general.util.AnswerDialog;
import com.myster.access.AccessList;
import com.myster.access.AccessListKeyUtils;
import com.myster.access.AccessListManager;
import com.myster.access.AccessListState;
import com.myster.access.Policy;
import com.myster.access.SetDescriptionOp;
import com.myster.access.SetExtensionsOp;
import com.myster.access.SetNameOp;
import com.myster.access.SetPolicyOp;
import com.myster.access.SetSearchInArchivesOp;
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
 * is present, only the fields that actually changed produce new signed blocks appended to the
 * chain.
 */
public class TypeEditorPanel extends JPanel {
    private final TypeDescriptionList typeList;
    private final CustomTypeDefinition existingType;
    private final AccessListManager accessListManager;

    private final Runnable onSave;
    private final Runnable onCancel;

    // pop in create mode only
    private final Optional<KeyPair> rsaKeyPair;
    private final Optional<KeyPair> adminKeyPair;

    // pop in edit mode only
    private final Optional<KeyPair> editAdminKeyPair;
    private final Optional<AccessList> editAccessList;

    private JTextField nameField;
    private JTextArea descriptionArea;
    private JTextField extensionsField;
    private JCheckBox searchInArchivesCheckbox;
    private JRadioButton publicRadio;
    private JRadioButton privateRadio;
    private JButton saveButton;

    /**
     * Creates a panel for creating a new custom type.
     */
    public TypeEditorPanel(TypeDescriptionList typeList,
                           AccessListManager accessListManager,
                           Runnable onSave,
                           Runnable onCancel) {
        this(typeList, accessListManager, null, onSave, onCancel);
    }

    /**
     * Creates a panel for creating or editing a custom type.
     *
     * @param existingType the type to edit, or null to create a new type
     */
    public TypeEditorPanel(TypeDescriptionList typeList,
                           AccessListManager accessListManager,
                           CustomTypeDefinition existingType,
                           Runnable onSave,
                           Runnable onCancel) {
        this.typeList = typeList;
        this.accessListManager = accessListManager;
        this.existingType = existingType;
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
        nameField = new JTextField(30);
        descriptionArea = new JTextArea(3, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        extensionsField = new JTextField(30);
        extensionsField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                normalizeExtensionsField();
            }
        });

        searchInArchivesCheckbox = new JCheckBox("Search inside ZIP/archive files");

        publicRadio  = new JRadioButton("Public", true);
        privateRadio = new JRadioButton("Members only");

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

        // Form
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

        add(formPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveButton = new JButton("Save");
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));
        saveButton.addActionListener(e -> handleOk());
        buttonPanel.add(saveButton);
        add(buttonPanel, BorderLayout.SOUTH);
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
        if (extList.isEmpty()) {
            String answer = AnswerDialog.simpleAlert(AnswerDialog.getCenteredFrame(),
                    "No file extensions specified. This type will match all files. Continue?",
                    new String[] { "Continue", "Cancel" });
            if (!"Continue".equals(answer)) return;
        }

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
}
