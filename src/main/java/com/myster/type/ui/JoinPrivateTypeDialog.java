package com.myster.type.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Arrays;

import com.general.thread.PromiseFuture;
import com.general.util.AnswerDialog;
import com.general.util.GridBagBuilder;
import com.general.util.MessageField;
import com.myster.access.AccessList;
import com.myster.type.join.TypeJoinCoordinator;
import com.myster.type.join.TypeJoinUri;

/** Confirmation dialog shared by pasted links, launch arguments, and native URI activation. */
public final class JoinPrivateTypeDialog extends JDialog {
    private final TypeJoinCoordinator coordinator;
    private final JTextField uriField = new JTextField(48);
    private final JButton previewButton = new JButton("Preview");
    private final JLabel typeValue = new JLabel("—");
    private final JLabel bootstrapValue = new JLabel("—");
    private final JPasswordField codeField = new JPasswordField(28);
    private final JCheckBox enable = new JCheckBox("Enable this type");
    private final JButton joinButton = new JButton("Join");
    private final MessageField status = new MessageField("Paste an invitation link to preview it.");
    private TypeJoinCoordinator.Preview preview;
    private PromiseFuture<?> activeOperation;

    public JoinPrivateTypeDialog(Window owner, TypeJoinCoordinator coordinator, String initialUri) {
        super(owner, "Join Private Network", ModalityType.APPLICATION_MODAL);
        this.coordinator = java.util.Objects.requireNonNull(coordinator);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        uriField.setText(initialUri == null ? "" : initialUri);
        enable.setSelected(coordinator.defaultEnabled());
        joinButton.setEnabled(false);
        buildUi();
        if (!uriField.getText().isBlank()) {
            preview();
        }
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        GridBagBuilder constraints = new GridBagBuilder().withInsets(new Insets(4, 4, 4, 4));

        int row = 0;

        form.add(new JLabel("Invitation link:"), constraints.withGridLoc(0, row));
        form.add(uriField, constraints.withGridLoc(1, row).withWeight(1, 0)
                .withFill(GridBagConstraints.HORIZONTAL));
        form.add(previewButton, constraints.withGridLoc(2, row++));
        form.add(new JLabel("Type:"), constraints.withGridLoc(0, row));
        form.add(typeValue, constraints.withGridLoc(1, row++));
        form.add(new JLabel("Bootstrap:"), constraints.withGridLoc(0, row));
        form.add(bootstrapValue, constraints.withGridLoc(1, row++));
        form.add(new JLabel("Password:"), constraints.withGridLoc(0, row));
        form.add(codeField, constraints.withGridLoc(1, row++).withWeight(1, 0)
                .withFill(GridBagConstraints.HORIZONTAL));
        form.add(enable, constraints.withGridLoc(1, row++));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");

        previewButton.addActionListener(event -> preview());
        joinButton.addActionListener(event -> join());
        cancel.addActionListener(event -> dispose());
        codeField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { updateJoinEnabled(); }
            public void removeUpdate(DocumentEvent event) { updateJoinEnabled(); }
            public void changedUpdate(DocumentEvent event) { updateJoinEnabled(); }
        });
        buttons.add(joinButton);
        buttons.add(cancel);

        add(status, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(previewButton);

        pack();
        setLocationRelativeTo(getOwner());
    }

    private void preview() {
        TypeJoinUri uri;
        try {
            uri = TypeJoinUri.parse(uriField.getText().trim());
        } catch (java.io.IOException exception) {
            status.sayError("That is not a valid Myster invitation link.");
            return;
        }
        preview = null;
        joinButton.setEnabled(false);
        previewButton.setEnabled(false);

        status.say("Finding the invitation bootstrap…");

        PromiseFuture<TypeJoinCoordinator.Preview> operation = coordinator.prepare(uri);
        activeOperation = operation;
        operation.useEdt()
                 .addResultListener( (TypeJoinCoordinator.Preview result) -> {
                    previewButton.setEnabled(true);
                    preview = result;
                    typeValue.setText(result.typeName() + " (" + abbreviate(result.uri().type().toHexString()) + ")");
                    bootstrapValue.setText(abbreviate(result.uri().bootstrap().asHex()));
                    result.uri().code().ifPresent(codeField::setText);
                    codeField.setEnabled(result.invitationRequired());
                    updateJoinEnabled();
                    status.say(result.invitationRequired()
                            ? "Network verified. Enter the separately shared password, then join."
                            : "This network is public; no invitation password is required.");
                    getRootPane().setDefaultButton(joinButton);
                 }).addExceptionListener( (Throwable exception) -> {
                    previewButton.setEnabled(true);
                    status.sayError(friendlyMessage(exception));
                 });
    }

    private void join() {
        if (preview == null) {
            return;
        }
        char[] password = codeField.getPassword();
        if (preview.invitationRequired() && password.length == 0) {
            status.sayError("Enter the separately shared invitation password.");
            return;
        }
        String code = new String(password);
        Arrays.fill(password, '\0');
        joinButton.setEnabled(false);
        previewButton.setEnabled(false);
        status.say("Redeeming invitation and verifying membership…");
        PromiseFuture<AccessList> operation = coordinator.redeem(preview, code, enable.isSelected());
        activeOperation = operation;
        operation.useEdt()
                 .addResultListener((AccessList ignored) -> {
                    codeField.setText("");
                    AnswerDialog.simpleAlert(JoinPrivateTypeDialog.this,
                            "The network was joined successfully.");
                    dispose();
                 }).addExceptionListener((Throwable exception) -> {
                    joinButton.setEnabled(true);
                    previewButton.setEnabled(true);
                    status.sayError(friendlyMessage(exception));
                 });
    }

    private static String friendlyMessage(Throwable exception) {
        if (exception instanceof TypeJoinCoordinator.JoinException joinException) {
            return joinException.getMessage();
        }
        return "The operation could not be completed.";
    }

    private void updateJoinEnabled() {
        char[] password = codeField.getPassword();
        try {
            joinButton.setEnabled(preview != null
                    && (!preview.invitationRequired() || password.length > 0)
                    && previewButton.isEnabled());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static String abbreviate(String value) {
        return value.length() <= 12 ? value : value.substring(0, 12) + "…";
    }

    @Override
    public void dispose() {
        if (activeOperation != null) {
            activeOperation.cancel();
        }
        Arrays.fill(codeField.getPassword(), '\0');
        codeField.setText("");
        super.dispose();
    }
}
