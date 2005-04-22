/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

/**
 * The FileTypeList, better known as the FileList object contains a list of all
 * files shared under a given type. Every FileList has a type attached to it.
 *  
 */

package com.myster.filemanager;

import java.io.File;
import java.util.Locale;
import java.util.Vector;

import com.general.thread.CallListener;
import com.general.thread.CancellableCallable;
import com.general.thread.Future;
import com.myster.Myster;
import com.myster.hash.FileHash;
import com.myster.mml.MML;
import com.myster.mml.MMLException;
import com.myster.pref.Preferences;
import com.myster.type.MysterType;
import com.myster.util.MysterExecutor;
import com.myster.util.MysterThread;

public class FileTypeList extends MysterThread {
    private Vector filelist; //List of java.io.FileItem objects that are

    // shared.

    private MysterType type; //Myster type represented by this List.

    private String rootdir; //The root directory for this list.

    //private boolean isShared //This variable is accessed directly in the
    // preferences data structure! Use isShared() to access!
    private MML local_prefs;

    private String pref_key;

    private static final String ACTIVE_PREF = "/ActPref"; //Active.. sub dir

    // (active flag)

    private static final String PATH_PREF = "/PathPref"; //path pref sub dir.

    private static final String PREF_KEY = "FileManager.FileTypeList";

    public static final int MAX_RESULTS = 100; //maximum number of results

    // returnable (doesn't limit ""
    // queries)

    private volatile Future indexingFuture = null; // if true then the list is

    // indexing...

    /**
     * Creates a new FileTypeList. This shouldn't be called by anybody but the
     * FileItem Manager.
     * 
     * @param type
     *            is the Myster FileItem type to be represented by this object.
     * @param path
     *            is the root path IN THE PREFERENCES that this FileItem List
     *            should store it's preferences.
     */
    public FileTypeList(MysterType type, String path) {
        this.type = type;
        this.pref_key = PREF_KEY + "." + type;

        try {
            local_prefs = new MML(Preferences.getInstance().query(pref_key));
        } catch (MMLException ex) {
            local_prefs = new MML();
        }

        rootdir = getPath();
    }

    /**
     * returns the isShared flag. If isShared returns true, the list will share
     * files if any are available. If isShared returns false, the list will not
     * show any files shared even if there are file available. Think of it as a
     * sort of sharing over-ride.
     * 
     * @return <code>true</code> is the FileItem List is sharing files;
     *         <code>false</code> is the file list is not sharing files. There
     *         might be any files shared even if this function returns true. If
     *         this function returns false it is guarenteed that no files are
     *         being shared.
     */
    public synchronized boolean isShared() {
        String s = local_prefs.get(ACTIVE_PREF);
        if (s == null) {
            //init this
            local_prefs.put(ACTIVE_PREF, "true");
            savePrefs();
            s = local_prefs.get(ACTIVE_PREF);
        }

        return (s.equals("true")); //if s == 1 then return true.
    }

    /**
     * Some lists may be temporarily out of date while they perform and
     * expensive "indexing" operation. call this method to find out if this list
     * is in the middle of indexing...
     * 
     * @return true if this file list has an update pending.
     */
    public synchronized boolean isIndexing() {
        return indexingFuture != null;
    }

    /**
     * Sets the isShared flag. If isShared is set to true, the list will share
     * files if any are available. If isShared is set to false, the list will
     * not show any files shared even if there are file available. Think of it
     * as a sort of sharing over-ride. The effects of this function will not be
     * visible until the directory being pointed to has been indexed. In the
     * mean time, the file list will be empty.
     * 
     * @param b
     *            if b is false, no files will be shared.
     */
    public synchronized void setShared(boolean b) {
        local_prefs.put(ACTIVE_PREF, (b ? "true" : "false"));
        savePrefs();
        assertFileList();
    }

    /**
     * Gets the Myster type associated with this FileItem List.
     * 
     * @return the Myster Type associated with this object.
     */
    public MysterType getType() {
        return type; //note: no assertFileList(); file list ins't needed so
        // don't load it.
    }

    /**
     * Gets rootdirectory associated with this object as saved in the
     * preferences. Does not return rootdir variable since th rootdir variable
     * only has the root directory of the files save in the vector list of the
     * files.
     * 
     * @return the root directory associated with this object as saved in the
     *         preferences.
     */
    public synchronized String getPath() {
        if (!hasSetPath()) {
            setPath(getDefaultDirectoryPath());
        }
        return local_prefs.get(PATH_PREF);
    }

    /**
     * Returns a list of all shared files. If getShared is false, no filesa are
     * returned.
     * 
     * @return an array of all the shared files
     */
    public synchronized String[] getFileListAsStrings() {
        assertFileList(); //This must be called before working with filelist or
        // rootdir internal variables.

        String[] workingarray = new String[filelist.size()];
        for (int i = 0; i < filelist.size(); i++) {
            workingarray[i] = mergePunctuation(((FileItem) (filelist.elementAt(i))).getFile()
                    .getName());
        }
        return workingarray;
    }

