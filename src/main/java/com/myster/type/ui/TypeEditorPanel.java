package com.myster.type.ui;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.security.PublicKey;
import java.util.List;

import com.general.util.GridBagBuilder;
import com.myster.type.CustomTypeDefinition;
import com.myster.type.TypeDescriptionList;

/**
 * Panel for creating or editing custom MysterTypes.
 * Designed to be shown inline within the preferences panel rather than as a modal dialog.
 */
public class TypeEditorPanel extends JPanel {
    private final TypeDescriptionList typeList;
    private final CustomTypeDefinition existingType; // null for create mode
    private final PublicKey publicKey;

    private final Runnable onSave;
    private final Runnable onCancel;

    private JTextField nameField;
    private JTextArea descriptionArea;
    private JTextField extensionsField;
    private JCheckBox searchInArchivesCheckbox;
    private JRadioButton publicRadio;
    private JRadioButton privateRadio;

    private CustomTypeDefinition result = null;

    /**
     * Creates a panel for creating a new custom type.
     *
     * @param typeList the type description list (for validation)
     * @param onSave callback when user clicks OK
     * @param onCancel callback when user clicks Cancel
     */
    public TypeEditorPanel(TypeDescriptionList typeList, Runnable onSave, Runnable onCancel) {
        this(typeList, null, onSave, onCancel);
    }

