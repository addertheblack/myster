package com.myster.server.ui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.BiConsumer;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import com.general.util.GridBagBuilder;
import com.myster.net.ServerDescriptionUtils;
import com.myster.net.server.ServerPreferences;
import com.myster.pref.ui.PreferencesPanel;

public class ServerPreferencesPane extends PreferencesPanel {
    private final JTextField serverIdentityField;
    private final JTextField serverDescriptionField;
    private final JLabel serverIdentityLabel;
    private final JComboBox<String> openSlotChoice;
    private final JLabel openSlotLabel;
    private final JSpinner serverThreadsChoice;
    private final JLabel serverThreadsLabel;
    private final JLabel spacerLabel;
    private final ServerPreferences preferences;
    private Runnable onServerNameChanged; // Callback when server name changes
    private BiConsumer<Integer, Integer> onServerPortChanged; // Callback when server port changes (oldPort, newPort)

    private final FreeLoaderPref leech;
    
    private final int DISTANCE_BETWEEN_COLUMNS = 15;

    public ServerPreferencesPane(ServerPreferences preferences) {
        this.preferences = preferences;
        
        setLayout(new GridBagLayout());
        var constraints = new GridBagBuilder();

        openSlotLabel = new JLabel("Download Spots:");
        add(openSlotLabel, constraints.withAnchor(GridBagConstraints.WEST).withInsets(new Insets(5, 0, 0, 0)));

        openSlotChoice = new JComboBox<String>();
        for (int i = 2; i <= 10; i++) {
            openSlotChoice.addItem("" + i);
        }
        add(openSlotChoice,
            constraints.withGridLoc(1, 0)
                    .withAnchor(GridBagConstraints.WEST)
                    .withInsets(new Insets(5, DISTANCE_BETWEEN_COLUMNS, 0, 0)));

        serverThreadsLabel = new JLabel("Server Port:");
        add(serverThreadsLabel,
            constraints.withGridLoc(0, 1)
                    .withAnchor(GridBagConstraints.WEST)
                    .withInsets(new Insets(5, 0, 0, 0)));

        var spinnerNumberModel = new SpinnerNumberModel();
        spinnerNumberModel.setMinimum(1024);
        spinnerNumberModel.setMaximum((int) Math.pow(2, 16) - 1);
        spinnerNumberModel.setValue(preferences.getServerPort());
        serverThreadsChoice = new JSpinner(spinnerNumberModel);
        ((JSpinner.DefaultEditor) serverThreadsChoice.getEditor()).getTextField().setEditable(true);
        add(serverThreadsChoice,
            constraints.withGridLoc(1, 1)
                    .withAnchor(GridBagConstraints.WEST)
                    .withInsets(new Insets(5, DISTANCE_BETWEEN_COLUMNS, 0, 0)));

        serverIdentityLabel = new JLabel("Server Name:");
        add(serverIdentityLabel,
            constraints.withGridLoc(0, 2)
                    .withAnchor(GridBagConstraints.WEST)
                    .withInsets(new Insets(5, 0, 0, 0)));

        serverIdentityField = new JTextField(25);
        add(serverIdentityField,
            constraints.withGridLoc(1, 2)
                    .withAnchor(GridBagConstraints.WEST)
                    .withInsets(new Insets(5, DISTANCE_BETWEEN_COLUMNS, 0, 0)));

        JLabel descriptionLabel = new JLabel("Description:");
        add(descriptionLabel,
            constraints.withGridLoc(0, 3)
                    .withAnchor(GridBagConstraints.WEST)
                    .withInsets(new Insets(5, 0, 0, 0)));

        serverDescriptionField = new JTextField(25);
        serverDescriptionField.setToolTipText("Server description (maximum 255 characters)");
        descriptionLabel.setLabelFor(serverDescriptionField);
        ((AbstractDocument) serverDescriptionField.getDocument())
                .setDocumentFilter(new DescriptionLengthFilter());
        add(serverDescriptionField,
            constraints.withGridLoc(1, 3)
                    .withAnchor(GridBagConstraints.WEST)
                    .withInsets(new Insets(5, DISTANCE_BETWEEN_COLUMNS, 0, 0)));

        spacerLabel = new JLabel(" ");
        add(spacerLabel,
            constraints.withGridLoc(0, 4)
                    .withAnchor(GridBagConstraints.WEST)
                    .withInsets(new Insets(5, 0, 0, 0)));

        leech = new FreeLoaderPref();
        add(leech,
            constraints.withGridLoc(1, 4)
                    .withAnchor(GridBagConstraints.WEST)
                    .withInsets(new Insets(5, DISTANCE_BETWEEN_COLUMNS, 0, 0)));

        add(new JPanel(), constraints.withGridLoc(0, 5).withWeight(1, 1).withSize(3, 1));

        add(new JPanel(), constraints.withGridLoc(2, 0).withWeight(1, 0).withSize(1, 5));

        reset();
    }
    
