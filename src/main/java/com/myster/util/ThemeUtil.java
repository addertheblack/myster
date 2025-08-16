package com.myster.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.UIManager;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

import com.general.util.Util;
import com.myster.pref.MysterPreferences;

public class ThemeUtil {
    private static final Logger LOGGER = Logger.getLogger(ThemeUtil.class.getName());
    
    // Themes in UI display order, including separators
    private static final List<ThemeInfo> THEMES_WITH_SEPARATORS = createThemesList();
    
    // Filtered list for lookups (no separators)
    private static final List<ThemeInfo> THEMES = THEMES_WITH_SEPARATORS.stream()
        .filter(t -> !t.isSeparator())
        .toList();
    
    // Create maps for quick lookups (automatically excludes separators since they have null names)
    private static final Map<String, ThemeInfo> FRIENDLY_NAME_MAP = THEMES.stream()
        .collect(Collectors.toMap(ThemeInfo::friendlyName, t -> t));
    
    private static final Map<String, ThemeInfo> CLASS_NAME_MAP = THEMES.stream()
        .collect(Collectors.toMap(ThemeInfo::className, t -> t));
    
    public static List<ThemeInfo> getThemesInDisplayOrder() {
        return THEMES_WITH_SEPARATORS;
    }
    
    public static class ThemeApplicationException extends RuntimeException {
        public ThemeApplicationException(Exception ex) {
            super(ex);
        }
    }

    private interface Throwing {
        public void run() throws Exception;
    }

    private static void run(Throwing r) {
        try {
            r.run();
        } catch (Exception ex) {
            throw new ThemeApplicationException(ex);
        }
    }

    private static ThemeApplier wrap(Throwing r) {
        return () -> run(r);
    }

    public record ThemeInfo(
        String friendlyName,
        String className,
        ThemeApplier applier
    ) {
        // Constructor for separators
        public static ThemeInfo separator() {
            return new ThemeInfo(null, null, null);
        }
        
        public boolean isSeparator() {
            return friendlyName == null;
        }
    }
    
    @FunctionalInterface
    public interface ThemeApplier {
        void apply();
    }
    
    private static List<ThemeInfo> createThemesList() {
        var builder = new ArrayList<ThemeInfo>();
        
        // Add built-in system L&Fs (except Metal which we handle separately)
        for (var lafInfo : UIManager.getInstalledLookAndFeels()) {
            // Skip Metal L&Fs as we handle them separately
            if (!lafInfo.getClassName().contains("MetalLookAndFeel")) {
                builder.add(new ThemeInfo(
                    lafInfo.getName(),
                    lafInfo.getClassName(),
                    wrap(() -> UIManager.setLookAndFeel(lafInfo.getClassName()))
                ));
            }
        }
        
        // Add separator before our custom Metal themes
        builder.add(ThemeInfo.separator());
        
        // Built-in Java themes with custom Metal themes
        builder.add(new ThemeInfo(
            "90s Metal",
            "javax.swing.plaf.metal.MetalLookAndFeel.classic",
            wrap(() -> {
                MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
                UIManager.setLookAndFeel(new MetalLookAndFeel());
            })
        ));
        builder.add(new ThemeInfo(
            "Metal",
            "javax.swing.plaf.metal.MetalLookAndFeel.ocean",
            wrap(() -> {
                MetalLookAndFeel.setCurrentTheme(new OceanTheme());
                UIManager.setLookAndFeel(new MetalLookAndFeel());
            })
        ));
        
        builder.add(ThemeInfo.separator());

        // Core FlatLaf Themes
        builder.add(new ThemeInfo("FlatLaf Light",
                                  "com.formdev.flatlaf.FlatLightLaf",
                                  () -> com.formdev.flatlaf.FlatLightLaf.setup()));
        builder.add(new ThemeInfo("FlatLaf Dark",
                                  "com.formdev.flatlaf.FlatDarkLaf",
                                  () -> com.formdev.flatlaf.FlatDarkLaf.setup()));
        builder.add(new ThemeInfo("FlatLaf IntelliJ",
                                  "com.formdev.flatlaf.FlatIntelliJLaf",
                                  () -> com.formdev.flatlaf.FlatIntelliJLaf.setup()));
        builder.add(new ThemeInfo("FlatLaf Darcula",
                                  "com.formdev.flatlaf.FlatDarculaLaf",
                                  () -> com.formdev.flatlaf.FlatDarculaLaf.setup()));
        builder.add(ThemeInfo.separator());

        // Material Themes
        builder.add(new ThemeInfo("FlatLaf Material Design Dark",
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Material Design Light",
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialLighterIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialLighterIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Material Deep Ocean",
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDeepOceanIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDeepOceanIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Material Oceanic",
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialOceanicIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialOceanicIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Material Palenight",
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialPalenightIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialPalenightIJTheme.setup()
        ));
        builder.add(ThemeInfo.separator());
        
        // Arc Themes
        builder.add(new ThemeInfo(
            "FlatLaf Arc",
            "com.formdev.flatlaf.intellijthemes.FlatArcIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatArcIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Arc Dark",
            "com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Arc Orange",
            "com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Arc Dark Orange",
            "com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme.setup()
        ));
        builder.add(ThemeInfo.separator());
        
        // Atom Themes
        builder.add(new ThemeInfo(
            "FlatLaf Atom One Dark",
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneDarkIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneDarkIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Atom One Light",
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneLightIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneLightIJTheme.setup()
        ));
        builder.add(ThemeInfo.separator());
        
        // Carbon & Dracula
        builder.add(new ThemeInfo(
            "FlatLaf Carbon",
            "com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Dracula",
            "com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme.setup()
        ));
        builder.add(ThemeInfo.separator());
        
        // Flat Themes
        builder.add(new ThemeInfo(
            "FlatLaf Dark Flat",
            "com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Light Flat",
            "com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme.setup()
        ));
        builder.add(ThemeInfo.separator());
        
        // Github Themes
        builder.add(new ThemeInfo(
            "FlatLaf Github",
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Github Dark",
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubDarkIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubDarkIJTheme.setup()
        ));
        builder.add(ThemeInfo.separator());
        
        // Gradianto Themes
        builder.add(new ThemeInfo(
            "FlatLaf Gradianto",
            "com.formdev.flatlaf.intellijthemes.FlatGrayIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatGrayIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Gradianto Deep Ocean",
            "com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Gradianto Midnight Blue",
            "com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Gradianto Nature Green",
            "com.formdev.flatlaf.intellijthemes.FlatGradiantoNatureGreenIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatGradiantoNatureGreenIJTheme.setup()
        ));
        builder.add(ThemeInfo.separator());
        
        // Miscellaneous Dark Themes
        builder.add(new ThemeInfo(
            "FlatLaf Hiberbee Dark",
            "com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf High Contrast",
            "com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Monokai Pro",
            "com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Moonlight",
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMoonlightIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMoonlightIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Nord",
            "com.formdev.flatlaf.intellijthemes.FlatNordIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatNordIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf One Dark",
            "com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Spacegray",
            "com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Solarized Dark",
            "com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Solarized Light",
            "com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf Vuesion",
            "com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme.setup()
        ));
        builder.add(new ThemeInfo(
            "FlatLaf XCode",
            "com.formdev.flatlaf.intellijthemes.FlatXcodeDarkIJTheme",
            () -> com.formdev.flatlaf.intellijthemes.FlatXcodeDarkIJTheme.setup()
        ));
        
        return List.copyOf(builder);  // Make immutable
    }
    
