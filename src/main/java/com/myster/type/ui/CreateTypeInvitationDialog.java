package com.myster.type.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.util.Arrays;

import com.general.thread.CallAdapter;
import com.general.thread.PromiseFutures;
import com.general.util.GridBagBuilder;
import com.general.util.MessageField;
import com.myster.cid.ServerCid;
import com.myster.type.MysterType;
import com.myster.type.join.InvitationLifetime;
import com.myster.type.join.TypeInvitation;
import com.myster.type.join.TypeInvitationManager;
import com.myster.type.join.TypeJoinUri;

/** Modal administrator dialog that creates and copies one password-free invitation link. */
public final class CreateTypeInvitationDialog extends JDialog {
    private final TypeInvitationManager invitationManager;
    private final MysterType type;
    private final ServerCid bootstrap;
    private final JPasswordField password = new JPasswordField(28);
    private final JPasswordField confirmation = new JPasswordField(28);
    private final JComboBox<InvitationLifetime> lifetime =
            new JComboBox<>(InvitationLifetime.values());
    private final JTextField link = new JTextField(42);
    private final JButton create = new JButton("Create Invitation");
    private final JButton copy = new JButton("Copy Link");
    private final MessageField status = new MessageField("Choose a password and expiry.");

    public CreateTypeInvitationDialog(Window owner,
                                      String typeName,
                                      MysterType type,
                                      ServerCid bootstrap,
                                      TypeInvitationManager invitationManager) {
        super(owner, "Create Invitation — " + typeName, ModalityType.APPLICATION_MODAL);
        this.type = java.util.Objects.requireNonNull(type);
        this.bootstrap = java.util.Objects.requireNonNull(bootstrap);
        this.invitationManager = java.util.Objects.requireNonNull(invitationManager);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        lifetime.setSelectedItem(InvitationLifetime.SEVEN_DAYS);
        link.setEditable(false);
        copy.setEnabled(false);
        buildUi(typeName);
    }

    private void buildUi(String typeName) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        GridBagBuilder constraints
                = new GridBagBuilder()
                .withInsets(new Insets(4, 4, 4, 4))
                .withAnchor(GridBagConstraints.WEST);

        int row = 0;
        form.add(new JLabel("Type:"), constraints.withGridLoc(0, row));
        form.add(new JLabel(typeName), constraints.withGridLoc(1, row++));
        form.add(new JLabel("Password:"), constraints.withGridLoc(0, row));
        form.add(password, constraints.withGridLoc(1, row++).withWeight(1, 0)
                .withFill(GridBagConstraints.HORIZONTAL));
        form.add(new JLabel("Confirm:"), constraints.withGridLoc(0, row));
        form.add(confirmation, constraints.withGridLoc(1, row++).withWeight(1, 0)
                .withFill(GridBagConstraints.HORIZONTAL));
        form.add(new JLabel("Expires:"), constraints.withGridLoc(0, row));
        form.add(lifetime, constraints.withGridLoc(1, row++));
        form.add(new JLabel("Link:"), constraints.withGridLoc(0, row));
        form.add(link, constraints.withGridLoc(1, row++).withWeight(1, 0)
                .withFill(GridBagConstraints.HORIZONTAL));
        form.add(new JLabel("Share the password separately from the link."),
                constraints.withGridLoc(1, row++));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        create.addActionListener(_ -> createInvitation());
        copy.addActionListener(_ -> copyLink());
        close.addActionListener(_ -> dispose());
        buttons.add(create);
        buttons.add(copy);
        buttons.add(close);

        add(form, BorderLayout.CENTER);
        add(status, BorderLayout.NORTH);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(create);

        pack();

        setLocationRelativeTo(getOwner());
    }

    private void createInvitation() {
        char[] first = password.getPassword();
        char[] second = confirmation.getPassword();
        boolean passwordsMatch = Arrays.equals(first, second);
        Arrays.fill(second, '\0');
        if (first.length == 0 || !passwordsMatch) {
            status.sayError(first.length == 0
                    ? "Enter a non-empty invitation password."
                    : "The password entries do not match.");
            Arrays.fill(first, '\0');
            return;
        }

        create.setEnabled(false);
        lifetime.setEnabled(false);
        status.say("Creating invitation…");
        InvitationLifetime selected = (InvitationLifetime) lifetime.getSelectedItem();
        var operation = PromiseFutures.execute(() -> {
            try {
                return invitationManager.create(type, first, selected);
            } finally {
                Arrays.fill(first, '\0');
            }
        });

        operation.useEdt().addCallListener(new CallAdapter<>() {
            @Override
            public void handleResult(TypeInvitation invitation) {
                password.setText("");
                confirmation.setText("");
                link.setText(TypeJoinUri.create(type, bootstrap, invitation.invitationId()).toString());
                copy.setEnabled(true);
                status.say("Invitation saved. Copy the link and share its password separately.");
            }

            @Override
            public void handleException(Throwable exception) {
                create.setEnabled(true);
                lifetime.setEnabled(true);
                status.sayError("Could not create the invitation.");
            }
        });
    }

    private void copyLink() {
        if (link.getText().isEmpty()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(link.getText()), null);
        status.say("Invitation link copied.");
    }

    @Override
    public void dispose() {
        Arrays.fill(password.getPassword(), '\0');
        Arrays.fill(confirmation.getPassword(), '\0');
        password.setText("");
        confirmation.setText("");
        super.dispose();
    }
}
