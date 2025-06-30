/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.filemanager.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import com.general.util.Timer;
import com.myster.filemanager.FileTypeListManager;
import com.myster.pref.ui.PreferencesPanel;
import com.myster.type.MysterType;
import com.myster.type.TypeDescriptionList;
import com.myster.util.TypeChoice;

/**
 * The FMICHooser is the FileManagerInterfaceChooser. It's the GUI for the
 * FileManager prefs panel. It's built to be as independent from the internal
 * working of the FileManager, despite having access to the sweat, sweat inners.
 */

public class FmiChooser extends PreferencesPanel {
    private static final Logger LOGGER = Logger.getLogger(PreferencesPanel.class.getName());
	private static final int XPAD = 10;
	private static final int SAB = 200;
	private static final int MAX_PATH_LABEL_SIZE = STD_XSIZE - 100 - 3 * XPAD - 5;
	
    private final FileTypeListManager manager;
    private final TypeChoice choice;
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

    public FmiChooser(FileTypeListManager manager, TypeDescriptionList tdList) {
        this.manager = manager;
        setLayout(null);

        choice = new TypeChoice(tdList, false);
        choice.setLocation(5, 4);
        choice.setSize(STD_XSIZE - XPAD - XPAD - SAB, 20);
        choice.addItemListener((ItemEvent _) -> {
            restoreState();
            repaint();
        });
        add(choice);

        setAllButton = new JButton("Set all paths to this path");
        setAllButton.setLocation(STD_XSIZE - XPAD - SAB, 4);
        setAllButton.setSize(SAB, 20);
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
        add(setAllButton);

        path = manager.getPathFromType(choice.getType().get());

        checkbox = new JCheckBox("Share this type", manager.isShared(choice.getType().get()));
        checkbox.setLocation(10, 55);
        checkbox.setSize(150, 25);
        checkbox.addItemListener((ItemEvent _) -> {
            hash.put(choice.getType().get(),
                     new SettingsStruct(choice.getType().get(), path, checkbox.isSelected()));
        });
        add(checkbox);

        button = new JButton("Set Folder");
        button.setLocation(STD_XSIZE - 100 - XPAD, 55);
        button.setSize(100, 25);
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
                LOGGER.info("User cancelled the action.");
            }
        });
        add(button);

        folderl = new JLabel("Shared Folder:");
        folderl.setLocation(10, 85);
        folderl.setSize(100, 20);
        add(folderl);

        // choice must be created first
        pathLabel = new JLabel();
        pathLabel.setLocation(100 + XPAD + 5, 85);
        //textfeild.setEditable(false);
        pathLabel.setSize(STD_XSIZE - 100 - 3 * XPAD - 5, 20);
        setPathLabel(manager.getPathFromType(choice.getType().get()));
        add(pathLabel);

        filelistl = new JLabel("Shared Files (click \"Apply\" to see changes) :");
        filelistl.setLocation(10, 110);
        filelistl.setSize(STD_XSIZE - 2 * XPAD, 20);
        add(filelistl);

        fList = new JList<>();
        fListModel = new DefaultListModel<>();
        fList.setModel(fListModel);
        JScrollPane fListScrollPane = new JScrollPane(fList);
        fListScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        fListScrollPane.setLocation(10, 135);
        fListScrollPane.setSize(STD_XSIZE - 2 * XPAD, STD_YSIZE - 150);
        add(fListScrollPane);

        repaint();
        reset();
        restoreState();
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

    public void paintComponent(Graphics g) {
//        g.setColor(getBackground());
//        Rectangle rectangle = g.getClipBounds();
//        g.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
        super.paintComponent(g);
        g.setColor(new Color(150, 150, 150));
        g.drawRect(5, 35, STD_XSIZE - 10, STD_YSIZE - 40);
        g.setColor(getBackground());
        g.fillRect(10, 34, 170, 3);
        g.setColor(Color.black);
        g.drawString("Setting for type: " + choice.getSelectedDescription(), 12, 39);
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