    /**
     * Returns a list of all shared files. If getShared is false, no filesa are
     * returned.
     * 
     * @return an array of all the shared file with the hashes
     */
    public synchronized FileItem getFileFromHash(FileHash hash) {
        assertFileList(); //This must be called before working with filelist or
        // rootdir internal variables.

        for (int i = 0; i < filelist.size(); i++) {
            if (isMatch((FileItem) filelist.elementAt(i), hash))
                return (FileItem) filelist.elementAt(i);
        }

        return null;
    }

    private boolean isMatch(FileItem item, FileHash hash) {
        FileHash myHash = item.getHash(hash.getHashName());

        if (myHash == null)
            return false;

        return hash.equals(myHash);
    }

    /**
     * Returns a list of files that match the query string
     * 
     * Matching ALGORYTHM IS: -Fill this in.-
     * 
     * @param queryStr
     *            string
     * @return a list of files maching the query string
     */
    public synchronized String[] getFileListAsStrings(String queryStr) {
        if (queryStr.equals(""))
            return getFileListAsStrings(); //not limited by MAX_RESULTS

        assertFileList();

        Vector rtn = new Vector(MAX_RESULTS);

        Vector keywords = new Vector(20, 10);
        StringBuffer t = new StringBuffer(" ");

        boolean inWord = false;
        boolean aggregate = false;

        queryStr = mergePunctuation(queryStr);

        // Split queryStr into keywords at the whitespaces into keywords
        //    (Anything !Character.isLetterOrDigit() is considered whitespace)
        // Keeping a space as the first character of each keyword forces
        // beginning-of-word matches.

        //TOKENIZZZZEE!!
        for (int i = 0; i < queryStr.length(); i++) {
            char c = queryStr.charAt(i);
            if (c == '\"') {
                if (t.charAt(t.length() - 1) != ' ')
                    t.append(' ');
                if (t.length() > 1) {
                    keywords.addElement(t.toString());
                    t = new StringBuffer(" ");
                }
                aggregate = !aggregate;
            } else if (Character.isLetterOrDigit(c)) {
                t.append(Character.toLowerCase(c));
            } else {
                // if (t.charAt(t.length()-1) != ' ') t.append(' '); //
                // uncomment to match full words only.
                // for now it matches any begining of words.
                if (t.length() > 1 && !aggregate) {
                    keywords.addElement(t.toString());
                    t = new StringBuffer(" ");
                } else if (t.charAt(t.length() - 1) != ' ')
                    t.append(' ');
            }
        }
        if (t.length() > 1)
            keywords.addElement(t.toString());

        //MATCHER
        for (int iFile = 0; iFile < filelist.size(); iFile++) {
            FileItem file = (FileItem) filelist.elementAt(iFile);
            String filename = mergePunctuation(file.getFile().getName());

            // Filter out sequential whitespace
            String simplified = simplify(filename);

            if (isMatch(keywords, simplified))
                rtn.addElement(filename);

            if (rtn.size() > MAX_RESULTS)
                break;
        }

        String[] rtnStr = new String[rtn.size()];
        rtn.copyInto(rtnStr);
        return rtnStr;
    }

    /**
     * Private function used by String[] getFileListAsStrings(String queryStr)
     * 
     * matches queries to a "simplified" string.
     * 
     * @param keywords
     *            to match.
     * @param simplified
     *            string to match against.
     * @return the java.io.FileItem object corresponding the the query.
     *  
     */
    private static boolean isMatch(Vector keywords, String simplified) {
        for (int iKeyword = 0; iKeyword < keywords.size(); iKeyword++) {
            String keyword = (String) keywords.elementAt(iKeyword);
            if (simplified.indexOf(keyword) == -1)
                return false;
        }
        return true;
    }

    /**
     * Private function used by String[] getFileListAsStrings(String queryStr)
     * 
     * replaces all non letter or digit characters (like: !@#$%^&*()) with
     * spaces to simplify matching
     * 
     * @param filename
     *            filename to simplify
     * @return the simplified string.
     */
    private static String simplify(String filename) {
        StringBuffer simplified = new StringBuffer(255); //pre-allocate some
        // space to the string
        // buffer!

        simplified.append(' ');

        for (int i = 0; i < filename.length(); i++) {
            char c = filename.charAt(i);
            if (Character.isLetterOrDigit(c))
                simplified.append(Character.toLowerCase(c));
            else if (simplified.charAt(simplified.length() - 1) != ' ')
                simplified.append(' ');
        }
        return new String(simplified);
    }

