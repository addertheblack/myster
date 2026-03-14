/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.filemanager.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.general.util.GridBagBuilder;
import com.general.util.IconLoader;
import com.general.util.MessageField;
import com.general.util.Timer;
import com.myster.filemanager.FileTypeListManager;
import com.myster.pref.ui.PreferencesPanel;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;
import com.myster.util.TypeChoice;

/**
 * The FMICHooser is the FileManagerInterfaceChooser. It's the GUI for the
 * FileManager prefs panel. It's built to be as independent from the internal
 * working of the FileManager, despite having access to the sweat, sweat inners.
 * <p>
 * When the selected type is public (non-members can see shared files), a yellow
 * warning banner is shown below the type selector to make the sharing scope visible.
 */

public class FmiChooser extends PreferencesPanel {
    private static final Logger log = Logger.getLogger(PreferencesPanel.class.getName());
	private static final int XPAD = 10;
	private static final int MAX_PATH_LABEL_SIZE = STD_XSIZE - 100 - 3 * XPAD - 5;
	
    private final FileTypeListManager manager;
    private final TypeChoice choice;
    private final JLabel publicWarningLabel;
    private final JCheckBox checkbox;
    private final JButton button;
    private final JLabel pathLabel;
    private final JLabel folderl, filelistl;
    private final JList<String> fList;
    private final JButton setAllButton;
    private final Map<MysterType, SettingsStruct> hash = new HashMap<>();
    private final DefaultListModel<String> fListModel;

    private Timer timer = null;
    private String path;
    private final TitledBorder titledBorder;

