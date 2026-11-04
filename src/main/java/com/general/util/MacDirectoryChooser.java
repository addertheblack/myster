package com.general.util;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;

import javax.swing.SwingUtilities;

/**
 * Native macOS directory selection through AWT's Apple-specific extension.
 */
public final class MacDirectoryChooser {
    private static final String DIRECTORY_MODE_PROPERTY = "apple.awt.fileDialogForDirectories";

    private MacDirectoryChooser() {
    }

    /**
     * Shows the native Open panel in directory-selection mode. Call only on macOS,
     * on the event dispatch thread. Other platforms should keep using their Swing
     * directory chooser because this property is specific to the macOS AWT peer.
     *
     * @param parent component whose window owns the dialog, or null
     * @param title dialog title
     * @param initialDirectory starting directory, or null for the platform default
     * @return selected directory, or null if cancelled
     */
    public static File showDialog(Component parent, String title, File initialDirectory) {
        Util.ensureEventDispatchThread();
        Window owner = parent instanceof Window window ? window
                : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        FileDialog dialog = owner instanceof Dialog parentDialog
                ? new FileDialog(parentDialog, title, FileDialog.LOAD)
                : new FileDialog(owner instanceof Frame frame ? frame : null, title, FileDialog.LOAD);
        String previousMode = System.getProperty(DIRECTORY_MODE_PROPERTY);
        try {
            System.setProperty(DIRECTORY_MODE_PROPERTY, "true");
            if (initialDirectory != null) {
                dialog.setDirectory(initialDirectory.getAbsolutePath());
            }
            dialog.setVisible(true);
            String selectedName = dialog.getFile();
            return selectedName == null ? null : new File(dialog.getDirectory(), selectedName);
        } finally {
            try {
                dialog.dispose();
            } finally {
                // Do not turn subsequent AWT file-open dialogs into folder pickers.
                if (previousMode == null) {
                    System.clearProperty(DIRECTORY_MODE_PROPERTY);
                } else {
                    System.setProperty(DIRECTORY_MODE_PROPERTY, previousMode);
                }
            }
        }
    }
}
