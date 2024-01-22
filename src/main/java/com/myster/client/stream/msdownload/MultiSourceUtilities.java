package com.myster.client.stream.msdownload;

import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import com.general.util.AnswerDialog;
import com.myster.filemanager.FileTypeListManager;
import com.myster.hash.FileHash;
import com.myster.mml.RobustMML;
import com.myster.search.MysterFileStub;

public class MultiSourceUtilities {
    private static final Logger LOGGER = Logger.getLogger(MultiSourceUtilities.class.getName());

    private static final String EXTENSION = ".i";

    private static final String OK_BUTTON = "OK", CANCEL_BUTTON = "Cancel",
            WRITE_OVER = "Write-Over", RENAME = "Rename";

    public static File getFileToDownloadTo(MysterFileStub stub, Frame parentFrame) {
        String directoryString = FileTypeListManager.getInstance().getPathFromType(stub.getType());
        File directory = (directoryString == null ? askUserForANewFile(stub.getName()) : new File(
                directoryString));
        File file = new File(directory.getPath(), stub.getName() + EXTENSION);

        if (!directory.isDirectory()) {
            file = askUserForANewFile(stub.getName());
        }

        while (true) {
            if (file == null) {
                return null;
            } else if (file.exists()) {
                String answer = AnswerDialog
                        .simpleAlert(parentFrame,
                                     "A file by the name of " + file.getName()
                                             + " already exists. What do you want to do.",
                                     new String[] { WRITE_OVER, CANCEL_BUTTON });
                if (answer.equals(CANCEL_BUTTON)) {
                    return null;
                } else if (answer.equals(WRITE_OVER)) {
                    if (!file.delete()) {
                        AnswerDialog.simpleAlert(parentFrame, "Could not delete the file.");
                        return null;
                    }
                } else if (answer.equals(RENAME)) {
                    // if file is null, will be cancelled next loop
                    file = askUserForANewFile(stub.getName());
                }
            } else if (!isWritable(file)) {
                String answer =  AnswerDialog.simpleAlert(parentFrame,
                        "Cannot write to this directory, it appears to be read-only.",
                        new String[] { OK_BUTTON, CANCEL_BUTTON });
                if (answer.equals(CANCEL_BUTTON)) {
                    return null;
                } else {
                    file = askUserForANewFile(stub.getName());
                }
            } else {
                break;
            }
        }

        return file;
    }

    public static boolean isWritable(File file) {
        boolean fileExsts = file.exists();

        //try {
        //    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        //} catch (FileNotFoundException ex) {
        //    return false;
        //}
        if (!fileExsts && file.exists())
            file.delete();

        return true;
    }

    public static void moveFileToFinalDestination(final File sourceFile, Frame parentFrame) {
        final String FILE_ENDING = ".i";

        if (!sourceFile.getName().endsWith(FILE_ENDING)) {
            AnswerDialog.simpleAlert(parentFrame,
                                     "Could not rename file \"" + sourceFile.getName()
                                             + "\" because it does not end with " + FILE_ENDING
                                             + ".");
            return; // don't display an error, I've already done it
        }

        String path = sourceFile.getAbsolutePath();

        File someFile = new File(path.substring(0, path.length() - (FILE_ENDING.length())));

        if (someFile.exists()) {
            AnswerDialog.simpleAlert(parentFrame,
                                     "Could not rename file from \"" + sourceFile.getName()
                                             + "\" to \"" + someFile.getName()
                                             + "\" because a file by that name already exists.");
            return;
        }

        if (!sourceFile.renameTo(someFile)) {
            AnswerDialog.simpleAlert(parentFrame,
                                     "Could not rename file from \"" + sourceFile.getName()
                                             + "\" to \"" + someFile.getName()
                                             + "\" because an unspecified error occured.");
            return;
        }
    }

    /**
     * Returns a file object containing the path that a multi-source object will
     * be downloaded to.
     * <p>
     * This routine is not stable as it is dependent on the way multi-source
     * downloads are downloaded.
     */
    public static File getIncomingDirectory() throws IOException {
        File file = new File(com.myster.application.MysterGlobals.getCurrentDirectory(), "Incoming");

        if ((file.exists()) && (file.isDirectory()))
            return file;

        if (!file.exists()) {
            file.mkdir();
            return file;
        } else {
            throw new IOException(
                    "Could not make an incoming directory because there is a file in the way.");
        }
    }

    private static File askUserForANewFile(String name) {
        java.awt.FileDialog dialog = new java.awt.FileDialog(com.general.util.AnswerDialog
                .getCenteredFrame(), "What do you want to save the file as?",
                java.awt.FileDialog.SAVE);
        dialog.setFile(name);
        dialog.setDirectory(name);

        dialog.setVisible(true);

        File directory = new File(dialog.getDirectory());

        if (dialog.getFile() == null)
            return null; //canceled.

        return new File(directory + File.separator + dialog.getFile() + EXTENSION);
    }

    public static FileHash getHashFromStats(RobustMML mml) throws IOException {
        String hashString = mml.get("/hash/" + com.myster.hash.HashManager.MD5);

        if (hashString == null)
            return null;

        try {
            return com.myster.hash.SimpleFileHash.buildFromHexString(
                    com.myster.hash.HashManager.MD5, hashString);
        } catch (NumberFormatException ex) {
            throw new IOException("Stats MML is corrupt.");
        }
    }

    public static long getLengthFromStats(RobustMML mml) throws IOException {
        String fileLengthString = mml.get("/size");

        if (fileLengthString == null)
            throw new IOException("Stats MML does not contain the wanted info.");

        try {
            return Long.parseLong(fileLengthString);
        } catch (NumberFormatException ex) {
            throw new IOException("Stats MML is corrupt.");
        }
    }

    public static void debug(String msg) {
        LOGGER.fine(msg);
    }

    private static final String STOP_DOWNLOAD = "Kill";

    private static final String CANCEL = "Don't Kill";

    /**
     * Asks the user to confirm stopping this download.
     */
    public static boolean confirmCancel(Frame progress) {
        final String choice = AnswerDialog.simpleAlert(progress,
                "Are you sure you want to kill this download?", new String[] { STOP_DOWNLOAD,
                        CANCEL });
        return (choice.equals(STOP_DOWNLOAD));
    }
}