    /**
     * Sets a callback to be invoked when the server name is changed and saved.
     * 
     * @param callback the callback to invoke
     */
    public void setOnServerNameChanged(Runnable callback) {
        this.onServerNameChanged = callback;
    }

    /**
     * Sets a callback to be invoked when the server port is changed and saved.
     * The callback receives both the old port and the new port.
     *
     * @param callback the callback to invoke with (oldPort, newPort)
     */
    public void setOnServerPortChanged(BiConsumer<Integer, Integer> callback) {
        this.onServerPortChanged = callback;
    }

    public Dimension getPreferredSize() {
        return new Dimension(STD_XSIZE, 170);
    }

    public String getKey() {
        return "Server";
    }

    public void save() {
        String oldName = preferences.getIdentityName();
        String newName = serverIdentityField.getText();
        int oldPort = preferences.getServerPort();
        int newPort = (int) serverThreadsChoice.getModel().getValue();

        preferences.setIdentityName(newName);
        preferences.setDescription(serverDescriptionField.getText());
        preferences.setDownloadSlots(Integer.parseInt((String) openSlotChoice.getSelectedItem()));
        preferences.setPort(newPort);
        preferences.setKickFreeloaders(leech.isSet());
        
        // Notify if server name changed
        if (!oldName.equals(newName) && onServerNameChanged != null) {
            onServerNameChanged.run();
        }

        // Notify if server port changed
        if (oldPort != newPort && onServerPortChanged != null) {
            onServerPortChanged.accept(oldPort, newPort);
        }
    }

    public void reset() {
        serverIdentityField.setText(preferences.getIdentityName());
        serverDescriptionField.setText(preferences.getDescription());
        openSlotChoice.setSelectedItem("" + preferences.getDownloadSlots());
        serverThreadsChoice.getModel().setValue(preferences.getServerPort());
        leech.reset(preferences.isKickFreeloaders());
    }

    private static class DescriptionLengthFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes) throws
                BadLocationException {
            replace(bypass, offset, 0, text, attributes);
        }

        @Override
        public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes) throws
                BadLocationException {
            String current = bypass.getDocument().getText(0, bypass.getDocument().getLength());
            int retainedLength = current.codePointCount(0, offset) + current.codePointCount(offset + length,
                                                                                            current.length());
            int available = ServerDescriptionUtils.MAX_LENGTH - retainedLength;
            String replacement = text == null ? "" : text;
            int count = Math.min(available, replacement.codePointCount(0, replacement.length()));
            bypass.replace(offset,
                           length,
                           replacement.substring(0, replacement.offsetByCodePoints(0, count)),
                           attributes);
        }
    }

    public static class FreeLoaderPref extends JPanel {
        private final JCheckBox freeloaderCheckbox;

        public FreeLoaderPref() {
            setLayout(new FlowLayout());

            freeloaderCheckbox = new JCheckBox("Kick Freeloaders");
            add(freeloaderCheckbox);
        }

        public boolean isSet() {
            return freeloaderCheckbox.isSelected();
        }

        public void reset(boolean b) {
            freeloaderCheckbox.setSelected(b);
        }
    }
}
