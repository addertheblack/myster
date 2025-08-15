package com.myster.pref.ui;

import java.awt.Container;
import java.awt.GridBagLayout;
import java.util.ArrayList;

import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

import com.general.util.GridBagBuilder;
import com.general.util.Util;
import com.myster.ui.MysterFrame;

public class ThemePane extends PreferencesPanel {
    private static final String SEPARATOR = Util.SEPARATOR;
    
    public ThemePane() {
        setLayout(new GridBagLayout());

        var params = new GridBagBuilder();

        add(com.general.util.MessagePanel
                .createNew("Select the theme you want to use from the drop down box below. You will need to restart the application for the theme to take effect."),
            params.withGridLoc(0, 0)
                    .withSize(2, 1)
                    .withWeight(1.0, 0.0)
                    .withFill(GridBagBuilder.HORIZONTAL)
                    .withInsets(new java.awt.Insets(10, 0, 10, 10)));

        // Build a list of all currently built in LAFs
        var listOfThemes = new ArrayList<String>();
        listOfThemes.add("90s Metal");
        for (var lafInfo : javax.swing.UIManager.getInstalledLookAndFeels()) {
            listOfThemes.add(lafInfo.getName());
        }
        
        // Now add the choice box
        var themeChoice = new javax.swing.JComboBox<String>();
        Util.addSeparatorSupport(themeChoice);
        
        // Add built in
        for (var theme : listOfThemes) {
            themeChoice.addItem(theme);
        }
        
        // Add separator before FlatLaf themes
        themeChoice.addItem(SEPARATOR);
        
        // Add flat LAF core themes
        themeChoice.addItem("FlatLaf Light");
        themeChoice.addItem("FlatLaf Dark");
        themeChoice.addItem("FlatLaf IntelliJ");
        themeChoice.addItem("FlatLaf Darcula");

        // Material Themes
        themeChoice.addItem(SEPARATOR);
        themeChoice.addItem("FlatLaf Material Design Dark");
        themeChoice.addItem("FlatLaf Material Design Light");
        themeChoice.addItem("FlatLaf Material Deep Ocean");
        themeChoice.addItem("FlatLaf Material Oceanic");
        themeChoice.addItem("FlatLaf Material Palenight");
        themeChoice.addItem("FlatLaf Material Lighter");

        // Arc Themes
        themeChoice.addItem(SEPARATOR);
        themeChoice.addItem("FlatLaf Arc");
        themeChoice.addItem("FlatLaf Arc Dark");
        themeChoice.addItem("FlatLaf Arc Orange");
        themeChoice.addItem("FlatLaf Arc Dark Orange");

        // Atom Themes
        themeChoice.addItem(SEPARATOR);
        themeChoice.addItem("FlatLaf Atom One Dark");
        themeChoice.addItem("FlatLaf Atom One Light");

        // Carbon & Dracula
        themeChoice.addItem(SEPARATOR);
        themeChoice.addItem("FlatLaf Carbon");
        themeChoice.addItem("FlatLaf Dracula");

        // Flat Themes
        themeChoice.addItem(SEPARATOR);
        themeChoice.addItem("FlatLaf Dark Flat");
        themeChoice.addItem("FlatLaf Light Flat");

        // Github Themes
        themeChoice.addItem(SEPARATOR);
        themeChoice.addItem("FlatLaf Github");
        themeChoice.addItem("FlatLaf Github Dark");

        // Gradianto Themes
        themeChoice.addItem(SEPARATOR);
        themeChoice.addItem("FlatLaf Gradianto Dark Fuchsia");
        themeChoice.addItem("FlatLaf Gradianto Deep Ocean");
        themeChoice.addItem("FlatLaf Gradianto Midnight Blue");
        themeChoice.addItem("FlatLaf Gradianto Nature Green");

        // Gruvbox Themes
        themeChoice.addItem(SEPARATOR);
        themeChoice.addItem("FlatLaf Gruvbox Dark Hard");

        // Miscellaneous Dark Themes
        themeChoice.addItem(SEPARATOR);
        themeChoice.addItem("FlatLaf Cobalt 2");
        themeChoice.addItem("FlatLaf Cyan Light");
        themeChoice.addItem("FlatLaf Dark Purple");
        themeChoice.addItem("FlatLaf Hiberbee Dark");
        themeChoice.addItem("FlatLaf High Contrast");
        themeChoice.addItem("FlatLaf Monokai");
        themeChoice.addItem("FlatLaf Moonlight");
        themeChoice.addItem("FlatLaf Nord");
        themeChoice.addItem("FlatLaf One Dark");
        themeChoice.addItem("FlatLaf Spacegray");
        themeChoice.addItem("FlatLaf Solarized Dark");
        themeChoice.addItem("FlatLaf Solarized Light");
        themeChoice.addItem("FlatLaf Vuesion");
        themeChoice.addItem("FlatLaf XCode");
        
        
        add(themeChoice, params.withGridLoc(0, 1)
                .withSize(1, 1)
                .withWeight(0.0, 0.0)
                .withFill(GridBagBuilder.NONE)
                .withInsets(new java.awt.Insets(10, 10, 10, 10))
                .withAnchor(GridBagBuilder.WEST));
        
        // Add themeChoice listener to switch to the theme in question
        themeChoice.addActionListener(e -> {
            String selectedTheme = (String) themeChoice.getSelectedItem();
            try {
                
                // not euse Setup() not install which is deprecated
                switch (selectedTheme) {
                    case "90s Metal" -> {
                        MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme()); // Steel
                        javax.swing.UIManager.setLookAndFeel(new MetalLookAndFeel());
                    }
                    case "Metal" -> {
                        MetalLookAndFeel.setCurrentTheme(new OceanTheme());
                        javax.swing.UIManager.setLookAndFeel(new MetalLookAndFeel());
                    }
                    
                    // Core Themes
                    case "FlatLaf Light" -> com.formdev.flatlaf.FlatLightLaf.setup();
                    case "FlatLaf Dark" -> com.formdev.flatlaf.FlatDarkLaf.setup();
                    case "FlatLaf IntelliJ" -> com.formdev.flatlaf.FlatIntelliJLaf.setup();
                    case "FlatLaf Darcula" -> com.formdev.flatlaf.FlatDarculaLaf.setup();
                    
                    // Material Themes
                    case "FlatLaf Material Design Dark" -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme.setup();
                    case "FlatLaf Material Design Light" -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialLighterIJTheme.setup();
                    case "FlatLaf Material Deep Ocean" -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDeepOceanIJTheme.setup();
                    case "FlatLaf Material Oceanic" -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialOceanicIJTheme.setup();
                    case "FlatLaf Material Palenight" -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialPalenightIJTheme.setup();
                    case "FlatLaf Material Lighter" -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialLighterIJTheme.setup();

                    // Arc Themes
                    case "FlatLaf Arc" -> com.formdev.flatlaf.intellijthemes.FlatArcIJTheme.setup();
                    case "FlatLaf Arc Dark" -> com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme.setup();
                    case "FlatLaf Arc Orange" -> com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme.setup();
                    case "FlatLaf Arc Dark Orange" -> com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme.setup();

                    // Atom Themes
                    case "FlatLaf Atom One Dark" -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneDarkIJTheme.setup();
                    case "FlatLaf Atom One Light" -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneLightIJTheme.setup();

                    // Carbon & Dracula
                    case "FlatLaf Carbon" -> com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme.setup();
                    case "FlatLaf Dracula" -> com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme.setup();

                    // Flat Themes
                    case "FlatLaf Dark Flat" -> com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme.setup();
                    case "FlatLaf Light Flat" -> com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme.setup();

                    // Github Themes
                    case "FlatLaf Github" -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubIJTheme.setup();
                    case "FlatLaf Github Dark" -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubDarkIJTheme.setup();

                    // Gradianto Themes
                    case "FlatLaf Gradianto Dark Fuchsia" -> com.formdev.flatlaf.intellijthemes.FlatGradiantoDarkFuchsiaIJTheme.setup();
                    case "FlatLaf Gradianto Deep Ocean" -> com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme.setup();
                    case "FlatLaf Gradianto Midnight Blue" -> com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme.setup();
                    case "FlatLaf Gradianto Nature Green" -> com.formdev.flatlaf.intellijthemes.FlatGradiantoNatureGreenIJTheme.setup();

                    // Gruvbox Themes
                    case "FlatLaf Gruvbox Dark Hard" -> com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme.setup();

                    // Miscellaneous Themes
                    case "FlatLaf Cobalt 2" -> com.formdev.flatlaf.intellijthemes.FlatCobalt2IJTheme.setup();
                    case "FlatLaf Cyan Light" -> com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme.setup();
                    case "FlatLaf Dark Purple" -> com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme.setup();
                    case "FlatLaf Hiberbee Dark" -> com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme.setup();
                    case "FlatLaf High Contrast" -> com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme.setup();
                    case "FlatLaf Monokai" -> com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme.setup();
                    case "FlatLaf Moonlight" -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMoonlightIJTheme.setup();
                    case "FlatLaf Nord" -> com.formdev.flatlaf.intellijthemes.FlatNordIJTheme.setup();
                    case "FlatLaf One Dark" -> com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme.setup();
                    case "FlatLaf Spacegray" -> com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme.setup();
                    case "FlatLaf Solarized Dark" -> com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme.setup();
                    case "FlatLaf Solarized Light" -> com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme.setup();
                    case "FlatLaf Vuesion" -> com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme.setup();
                    case "FlatLaf XCode" -> com.formdev.flatlaf.intellijthemes.FlatXcodeDarkIJTheme.setup();
                    default -> {
                        for (var lafInfo : javax.swing.UIManager.getInstalledLookAndFeels()) {
                            if (lafInfo.getName().equals(selectedTheme)) {
                                javax.swing.UIManager.setLookAndFeel(lafInfo.getClassName());
                                break;
                            }
                        }
                    }
                }
                
                
                Container topLevelAncestor = this.getTopLevelAncestor();
                MysterFrame f = (MysterFrame) topLevelAncestor;
                var manager = f.getMysterFrameContext().windowManager();
                var mysterFrames = manager.getWindowListCopy();
                // Update all windows
                for (var frame : mysterFrames) {
                    javax.swing.SwingUtilities.updateComponentTreeUI(frame);
                    frame.invalidate();
                    frame.validate();
                    frame.repaint();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        // now add fill pane to  make sure components are moved to top
        add(new javax.swing.JPanel(), params.withGridLoc(0, 2)
                .withSize(1, 1)
                .withWeight(1.0, 1.0)
                .withFill(GridBagBuilder.BOTH)
                .withInsets(new java.awt.Insets(10, 10, 10, 10)));
    }



    @Override
    public void save() {
        
    }

    @Override
    public void reset() {
        
    }

    @Override
    public String getKey() {
        return "Themes";
    }
}