package com.myster.client.stream;

import java.awt.Frame;
import java.io.File;
import java.io.IOException;

import com.general.util.AnswerDialog;
import com.myster.hash.FileHash;
import com.myster.mml.RobustMML;
import com.myster.search.MysterFileStub;

//import Myster;

public class MultiSourceUtilities {

    private static final String EXTENSION = ".i";

    public static File getFileToDownloadTo(MysterFileStub stub,
            Frame parentFrame) {
        /*
         * 
         * File incomingDirectory = getIncomingDirectory();
         * 
         * return new
         * File(incomingDirectory.getPath()+File.separator+stub.getName()+EXTENSION);
         */

        File directory = new File(com.myster.filemanager.FileTypeListManager
                .getInstance().getPathFromType(stub.getType())); //throws a null pointer exception on invalid type.
        File file = new File(directory.getPath() + File.separator
                + stub.getName() + EXTENSION);

        if (!directory.isDirectory()) {
            file = askUserForANewFile(stub.getName());

            if (file == null)
                return null;//throw new IOException("User Cancelled");
        }

        while (file.exists()) {
            final String CANCEL_BUTTON = "Cancel", WRITE_OVER = "Write-Over", RENAME = "Rename";

            String answer = (new AnswerDialog(parentFrame,
                    "A file by the name of " + file.getName()
                            + " already exists. What do you want to do.",
                    new String[] { CANCEL_BUTTON, WRITE_OVER })).answer();
            if (answer.equals(CANCEL_BUTTON)) {
                return null;//throw new IOException("User Canceled.");
            } else if (answer.equals(WRITE_OVER)) {
                if (!file.delete()) {
                    AnswerDialog.simpleAlert(parentFrame,
                            "Could not delete the file.");
                    return null;///throw new IOException("Could not delete file");
                }
            } else if (answer.equals(RENAME)) {
                file = askUserForANewFile(stub.getName());

                if (file == null)
                    return null;//throw new IOException("User Cancelled");
            }
        }

        return file;
    }

    public static boolean moveFileToFinalDestination(final File sourceFile,
            Frame parentFrame) throws IOException {

        final String FILE_ENDING = ".i";

        File theFile = sourceFile; //!

        if (!theFile.getName().endsWith(FILE_ENDING)) {
            AnswerDialog.simpleAlert(parentFrame, "Could not rename file \""
                    + theFile.getName() + "\" because it does not end with "
                    + FILE_ENDING + ".");
            return true; //don't display an error, I've already done it
        }

        String path = theFile.getAbsolutePath();
        File someFile = someFile = new File(path.substring(0, path.length()
                - (FILE_ENDING.length()))); //-2 is for .i

        if (someFile.exists()) {
            AnswerDialog.simpleAlert(parentFrame,
                    "Could not rename file from \"" + theFile.getName()
                            + "\" to \"" + someFile.getName()
                            + "\" because a file by that name already exists.");
            return true;
        }

        if (!theFile.renameTo(someFile)) {
            AnswerDialog.simpleAlert(parentFrame,
                    "Could not rename file from \"" + theFile.getName()
                            + "\" to \"" + someFile.getName()
                            + "\" because an unspecified error occured.");
            return true;
        }
        return true;

        /*
         * File directory = new
         * File(com.myster.filemanager.FileTypeListManager.getInstance().getPathFromType(stub.getType()));
         * File file = new
         * File(directory.getPath()+File.separator+stub.getName());
         * 
         * if (!directory.isDirectory()) { file =
         * askUserForANewFile(file.getName());
         * 
         * if (file == null) throw new IOException("User Cancelled"); }
         * 
         * while (file.exists()) { final String CANCEL_BUTTON = "Cancel",
         * WRITE_OVER = "Write-Over", RENAME = "Rename";
         * 
         * String answer = (new AnswerDialog( parentFrame, "A file by the name
         * of "+file.getName()+" already exists. What do you want to do.", new
         * String[]{CANCEL_BUTTON, WRITE_OVER, RENAME}) ).answer(); if
         * (answer.equals(CANCEL_BUTTON)) { throw new IOException("User
         * Canceled."); } else if (answer.equals(WRITE_OVER)) { if
         * (!file.delete()) { AnswerDialog.simpleAlert(parentFrame, "Could not
         * delete the file."); throw new IOException("Could not delete file"); } }
         * else if (answer.equals(RENAME)) { file =
         * askUserForANewFile(file.getName());
         * 
         * if (file == null) throw new IOException ("User Cancelled"); } }
         * 
         * System.out.println(""+file);
         * 
         * return sourceFile.renameTo(file);
         */
    }