    public static String findDefaultThemeFullyQualifiedName() {
        for (var info : UIManager.getInstalledLookAndFeels()) {
            LOGGER.info("Installed Look and Feel: " + info.getName() + " - " + info.getClassName());
        }
        
        String systemLookAndFeelClassName = UIManager.getSystemLookAndFeelClassName();
        LOGGER.info("System Look and feel: " + systemLookAndFeelClassName);
        
        // If dark mode is detected, use FlatLaf Dark regardless of platform
        if (Util.isSystemDarkTheme()) {
            LOGGER.info("System is using dark theme, using FlatDarkLaf");
            systemLookAndFeelClassName = "com.formdev.flatlaf.FlatDarkLaf";
        } else if (systemLookAndFeelClassName.equals("javax.swing.plaf.metal.MetalLookAndFeel")) {
            // Metal look and feel is ugly, use FlatLaf Light instead
            LOGGER.info("MetalLookAndFeel detected, using FlatLightLaf instead");
            systemLookAndFeelClassName = "com.formdev.flatlaf.FlatLightLaf";
        }
        return systemLookAndFeelClassName;
    }
    
    public static String getFriendlyName(String className) {
        return CLASS_NAME_MAP.containsKey(className) 
            ? CLASS_NAME_MAP.get(className).friendlyName() 
            : className;
    }
    
    public static String getClassName(String friendlyName) {
        return FRIENDLY_NAME_MAP.containsKey(friendlyName) 
            ? FRIENDLY_NAME_MAP.get(friendlyName).className() 
            : friendlyName;
    }
    
    public static void applyTheme(String friendlyName)  {
        var themeInfo = FRIENDLY_NAME_MAP.get(friendlyName);
        if (themeInfo != null) {
            themeInfo.applier().apply();
        } else {
            // Handle system L&Fs that aren't in our list
            for (var lafInfo : UIManager.getInstalledLookAndFeels()) {
                if (lafInfo.getName().equals(friendlyName)) {
                    run(() -> UIManager.setLookAndFeel(lafInfo.getClassName()));
                    break;
                }
            }
        }
    }
    
    public static final String THEME_NAME_PREF_KEY = "Theme Name";
    
    public static void applyThemeFromPreferences(MysterPreferences preferences) {
        String savedThemeClass = preferences.get(THEME_NAME_PREF_KEY, findDefaultThemeFullyQualifiedName());
        String friendlyName = getFriendlyName(savedThemeClass);
        applyTheme(friendlyName);
    }
}