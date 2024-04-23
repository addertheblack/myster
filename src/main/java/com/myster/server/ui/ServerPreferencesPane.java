
package com.myster.server.ui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import com.myster.pref.MysterPreferences;
import com.myster.pref.ui.PreferencesPanel;
import com.myster.server.ServerPreferences;

public class ServerPreferencesPane extends PreferencesPanel {
    private final JTextField serverIdentityField;
    private final JLabel serverIdentityLabel;
    private final JComboBox<String> openSlotChoice;
    private final JLabel openSlotLabel;
    private final JSpinner serverThreadsChoice;
    private final JLabel serverThreadsLabel;
    private final JLabel spacerLabel;
    private final ServerPreferences preferences;

    private final FreeLoaderPref leech;

    public ServerPreferencesPane(ServerPreferences preferences) {
        this.preferences = preferences;
        
        setLayout(new GridLayout(5, 2, 5, 5));

        openSlotLabel = new JLabel("Download Spots:");
        add(openSlotLabel);

        openSlotChoice = new JComboBox<String>();
        for (int i = 2; i <= 10; i++) {
            openSlotChoice.addItem("" + i);
        }
        add(openSlotChoice);

        serverThreadsLabel = new JLabel("Server Port:");
        add(serverThreadsLabel);

        var spinnerNumberModel = new SpinnerNumberModel();
        spinnerNumberModel.setMinimum(1024);
        spinnerNumberModel.setMaximum((int)Math.pow(2, 16) - 1);
        spinnerNumberModel.setValue(preferences.getServerPort());
        serverThreadsChoice = new JSpinner(spinnerNumberModel);
        ((JSpinner.DefaultEditor) serverThreadsChoice.getEditor()).getTextField().setEditable(true);
        add(serverThreadsChoice);

        serverIdentityLabel = new JLabel("Server Name:");
        add(serverIdentityLabel);

        serverIdentityField = new JTextField();
        add(serverIdentityField);

        spacerLabel = new JLabel();
        add(spacerLabel);

        leech = new FreeLoaderPref();
        add(leech);

        reset();
    }

    public Dimension getPreferredSize() {
        return new Dimension(STD_XSIZE, 140);
    }

    public String getKey() {
        return "Server";
    }

    public void save() {
        preferences.setIdentityName(serverIdentityField.getText());
        preferences.setDownloadSlots(Integer.parseInt((String) openSlotChoice.getSelectedItem()));
        preferences.setPort((int) serverThreadsChoice.getModel().getValue());
        leech.save();
    }

    public void reset() {
        serverIdentityField.setText(preferences.getIdentityName());
        openSlotChoice.setSelectedItem("" + preferences.getDownloadSlots());
        serverThreadsChoice.getModel().setValue(preferences.getServerPort());
        leech.reset();
    }

    public static class FreeLoaderPref extends JPanel {
        private final JCheckBox freeloaderCheckbox;

        public FreeLoaderPref() {
            setLayout(new FlowLayout());

            freeloaderCheckbox = new JCheckBox("Kick Freeloaders");
            add(freeloaderCheckbox);
        }

        public void save() {
            setKickFreeloaders(freeloaderCheckbox.isSelected());
        }

        public void reset() {
            freeloaderCheckbox.setSelected(kickFreeloaders());
        }

        public Dimension getPreferredSize() {
            return new Dimension(100, 1);
        }
        
        private static String freeloadKey = "ServerFreeloaderKey/";

        public static boolean kickFreeloaders() {
            boolean b_temp = false;

            try {
                b_temp = Boolean
                        .valueOf(MysterPreferences.getInstance().get(freeloadKey))
                        .booleanValue();
            } catch (NumberFormatException ex) {
                //nothing
            } catch (NullPointerException ex) {
                //nothing
            }
            return b_temp;
        }

        private static void setKickFreeloaders(boolean b) {
            MysterPreferences.getInstance().put(freeloadKey, "" + b);
        }
    }
}