    /**
     * rarray a java.io.FileItem object from a file name. NOTE: There is a
     * direct mapping between file names and java.io.FileItem objects.
     * 
     * @param query
     *            the name of a file to get the File for.
     * @return the java.io.FileItem object corresponding the the query.
     */
    public synchronized FileItem getFileItemFromString(String query) {
        assertFileList(); //This must be called before working with filelist or
        // rootdir internal variables.

        for (int i = 0; i < filelist.size(); i++) {
            if ((mergePunctuation(((FileItem) (filelist.elementAt(i))).getFile().getName()))
                    .equals(query))
                return (FileItem) (filelist.elementAt(i));
        }
        return null; //err, file not found.
    }

    /**
     * Returns the number of files
     * 
     * @return the number of files. Returns 0 if getShared() is false.
     */
    public synchronized int getNumOfFiles() {
        assertFileList(); //This must be called before working with filelist or
        // rootdir internal variables.
        return filelist.size();
    }

    /**
     * Sets the root directory in the preferences. The effects of this function
     * will not be visible until the directory being passed has been indexed. In
     * the mean time, the file list will maintain its old list.
     * 
     * @param s,
     *            the new root dir path.
     */
    public void setPath(String s) { //notice no error if path is nonsence!
        if (s == null) {
            local_prefs.remove(PATH_PREF);
        } else {
            local_prefs.put(PATH_PREF, mergePunctuation(s)); //Change info
        }

        savePrefs();
        assertFileList();
        //notice not root=pref value or anything.. This omition needed to clue
        // assertFileList to rebuild.
    }

    private synchronized void savePrefs() {
        Preferences.getInstance().put(pref_key, local_prefs.toString()); //Change
        // info
    }

    private synchronized void setFileList(Vector fileList) {
        resetIndexingVariables();
        filelist = fileList;
        assertFileList();
    }

    private synchronized void resetIndexingVariables() {
        timeoflastupdate = System.currentTimeMillis();
        indexingFuture = null;
    }

    /**
     * This function makes sure the the filelist and rootdir variables are up to
     * date. The general design of this object is that things should not happen
     * until they need to. That is, files should not be indexed if there's no
     * one waiting on the index. This function does all the checks and calls
     * nessesairy to make sure filelist and rootdir contain the most up-to-date
     * values. This funcion is also responsible for clearing the filelist
     * variable when the list has been shared or un-shared. As a general rule it
     * should be called before accessing the filelist or rootdir variables.
     *  
     */
    private synchronized void assertFileList() {
        if (filelist == null) {
            filelist = new Vector(1, 1);
        }
        if (!isShared()) { //if file list is not shared make sure list has
            // length = 0 then continue.
            if (filelist.size() != 0) {
                filelist = new Vector(1, 1);
            }
            timeoflastupdate = 0; //never updated (we just buggered up the
            // list, you see...)

            if (indexingFuture != null) {
                indexingFuture.cancel();
                indexingFuture = null;
            }
            return;
        }

        //We need to check to see if the user has changed the directory for
        // this type.
        //load the dir for this type
        String workingdir = getPath();

        /*
         * If the directory need indexing or the user has changed the directory
         * to index from, this part of the code will start a new indexing task
         * off.. assuming there's not one already running..
         */
        if (indexingFuture == null && (isOld() || !rootdir.equals(workingdir))) {
            rootdir = workingdir; //in case the dir for this type has changed.
            indexingFuture = MysterExecutor.getInstance().execute(new FileListIndexCall(),
                    new FileListCallListener());
        }
    }

    private class FileListCallListener implements CallListener {

