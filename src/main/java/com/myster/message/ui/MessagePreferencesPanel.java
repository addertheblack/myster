
package com.myster.message.ui;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Optional;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.myster.message.MessageManager;
import com.myster.pref.MysterPreferences;
import com.myster.pref.ui.PreferencesPanel;

public class MessagePreferencesPanel extends PreferencesPanel {
    private final JCheckBox refuseMessages;
    private final JLabel denyMessageLabel;
    private final JTextField denyMessageText;
    private final MysterPreferences preferences;

    public MessagePreferencesPanel(MysterPreferences preferences) {
        this.preferences = preferences;
        setLayout(null);

        refuseMessages = new JCheckBox("Refuse Messages");
        refuseMessages.setSize(150, 25);
        refuseMessages.setLocation(10, 25);
        refuseMessages.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                updateEnabled();
            }
        });
        add(refuseMessages);

        denyMessageLabel = new JLabel("Refusal Message:");
        denyMessageLabel.setSize(150, 25);
        denyMessageLabel.setLocation(20, 50);
        add(denyMessageLabel);

        denyMessageText = new JTextField("");
        denyMessageText.setSize(400, 25);
        denyMessageText.setLocation(25, 75);
        add(denyMessageText);

        setSize(STD_XSIZE, STD_YSIZE);
    }

    public void save() {
        String text = denyMessageText.getText();
        if (text.equals("") || text.length() > 255)
            text = ""; //MML can't take "", 255 is
        // arbitrary limit, real limit
        // is about 60k
        denyMessageText.setText(text);

        MessageManager.setPrefs(preferences,
                                text.equals("") ? Optional.empty() : Optional.of(text),
                                refuseMessages.isSelected());
    }

    //discard changes and reset values to their defaults.
    public void reset() {
        denyMessageText.setText(MessageManager.getRefusingMessage(preferences));

        refuseMessages.setSelected(MessageManager.isRefusingMessages(preferences));

        updateEnabled(); //cheese
    }

    private void updateEnabled() {
        if (refuseMessages.isSelected()) {
            denyMessageLabel.setEnabled(true);
            denyMessageText.setEnabled(true);
        } else {
            denyMessageLabel.setEnabled(false);
            denyMessageText.setEnabled(false);
        }
    }

    public String getKey() {
        return "Messages";
    }//gets the key structure for the place in the pref panel
}