    public FmiChooser(FileTypeListManager manager, TypeDescriptionList tdList) {
        this.manager = manager;
        setLayout(new GridBagLayout());

        GridBagBuilder outterGbc = new GridBagBuilder();
        outterGbc = outterGbc.withSize(1, 1);

        titledBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.gray));

        choice = new TypeChoice(tdList, false);
        choice.addItemListener((ItemEvent _) -> {
            restoreState();
            titledBorder.setTitle(choice.getSelectedDescription());
            updateWarningBanner();
        });
        add(choice,
            outterGbc.withGridLoc(0, 0)
                     .withFill(GridBagConstraints.BOTH)
                     .withWeight(1, 0)
                     .withInsets(new Insets(5, 0, 5, 0)));
        titledBorder.setTitle(choice.getSelectedDescription());

        publicWarningLabel = new JLabel();
        publicWarningLabel.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        publicWarningLabel.setVisible(false);
        add(publicWarningLabel,
            outterGbc.withGridLoc(0, 1)
                     .withSize(2, 1)
                     .withWeight(1, 0)
                     .withFill(GridBagConstraints.HORIZONTAL)
                     .withInsets(new Insets(0, 4, 4, 4)));

        setAllButton = new JButton("Set all paths to this path");
        setAllButton.addActionListener((ActionEvent _) -> {
            String newPath = path;

            for (int i = 0; i < choice.getItemCount(); i++) {

                // Figure out what the bool.. should be (hack)
                Object o = hash.get(choice.getType(i).get());
                boolean bool_temp;
                if (o != null) {
                    bool_temp = ((SettingsStruct) (o)).shared;
                } else {
                    bool_temp = FmiChooser.this.manager.isShared(choice.getType(i).get());
                } // end

                hash.put(choice.getType(i).get(),
                         new SettingsStruct(choice.getType(i).get(), newPath, bool_temp));
            }
        });
        add(setAllButton, outterGbc.withGridLoc(1, 0).withInsets(new Insets(5, 5, 5, 5)));

        path = manager.getPathFromType(choice.getType().get());
        
        JPanel panel = new JPanel();
        
        panel.setLayout(new GridBagLayout());
        var innerGbc = new GridBagBuilder();
        
        panel.setBorder(titledBorder);

        checkbox = new JCheckBox("Share this type", manager.isShared(choice.getType().get()));
        checkbox.addItemListener((ItemEvent _) -> {
            hash.put(choice.getType().get(),
                     new SettingsStruct(choice.getType().get(), path, checkbox.isSelected()));
        });
        panel.add(checkbox,
                  innerGbc.withGridLoc(0, 0)
                          .withSize(2, 1)
                          .withWeight(1, 0)
                          .withAnchor(GridBagConstraints.WEST));

        button = new JButton("Choose Folder");
        button.addActionListener((ActionEvent _) -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setDialogTitle("Choose a directory");

            int returnValue =
                    fileChooser.showOpenDialog(SwingUtilities.getWindowAncestor(FmiChooser.this));
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedDirectory = fileChooser.getSelectedFile();
                path = selectedDirectory.getAbsolutePath();

                // Your existing logic after selecting the directory
                setPathLabel(path);
                // Assuming 'hash' and 'choice' are accessible here
                hash.put(choice.getType().get(),
                         new SettingsStruct(choice.getType().get(), path, checkbox.isSelected()));
            } else {
                log.info("User cancelled the action.");
            }
        });
        panel.add(button, innerGbc.withGridLoc(2, 0).withInsets(new Insets(5, 0, 0, 5)));

        folderl = new JLabel("Shared Folder:");
        panel.add(folderl, innerGbc.withGridLoc(0, 1).withInsets(new Insets(5, 5, 0, 5)));

        // choice must be created first
        pathLabel = new JLabel();
        // textfeild.setEditable(false);
        setPathLabel(manager.getPathFromType(choice.getType().get()));
        panel.add(pathLabel,
                  innerGbc.withGridLoc(1, 1)
                          .withSize(2, 1)
                          .withWeight(1, 0)
                          .withAnchor(GridBagConstraints.EAST)
                          .withInsets(new Insets(5, 5, 0, 5)));

        filelistl = new JLabel("Shared Files (click \"Apply\" to see changes) :");
        panel.add(filelistl,
                  innerGbc.withGridLoc(0, 2)
                          .withSize(3, 1)
                          .withWeight(1, 0)
                          .withAnchor(GridBagConstraints.WEST)
                          .withInsets(new Insets(5, 5, 0, 5)));

        fList = new JList<>();
        fListModel = new DefaultListModel<>();
        fList.setModel(fListModel);
        JScrollPane fListScrollPane = new JScrollPane(fList);
        fListScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        fListScrollPane.setPreferredSize(new Dimension(1, 1));
        panel.add(fListScrollPane,
                  innerGbc.withGridLoc(0, 3)
                          .withFill(GridBagConstraints.BOTH)
                          .withWeight(1, 1)
                          .withSize(3, 1)
                          .withInsets(new Insets(5, 5, 5, 5)));

        add(panel,
            outterGbc.withGridLoc(0, 2)
                    .withSize(2, 1)
                    .withFill(GridBagConstraints.BOTH)
                    .withWeight(1, 1)
                    .withInsets(new Insets(5, 0, 0, 5)));

        repaint();
        reset();
        restoreState();
        updateWarningBanner();
    }

    private void setPathLabel(String newPath) {
        pathLabel.setText(newPath);
        for (int i = newPath.length(); MAX_PATH_LABEL_SIZE < pathLabel.getPreferredSize().width
                && i >= 0; i--) {
            pathLabel.setText(trimInMiddle(newPath, i));
        }
    }

    public void save() {
    	for (SettingsStruct s : hash.values()) {
            manager.setPathFromType(s.type, s.path);
            manager.setShared(s.type, s.shared);
        }
        reset();//funky.
    }

    private record SettingsStruct(MysterType type, String path, boolean shared) {}

    public void reset() {
        hash.clear();
        loadStateFromPrefs();
        restoreState();
    }

    public void loadStateFromPrefs() {
        path = manager.getPathFromType(choice.getType().get());
        checkbox.setSelected(manager.isShared(choice.getType().get()));
    }

    public String getKey() {
        return "File Manager";
    }

    public Dimension getPreferredSize() {
        return new Dimension(STD_XSIZE, STD_YSIZE);
    }

    private void restoreState() {
        SettingsStruct ss = hash.get(choice.getType().get());
        if (ss != null) {
            path = ss.path;
            checkbox.setSelected(ss.shared);
        } else {
            loadStateFromPrefs(); //if the state hasen't been changed then load
            // form prefs.
        }

        setPathLabel(path);
        String[] s = manager.getDirList(choice.getType().get());
        boolean isShared = manager.isShared(choice.getType().get());

        fListModel.removeAllElements();
        if (manager.getFileTypeList(choice.getType().get()).isIndexing()) {
            fListModel.addElement("<Indexing files...>");
            pokeTimer();
            return;
        } else {
            if (timer!=null)
                timer.cancelTimer(); //speed hack.
            
            timer = null;
        }

        if (s != null) {
            if (!isShared) {
                fListModel.addElement("<no files are being shared, sharing is disabled>");
            } else if (s.length == 0) {
                fListModel.addElement("<no files are being shared, there's no relevant files in this folder>");
            } else {
                if (s.length > 350) {
                    fListModel.addElement("<You are sharing " + s.length + " files (too many to list)>");
                } else {
                    for (int i = 0; i < s.length; i++) {
                        fListModel.addElement(s[i]);
                    }
                }
            }
        }
    }

    public void pokeTimer() {
        if (timer != null)
            return;
        if (manager.getFileTypeList(choice.getType().get()).isIndexing()) {
            timer = new Timer(() -> {
                timer = null;
                pokeTimer();
            }, 100);
        } else {
            restoreState();
        }
    }

    /**
     * Shows or hides the public-type warning banner depending on whether the currently
     * selected type is public.
     * <p>
     * Uses a translucent yellow background ({@code "Actions.Yellow"} at ~35% alpha) with the
     * theme's normal label foreground. This is legible on both light and dark themes: on light
     * it renders as a warm amber tint; on dark it reads as a subtle golden highlight. The icon
     * uses the full-saturation yellow so it stays visible at any opacity.
     */
    private void updateWarningBanner() {
        boolean isPublic = choice.getSelectedTypeDescription()
                                 .map(TypeDescription::isPublic)
                                 .orElse(false);
        if (isPublic) {
            Color yellow = Optional.ofNullable(UIManager.getColor("Actions.Yellow"))
                                   .orElse(new Color(0xED, 0xA2, 0x00));
            // Translucent background tint — readable against both light and dark panel backgrounds
            publicWarningLabel.setBackground(new Color(yellow.getRed(), yellow.getGreen(), yellow.getBlue(), 90));
            publicWarningLabel.setForeground(UIManager.getColor("Label.foreground"));
            publicWarningLabel.setOpaque(true);
            int h = publicWarningLabel.getFontMetrics(publicWarningLabel.getFont()).getHeight();
            try {
                FlatSVGIcon icon = IconLoader.loadSvg(MessageField.class, "warning-icon", h);
                publicWarningLabel.setIcon(icon);
            } catch (Exception ignored) {
                publicWarningLabel.setIcon(null);
            }
            publicWarningLabel.setText(
                    "Public type — files in this folder are visible to everyone on the network.");
            publicWarningLabel.setVisible(true);
        } else {
            publicWarningLabel.setOpaque(false);
            publicWarningLabel.setVisible(false);
        }
    }

    /**
     * This is a utility function that keeps strings
     * under numberOfChars characters and removes characters from the middle and
     * adding "..."
     */
    private static String trimInMiddle(String input, int numberOfChars) {
        if (input.length() <= numberOfChars)
            return input;
        if (input.length() < 2)
            return "...";

        int amountToDelete = numberOfChars / 2;
        return input.substring(0, amountToDelete)
                + "..."
                + input.substring(input.length() - (numberOfChars - amountToDelete) - 1, input
                        .length());
    }

}