        /*
         * (non-Javadoc)
         * 
         * @see com.general.thread.CallListener#handleCancel()
         */
        public void handleCancel() {
            //nothing
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.general.thread.CallListener#handleResult(java.lang.Object)
         */
        public void handleResult(Object result) {
            setFileList((Vector) result);
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.general.thread.CallListener#handleException(java.lang.Exception)
         */
        public void handleException(Exception ex) {
            ex.printStackTrace();
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.general.thread.CallListener#handleFinally()
         */
        public void handleFinally() {
            resetIndexingVariables();
        }

    }

    private class FileListIndexCall implements CancellableCallable {
        private boolean endFlag = false;

        /*
         * (non-Javadoc)
         * 
         * @see com.general.thread.CancellableCallable#call()
         */
        public Object call() throws Exception {
            return indexFiles(type, rootdir);
        }

        /**
         * an internal procedure used to do the setup of file indexing. This
         * function is only called in one place at this writting.
         */
        private Vector indexFiles(MysterType type, String rootdir) {
            Vector temp = new Vector(10000, 10000); //Preallocates a whole lot
            // of
            // space
            File dir = new File(rootdir);
            if (dir.exists() && dir.isDirectory())
                indexDir(type, dir, temp, 5); //Indexes root dir into temp with
            // 5 levels
            // deep.
            temp.trimToSize(); //save some space
            return temp;
        }

        /**
         * an internal proceedure used to do the actual file indexing. This
         * function is called recursively for each sub directories up to
         * telomere levels
         * 
         * @param file
         *            is the directory to index
         * @param filelist
         *            is the data structure to save the indexed filename to.
         * @param telomere
         *            is a recusion counter. The function will recurse a maximum
         *            of telomere times
         */
        private void indexDir(MysterType type, File file, Vector filelist, int telomere) {
            telomere--;
            if (telomere < 0)
                return;
            if (!file.isDirectory() || !file.exists()) {
                System.out
                        .println("Nonsence sent to indexDir. Does this type have a d/l dir associated with it?");
                return;
            }
            String[] listing = file.list();
            File temp;
            if (listing != null) { // listing is null on permission denied
                for (int i = 0; i < listing.length; i++) {
                    if (endFlag)
                        return;

                    temp = new File(file.getAbsolutePath() + File.separator + listing[i]);
                    if (temp.isDirectory()) {
                        indexDir(type, temp, filelist, telomere);
                    } else {
                        if (FileFilter.isCorrectType(type, temp)) {
                            FileItem fileItem = createFileItem(temp);
                            if (!filelist.contains(fileItem)) {//Don't add a
                                                               // file to the
                                                               // list if it's
                                                               // already there
                                filelist.addElement(fileItem);
                            }
                        }
                    }
                }
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.general.thread.CancellableCallable#cancel()
         */
        public void cancel() {
            endFlag = true;
        }

    }

    /**
     * Returns true of the FileItem List is out of date. This function is called
     * by assertFileList and should oly be called by assertFileList
     */
    long timeoflastupdate = 0; //globalish Needed to make sure the list is not

    // too old.

    //NOTE: The user could also change the DIR to force and update... He could
    // also re-start Myster.
    private synchronized boolean isOld() {
        if (System.currentTimeMillis() - timeoflastupdate > (1000 * 60 * 60))
            return true; //If list is older than 1 hour...
        return false;
    }

    /**
     * Determines what path should be used as the root path. Should only be used
     * by getPath();
     */
    private String getDefaultDirectoryPath() {
        String s = getDefaultDirectory().getAbsolutePath();
        if (s.charAt(s.length() - 1) != File.separatorChar)
            s = s + File.separator;
        return s;
    }

    /*
     * Suggests a default root directory in the fileing system. Should only be
     * used by getDefaultDirectoryPath();
     */
    private synchronized File getDefaultDirectory() {
        File empty = new File(Myster.getCurrentDirectory(), type + " Downloads");
        int counter = 1;
        do {
            if (empty.exists()) {
                if (empty.isDirectory())
                    return empty; //here is where the routine should go most of
                // the time.
                else {
                    empty = new File(type + " Downloads" + counter);
                    counter++;
                    //if (counter>1000) System.exit(0);//bam!
                }
            } else {
                break; //if file doesn't exist go make a dir.
            }
        } while (true);

        empty.mkdir();

        return empty;

    }

    /**
     * Returns true if the path has been initialized, returns false if it
     * hasen't.
     * 
     * @return <code>false</code> if the path in the preferences has been
     *         initialized. true otherwise.
     */
    private boolean hasSetPath() {
        return (local_prefs.get(PATH_PREF) != null);
    }

    /**
     * Creates a FileItem from a file. Sub classes should over-ride this.
     * 
     * @param file
     *            to be the basis of this FileItem.
     * @return FileItem created from file.
     */
    private static final MysterType MPG3 = new MysterType("MPG3");
    protected FileItem createFileItem(File file) {
        if (MPG3.equals(type))
            return new MPG3FileItem(file);
        return new FileItem(file);
    }

    /**
     * This function Merges Japaneese punctuation into a form that displays and
     * matches in JAVA This function should be called whenever the name or path
     * of a file is read.
     * 
     * (code submited by heavy_baby@yahoo.co.jp)
     * 
     * @param text
     *            of a filename or path that needs merging.
     * @return String with punctuation merged
     */
    public static String mergePunctuation(String text) {
        if (Locale.getDefault().getDisplayLanguage().equals(Locale.JAPANESE.getDisplayLanguage())) {
            if (text.length() <= 1)
                return text;
            StringBuffer buffer = new StringBuffer(text.length());
            char pre = text.charAt(0);
            for (int i = 1; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '\u3099') {
                    if (pre == '\u30a6') {
                        pre = '\u30f4';
                    } else {
                        pre = (char) (pre + 1);
                    }
                } else if (ch == '\u309a') {
                    pre = (char) (pre + 2);
                } else {
                    buffer.append(pre);
                    pre = ch;
                }
            }
            buffer.append(pre);
            return buffer.toString();
        } else {
            return text;
        }
    }

}