package com.myster.pref.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GridBagLayout;

import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import com.general.util.GridBagBuilder;
import com.general.util.Util;
import com.myster.pref.MysterPreferences;
import com.myster.ui.MysterFrame;
import com.myster.util.ThemeUtil;

public class ThemePane extends PreferencesPanel {
    private static final String SEPARATOR = Util.SEPARATOR;
    
    private final MysterPreferences preferences;
    private JComboBox<String> themeChoice; // Add this field at class level
    
    public ThemePane(MysterPreferences preferences) {
        this.preferences = preferences;
        setLayout(new GridBagLayout());

        var params = new GridBagBuilder();

        add(com.general.util.MessagePanel
                .createNew("Select the theme you want to use from the drop down box below. "
                        + "You will need to restart the application for the theme to take effect. "
                        + "These themes are provided for entertainment purposes and might not work flawlessly. "
                        + "The recommended theme is listed in bold."),
            params.withGridLoc(0, 0)
                    .withSize(2, 1)
                    .withWeight(1.0, 0.0)
                    .withFill(GridBagBuilder.HORIZONTAL)
                    .withInsets(new java.awt.Insets(10, 0, 10, 10)));

        // Now add the choice box
        themeChoice = new javax.swing.JComboBox<String>();
        Util.addSeparatorSupport(themeChoice);
        addBoldDefaultThemeSupport(themeChoice);
        
        // Add all themes in display order from ThemeUtil
        for (var theme : ThemeUtil.getThemesInDisplayOrder()) {
            if (theme.isSeparator()) {
                themeChoice.addItem(Util.SEPARATOR);
            } else {
                themeChoice.addItem(theme.friendlyName());
            }
        }
        
        add(themeChoice, params.withGridLoc(0, 1)
                .withSize(1, 1)
                .withWeight(0.0, 0.0)
                .withFill(GridBagBuilder.NONE)
                .withInsets(new java.awt.Insets(10, 10, 10, 10))
                .withAnchor(GridBagBuilder.WEST));
        
        // Add themeChoice listener to switch to the theme in question
        themeChoice.addActionListener(e -> {
            String selectedTheme = (String) themeChoice.getSelectedItem();
            applyTheme(selectedTheme);
        });
        
        // now add fill pane to  make sure components are moved to top
        add(new javax.swing.JPanel(), params.withGridLoc(0, 2)
                .withSize(1, 1)
                .withWeight(1.0, 1.0)
                .withFill(GridBagBuilder.BOTH)
                .withInsets(new java.awt.Insets(10, 10, 10, 10)));
    }



    private void addBoldDefaultThemeSupport(JComboBox<String> box) {
        // Capture the original renderer
        final ListCellRenderer<String> originalRenderer = ((ListCellRenderer<String>) box.getRenderer());
        String fullyQualifiedName = ThemeUtil.findDefaultThemeFullyQualifiedName();
        
        // Create a new renderer that wraps the original one
        box.setRenderer(new ListCellRenderer<String>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends String> list, String value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                Component c = originalRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                if (value != null && value.equals(ThemeUtil.getFriendlyName(fullyQualifiedName))) {
                    if (c instanceof javax.swing.JLabel) {
                        javax.swing.JLabel label = (javax.swing.JLabel) c;
                        // Create a bold version of the current font to maintain the theme's font family
                        Font font = label.getFont();
                        if (font.isBold()) {
                            label.setFont(font.deriveFont(java.awt.Font.PLAIN));
                        } else {
                            label.setFont(font.deriveFont(java.awt.Font.BOLD));
                        }
                    }
                }
                
                return c;
            }
        });        
    }



    private void applyTheme(String selectedTheme) {
        if (selectedTheme == null || selectedTheme.equals(SEPARATOR)) {
            return;
        }
        
        try {
            ThemeUtil.applyTheme(selectedTheme);
            
            // Update all windows
            Container topLevelAncestor = this.getTopLevelAncestor();
            MysterFrame f = (MysterFrame) topLevelAncestor;
            var manager = f.getMysterFrameContext().windowManager();
            var mysterFrames = manager.getWindowListCopy();
            
            for (var frame : mysterFrames) {
                javax.swing.SwingUtilities.updateComponentTreeUI(frame);
                frame.invalidate();
                frame.validate();
                frame.repaint();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }



    @Override
    public void save() {
        String selectedTheme = (String) themeChoice.getSelectedItem();
        if (selectedTheme == null || selectedTheme.equals(Util.SEPARATOR)) {
            return;
        }
        
        String defaultThemeClass = ThemeUtil.findDefaultThemeFullyQualifiedName();
        String defaultThemeFriendly = ThemeUtil.getFriendlyName(defaultThemeClass);
        
        if (selectedTheme.equals(defaultThemeFriendly)) {
            // If it's the default theme, remove the preference
            preferences.remove(ThemeUtil.THEME_NAME_PREF_KEY);
        } else {
            // Store the fully qualified class name
            String className = ThemeUtil.getClassName(selectedTheme);
            preferences.put(ThemeUtil.THEME_NAME_PREF_KEY, className);
        }
    }

    @Override
    public void reset() {
        // Get the saved theme class name and apply it
        String savedThemeClass = preferences.get(ThemeUtil.THEME_NAME_PREF_KEY, ThemeUtil.findDefaultThemeFullyQualifiedName());
        String friendlyName = ThemeUtil.getFriendlyName(savedThemeClass);
        
        // Find and select the saved theme in the combo box
        for (int i = 0; i < themeChoice.getItemCount(); i++) {
            String item = themeChoice.getItemAt(i);
            if (item != null && !item.equals(Util.SEPARATOR) && item.equals(friendlyName)) {
                themeChoice.setSelectedIndex(i);
                break;
            }
        }
        
        try {
            ThemeUtil.applyThemeFromPreferences(preferences);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public String getKey() {
        return "Themes";
    }
}