    /**
     * Creates a panel for editing an existing custom type.
     *
     * @param typeList the type description list (for validation)
     * @param existingType the type to edit, or null to create new
     * @param onSave callback when user clicks OK
     * @param onCancel callback when user clicks Cancel
     */
    public TypeEditorPanel(TypeDescriptionList typeList, CustomTypeDefinition existingType,
                          Runnable onSave, Runnable onCancel) {
        this.typeList = typeList;
        this.existingType = existingType;
        this.onSave = onSave;
        this.onCancel = onCancel;

        // Generate or reuse public key
        if (existingType == null) {
            this.publicKey = CustomTypeDefinition.generateNew("temp", "", new String[0], false, true).getPublicKey();
        } else {
            this.publicKey = existingType.getPublicKey();
        }

        initComponents();
        layoutComponents();

        if (existingType != null) {
            populateFromExisting();
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

        publicRadio = new JRadioButton("Public (anyone can join)", true);
        privateRadio = new JRadioButton("Private (restricted access)");
        privateRadio.setEnabled(false);
        privateRadio.setToolTipText("Private networks not yet implemented. Coming soon!");

        ButtonGroup networkTypeGroup = new ButtonGroup();
        networkTypeGroup.add(publicRadio);
        networkTypeGroup.add(privateRadio);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());

        // Add a subtle visual distinction with extra padding around the border
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10, 10, 10, 10), // Outer padding for spacing from frame
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(javax.swing.UIManager.getColor("Component.borderColor"), 2),
                BorderFactory.createEmptyBorder(5, 5, 5, 5) // Inner padding for content
            )
        ));

        // Title bar with close button
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel titleLabel = new JLabel(existingType == null ? "Add Custom Type" : "Edit Custom Type");
        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        titleBar.add(titleLabel, BorderLayout.WEST);

        // Close button (small X)
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

        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        var gbc = new GridBagBuilder()
            .withInsets(new Insets(5, 5, 5, 5))
            .withFill(GridBagConstraints.HORIZONTAL)
            .withAnchor(GridBagConstraints.WEST);

        int row = 0;

        // Name
        formPanel.add(new JLabel("Name:"),
            gbc.withGridLoc(0, row).withWeight(0, 0));
        formPanel.add(nameField,
            gbc.withGridLoc(1, row).withWeight(1.0, 0));
        row++;

        // Description
        formPanel.add(new JLabel("Description:"),
            gbc.withGridLoc(0, row).withWeight(0, 0).withAnchor(GridBagConstraints.NORTHWEST));
        formPanel.add(new JScrollPane(descriptionArea),
            gbc.withGridLoc(1, row).withWeight(1.0, 0).withFill(GridBagConstraints.BOTH));
        row++;

        // Extensions
        formPanel.add(new JLabel("File Extensions:"),
            gbc.withGridLoc(0, row).withWeight(0, 0).withFill(GridBagConstraints.HORIZONTAL).withAnchor(GridBagConstraints.WEST));
        formPanel.add(extensionsField,
            gbc.withGridLoc(1, row).withWeight(1.0, 0));
        row++;

        // Help text
        JLabel extensionsHelp = new JLabel("<html><i>Comma-separated list, e.g.: exe, avi, mp3</i></html>");
        extensionsHelp.setFont(extensionsHelp.getFont().deriveFont(10f));
        formPanel.add(extensionsHelp,
            gbc.withGridLoc(1, row).withWeight(1.0, 0));
        row++;

        // Search in archives
        formPanel.add(searchInArchivesCheckbox,
            gbc.withGridLoc(0, row).withSize(2, 1).withWeight(1.0, 0));
        row++;

        // Network type
        JPanel networkTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        networkTypePanel.add(new JLabel("Network Type:"));
        networkTypePanel.add(publicRadio);
        networkTypePanel.add(privateRadio);
        formPanel.add(networkTypePanel,
            gbc.withGridLoc(0, row).withSize(2, 1).withWeight(1.0, 0));

        add(formPanel, BorderLayout.CENTER);

        // Save button at bottom
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> handleOk());
        saveButton.setFont(saveButton.getFont().deriveFont(java.awt.Font.BOLD));
        buttonPanel.add(saveButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void populateFromExisting() {
        nameField.setText(existingType.getName());
        descriptionArea.setText(existingType.getDescription());
        extensionsField.setText(String.join(", ", existingType.getExtensions()));
        searchInArchivesCheckbox.setSelected(existingType.isSearchInArchives());
        publicRadio.setSelected(existingType.isPublic());
        privateRadio.setSelected(!existingType.isPublic());
    }

    private void normalizeExtensionsField() {
        String currentText = extensionsField.getText();
        if (currentText == null || currentText.trim().isEmpty()) {
            return;
        }

        String normalized = ExtensionNormalizer.normalize(currentText);
        if (!normalized.equals(currentText)) {
            extensionsField.setText(normalized);
        }
    }

    private void handleOk() {
        normalizeExtensionsField();

        // Validate name
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Check for duplicate names
        if (existingType == null || !name.equals(existingType.getName())) {
            for (var typeDesc : typeList.getAllTypes()) {
                if (typeDesc.getDescription().equals(name)) {
                    JOptionPane.showMessageDialog(this, "A type with this name already exists.",
                        "Validation Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        }

        // Parse extensions
        List<String> extList = ExtensionNormalizer.parseToList(extensionsField.getText());
        if (extList.isEmpty()) {
            int response = JOptionPane.showConfirmDialog(this,
                "No file extensions specified. This type will match all files. Continue?",
                "Confirm", JOptionPane.YES_NO_OPTION);
            if (response != JOptionPane.YES_OPTION) {
                return;
            }
        }

        String[] extensions = extList.toArray(new String[0]);
        String description = descriptionArea.getText().trim();
        boolean searchInArchives = searchInArchivesCheckbox.isSelected();
        boolean isPublic = publicRadio.isSelected();

        result = new CustomTypeDefinition(
            publicKey,
            name,
            description,
            extensions,
            searchInArchives,
            isPublic
        );

        if (onSave != null) {
            onSave.run();
        }
    }

    private void handleCancel() {
        result = null;
        if (onCancel != null) {
            onCancel.run();
        }
    }

    /**
     * Gets the result after user clicks OK.
     *
     * @return the custom type definition, or null if cancelled
     */
    public CustomTypeDefinition getResult() {
        return result;
    }
}