    /**
     * Returns a file object containing te path that a multi-source object will
     * be downloaded to.
     * <p>
     * This routine is not stable as it is dependent on the way multi-source
     * downloads are downloaded.
     */
    public static File getIncomingDirectory() throws IOException {
        File file = new File(com.myster.Myster.getCurrentDirectory(),
                "Incoming"); //! This will only ever return null;

        if ((file.exists()) && (file.isDirectory()))
            return file;

        if (!file.exists()) {
            file.mkdir();
            return file;
        } else {
            throw new IOException(
                    "Could not make an incomming directory because there is a file in the way.");
        }
    }

    private static File askUserForANewFile(String name) {
        java.awt.FileDialog dialog = new java.awt.FileDialog(
                com.general.util.AnswerDialog.getCenteredFrame(),
                "What do you want to save the file as?",
                java.awt.FileDialog.SAVE);
        dialog.setFile(name);
        dialog.setDirectory(name);

        dialog.show();

        File directory = new File(dialog.getDirectory());

        if (dialog.getFile() == null)
            return null; //canceled.

        return new File(dialog.getDirectory() + File.separator
                + dialog.getFile() + EXTENSION);
    }

    public static FileHash getHashFromStats(RobustMML mml) throws IOException {
        String hashString = mml.get("/hash/" + com.myster.hash.HashManager.MD5);

        if (hashString == null)
            throw new IOException("Stats MML does not contain the wanted info.");

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

    /*
     * private boolean assertLengthAndHash(MysterSocket socket) throws
     * IOException { try { com.myster.mml.RobustMML mml =
     * StandardSuite.getFileStats(socket, stub);
     * 
     * String hashString =
     * mml.get(com.myster.filemanager.FileItem.HASH_PATH+com.myster.hash.HashManager.MD5);
     * String fileLengthString = mml.get("/size");
     * 
     * if (hashString == null || fileLengthString == null) return false;
     * 
     * try { hash =
     * com.myster.hash.SimpleFileHash.buildFromHexString(com.myster.hash.HashManager.MD5,
     * hashString); } catch (NumberFormatException ex) { return false; }
     * 
     * try { fileLength = Long.parseLong(fileLengthString); } catch
     * (NumberFormatException ex) { return false; }
     * 
     * return true; } catch (UnknownProtocolException ex) { return false; } }
     */

    public static void debug(String string) {
        //System.out.println(string);
    }

    
    private static final String STOP_DOWNLOAD = "Kill";
    private static final String CANCEL = "Don't Kill";
    /**
     * Asks the user to confirm stopping this download.
     * 
     * @return
     */
    public static boolean confirmCancel(Frame progress) {
        final String choice = AnswerDialog.simpleAlert(progress,
                "Are you sure you want to kill this download?", new String[] {
                STOP_DOWNLOAD, CANCEL });
        return (choice.equals(STOP_DOWNLOAD));
    }
}

/*
 * public synchronized boolean start() throws IOException { downloaders = new
 * InternalSegmentDownloader[5];
 * 
 * if (!assertLengthAndHash()) return false; //gets file length and hash values
 * from inital remote server.
 * 
 * progress.setTitle("MS Download: "+stub.getName());
 * progress.setProgressBarNumber(1);
 * 
 * try { getFileThingy(); } catch (IOException ex) { progress.hide(); throw ex; }
 * 
 * 
 * progress.setBarColor(Color.blue, 0);
 * progress.startBlock(FileProgressWindow.BAR_1, 0, fileLength);
 * progress.addWindowListener(new WindowAdapter() { public void
 * windowClosing(WindowEvent e) { flagToEnd(); //end();
 * progress.setVisible(false); } });
 * 
 * //progress.setText("Downloading file "+stub.getName());
 * 
 * //for (int i=0; i < downloaders.length; i++) { // progress.setBarColor(new
 * Color(0,(downloaders.length-i)*(255/downloaders.length),150), i+1); //}
 * 
 * 
 * startCrawler();
 * 
 * newDownload(stub, socket);
 * 
 * return true; }
 */

/*
 * 
 * 
 * private void startCrawler() { IPQueue ipQueue = new IPQueue();
 * 
 * String[] startingIps =
 * com.myster.tracker.IPListManagerSingleton.getIPListManager().getOnRamps();
 * 
 * ipQueue.addIP(stub.getMysterAddress()); ipQueue.getNextIP(); //this is so we
 * don't start downloading from the first one again.
 * 
 * for (int i = 0; i < startingIps.length; i++) { try { ipQueue.addIP(new
 * MysterAddress(startingIps[i])); } catch (IOException ex)
 * {ex.printStackTrace();} }
 * 
 * crawler = new CrawlerThread(new MultiSourceHashSearch(stub.getType(), hash,
 * new MSHashSearchListener()), stub.getType(), ipQueue, new Sayable() { public
 * void say(String string) { System.out.println(string); }}, null);
 * 
 * crawler.start(); }
 *  
 */
