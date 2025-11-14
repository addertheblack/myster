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

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.general.thread.CancellableCallable;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.ProtectedForUnitTests;
import com.myster.application.MysterGlobals;
import com.myster.hash.FileHash;
import com.myster.hash.FileHashEvent;
import com.myster.hash.FileHashListener;
import com.myster.mml.MML;
import com.myster.mml.MMLException;
import com.myster.pref.MysterPreferences;
import com.myster.type.MysterType;
import com.myster.type.StandardTypes;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;

public class FileTypeList {
    private static final Logger LOGGER = Logger.getLogger(FileTypeList.class.getName());
    
    private static final Invoker INVOKER = Invoker.newVThreadInvoker();
    
    private Map<String, FileItem> fileMap; // LinkedHashMap for fast filename lookups and maintains insertion order

    private final MysterType type; // Myster type represented by this List.
    
    private final FileSystem fileSystem; // FileSystem to use (default or custom for testing)

    private String rootdir; // The root directory for this list.

    // private boolean isShared //This variable is accessed directly in the
    // preferences data structure! Use isShared() to access!
    private MML local_prefs;

    private final String pref_key;

    private static final String ACTIVE_PREF = "/ActPref"; // Active.. sub dir

    // (active flag)

    private static final String PATH_PREF = "/PathPref"; // path pref sub dir.

    private static final String PREF_KEY = "FileManager.FileTypeList";

    public static final int MAX_RESULTS = 100; // maximum number of results

    // returnable (doesn't limit ""
    // queries)

    private volatile PromiseFuture<List<FileItem>> indexingFuture = null; // if true then the list is

    private final HashProvider hashProvider;
    private final TypeDescriptionList tdList;

    private volatile boolean initialized = false;

    // indexing...

    /**
     * Creates a new FileTypeList with the default file system.
     * This shouldn't be called by anybody but the FileItem Manager.
     * 
     * @param type
     *            is the Myster FileItem type to be represented by this object.
     * @param path
     *            is the root path IN THE PREFERENCES that this FileItem List
     *            should store it's preferences.
     * @param hashProvider
     * @param tdList
     */
    public FileTypeList(MysterType type,
                        String path,
                        HashProvider hashProvider,
                        TypeDescriptionList tdList) {
        this(type, path, hashProvider, tdList, FileSystems.getDefault());
    }
    
    /**
     * Creates a new FileTypeList with a custom file system (useful for testing with Jimfs).
     */
    public FileTypeList(MysterType type,
                        String path,
                        HashProvider hashProvider,
                        TypeDescriptionList tdList,
                        FileSystem fileSystem) {
        this.type = type;
        this.hashProvider = hashProvider;
        this.tdList = tdList;
        this.fileSystem = fileSystem;
        this.pref_key = PREF_KEY + "." + type.toHexString();

        try {
            local_prefs = new MML(MysterPreferences.getInstance().query(pref_key));
        } catch (MMLException ex) {
            local_prefs = new MML();
        }

        rootdir = getPath();
    }

    /** Package protected for unit tests */
    @ProtectedForUnitTests
    void waitForIndexer() {
        synchronized (this) {
            if (indexingFuture == null) {
                return;
            }

            try {
                indexingFuture.get();
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                // ignore - doesn't matter
                e.printStackTrace();
            }
        }

        try {
            INVOKER.waitForThread();
            com.general.util.Util.invokeAndWait(() -> {});
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * returns the isShared flag. If isShared returns true, the list will share files if any are
     * available. If isShared returns false, the list will not show any files shared even if there
     * are file available. Think of it as a sort of sharing over-ride.
     * 
     * @return <code>true</code> is the FileItem List is sharing files; <code>false</code> is
     *         the file list is not sharing files. There might be any files shared even if this
     *         function returns true. If this function returns false it is guarenteed that no files
     *         are being shared.
     */
    public synchronized boolean isShared() {
        String s = local_prefs.get(ACTIVE_PREF);
        if (s == null) {
            // init this
            local_prefs.put(ACTIVE_PREF, "true");
            savePrefs();
            s = local_prefs.get(ACTIVE_PREF);
        }

        return (s.equals("true")); // if s == 1 then return true.
    }

    /**
     * Some lists may be temporarily out of date while they perform and expensive "indexing"
     * operation. call this method to find out if this list is in the middle of indexing...
     * 
     * @return true if this file list has an update pending.
     */
    public synchronized boolean isIndexing() {
        return indexingFuture != null;
    }

    /**
     * Sets the isShared flag. If isShared is set to true, the list will share files if any are
     * available. If isShared is set to false, the list will not show any files shared even if there
     * are file available. Think of it as a sort of sharing over-ride. The effects of this function
     * will not be visible until the directory being pointed to has been indexed. In the mean time,
     * the file list will be empty.
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
        return type; // note: no assertFileList(); file list ins't needed so
        // don't load it.
    }

    /**
     * Gets rootdirectory associated with this object as saved in the preferences. Does not return
     * rootdir variable since th rootdir variable only has the root directory of the files save in
     * the vector list of the files.
     * 
     * @return the root directory associated with this object as saved in the preferences.
     */
    public synchronized String getPath() {
        if (!hasSetPath()) {
            setPath(getDefaultDirectoryPath());
        }
        return local_prefs.get(PATH_PREF);
    }

    /**
     * Returns a list of all shared files. If getShared is false, no filesa are returned.
     * 
     * @return an array of all the shared files
     */
    public synchronized String[] getFileListAsStrings() {
        assertFileList(); // This must be called before working with fileMap or
        // rootdir internal variables.

        return fileMap.keySet().toArray(String[]::new);
    }

    /**
     * Returns a list of all shared files. If getShared is false, no filesa are returned.
     * 
     * @return an array of all the shared file with the hashes
     */
    public synchronized FileItem getFileFromHash(FileHash hash) {
        assertFileList(); // This must be called before working with fileMap or
        // rootdir internal variables.

        for (FileItem item : fileMap.values()) {
            if (isMatch(item, hash))
                return item;
        }

        return null;
    }

    private static boolean isMatch(FileItem item, FileHash hash) {
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
     * @param queryString
     *            string
     * @return a list of files maching the query string
     */
    public synchronized String[] getFileListAsStrings(String queryString) {
        if (queryString.equals(""))
            return getFileListAsStrings(); // not limited by MAX_RESULTS

        assertFileList();

        List<String> results = new ArrayList<>();

        List<String> keywords = new ArrayList<>();
        StringBuffer stringBuffer = new StringBuffer(" ");

        // boolean inWord = false;
        boolean aggregate = false;

        String mergedQueryString = mergePunctuation(queryString);

        // Split queryStr into keywords at the whitespaces into keywords
        // (Anything !Character.isLetterOrDigit() is considered whitespace)
        // Keeping a space as the first character of each keyword forces
        // beginning-of-word matches.

        // TOKENIZZZZEE!!
        for (int i = 0; i < mergedQueryString.length(); i++) {
            char c = mergedQueryString.charAt(i);
            if (c == '\"') {
                if (stringBuffer.charAt(stringBuffer.length() - 1) != ' ') {
                    stringBuffer.append(' ');
                }
                if (stringBuffer.length() > 1) {
                    keywords.add(stringBuffer.toString());
                    stringBuffer = new StringBuffer(" ");
                }
                aggregate = !aggregate;
            } else if (Character.isLetterOrDigit(c)) {
                stringBuffer.append(Character.toLowerCase(c));
            } else {
                // if (t.charAt(t.length()-1) != ' ') t.append(' '); //
                // uncomment to match full words only.
                // for now it matches any begining of words.
                if (stringBuffer.length() > 1 && !aggregate) {
                    keywords.add(stringBuffer.toString());
                    stringBuffer = new StringBuffer(" ");
                } else if (stringBuffer.charAt(stringBuffer.length() - 1) != ' ')
                    stringBuffer.append(' ');
            }
        }
        if (stringBuffer.length() > 1) {
            keywords.add(stringBuffer.toString());
        }

        // MATCHER
        for (FileItem file : fileMap.values()) {
            String filename = mergePunctuation(file.getPath().getFileName().toString());

            // Filter out sequential whitespace
            String simplified = simplify(filename);

            if (isMatch(keywords, simplified))
                results.add(filename);

            if (results.size() > MAX_RESULTS)
                break;
        }

        return results.toArray(String[]::new);
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
    private static boolean isMatch(List<String> keywords, String simplified) {
        for (int iKeyword = 0; iKeyword < keywords.size(); iKeyword++) {
            String keyword = keywords.get(iKeyword);
            if (simplified.indexOf(keyword) == -1)
                return false;
        }
        return true;
    }

    /**
     * Private function used by String[] getFileListAsStrings(String queryStr)
     * 
     * replaces all non letter or digit characters (like: !@#$%^&*()) with spaces to simplify
     * matching
     * 
     * @param filename
     *            filename to simplify
     * @return the simplified string.
     */
    private static String simplify(String filename) {
        StringBuffer simplified = new StringBuffer(255); // pre-allocate some
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
     * Array a java.io.FileItem object from a file name. NOTE: There is a direct mapping between
     * file names and java.io.FileItem objects.
     * 
     * @param fileName
     *            the name of a file to get the File for.
     * @return the java.io.FileItem object corresponding the the query.
     */
    public synchronized FileItem getFileItemFromString(String fileName) {
        // This must be called before working with fileMap or
        // rootdir internal variables.
        assertFileList(); 
        
        // Use HashMap for O(1) lookup instead of linear search
        return fileMap != null ? fileMap.get(fileName) : null;
    }

    /**
     * Returns the number of files
     * 
     * @return the number of files. Returns 0 if getShared() is false.
     */
    public synchronized int getNumOfFiles() {
        // This must be called before working with fileMap or
        // rootdir internal variables.
        assertFileList();

        return fileMap.size();
    }

    /**
     * Sets the root directory in the preferences. The effects of this function will not be visible
     * until the directory being passed has been indexed. In the mean time, the file list will
     * maintain its old list.
     * 
     * the new root dir path.
     */
    public void setPath(String s) { // notice no error if path is nonsence!
        if (s == null) {
            local_prefs.remove(PATH_PREF);
        } else {
            local_prefs.put(PATH_PREF, mergePunctuation(s)); // Change info
        }

        savePrefs();
        assertFileList();
        // notice not root=pref value or anything.. This omition needed to clue
        // assertFileList to rebuild.
    }

    private synchronized void savePrefs() {
        MysterPreferences.getInstance().put(pref_key, local_prefs.toString()); // Change
        // info
    }

    private synchronized void setFileList(List<FileItem> filelist) {
        resetIndexingVariables();
        fileMap = new LinkedHashMap<>();
        if (filelist != null) {
            for (FileItem item : filelist) {
                String filename = mergePunctuation(item.getPath().getFileName().toString());
                fileMap.put(filename, item);
            }
        }
        assertFileList();
    }

    private synchronized void resetIndexingVariables() {
        timeoflastupdate = System.currentTimeMillis();
        indexingFuture = null;
    }

    /**
     * This function makes sure the the fileMap and rootdir variables are up to date. The general
     * design of this object is that things should not happen until they need to. That is, files
     * should not be indexed if there's no one waiting on the index. This function does all the
     * checks and calls necessary to make sure fileMap and rootdir contain the most up-to-date
     * values. This function is also responsible for clearing the fileMap variable when the list has
     * been shared or un-shared. As a general rule it should be called before accessing the fileMap
     * or rootdir variables.
     * 
     */
    private synchronized void assertFileList() {
        if (fileMap == null) {
            fileMap = new LinkedHashMap<>();
        }
        if (!isShared()) { // if file list is not shared make sure list has
            initialized = true;
            
            // length = 0 then continue.
            if (fileMap.size() != 0) {
                fileMap.clear();
            }
            timeoflastupdate = 0; // never updated (we just buggered up the
            // list, you see...)

            if (indexingFuture != null) {
                indexingFuture.cancel();
                indexingFuture = null;
            }
            return;
        }

        // We need to check to see if the user has changed the directory for
        // this type.
        // load the dir for this type
        String workingdir = getPath();

        /*
         * If the directory need indexing or the user has changed the directory to index from, this
         * part of the code will start a new indexing task off.. assuming there's not one already
         * running..
         */
        if (indexingFuture == null && (isOld() || !rootdir.equals(workingdir))) {
            rootdir = workingdir; // in case the dir for this type has changed.
            Path rootPath = fileSystem.getPath(rootdir);
            
            indexingFuture = PromiseFutures
                    .execute(new FileListIndexCall(type, rootPath, hashProvider, tdList))
                    .addFinallyListener(this::resetIndexingVariables)
                    .addFinallyListener(() -> initialized = true)
                    .addResultListener(this::setFileList)
                    .addStandardExceptionHandler()
                    .setInvoker(INVOKER);
        }
    }

    public static class FileListIndexCall implements CancellableCallable<List<FileItem>> {
        private final MysterType type;
        private final Path rootPath;
        private final HashProvider hashProvider;
        private final TypeDescriptionList tdList;
        
        private volatile boolean endFlag = false;

        public FileListIndexCall(MysterType type, Path rootPath, HashProvider hashProvider, TypeDescriptionList tdList) {
            this.type = type;
            this.rootPath = rootPath;
            this.hashProvider = hashProvider;
            this.tdList = tdList;
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.general.thread.CancellableCallable#call()
         */
        public List<FileItem> call() {
            return indexFiles(type, rootPath);
        }

        /**
         * an internal procedure used to do the setup of file indexing. This
         * function is only called in one place at this writing.
         * Uses breadth-first traversal to index files level by level.
         */
        private List<FileItem> indexFiles(MysterType type, Path rootPath) {
            List<FileItem> temp = new ArrayList<>();
            
            if (Files.exists(rootPath) && Files.isDirectory(rootPath)) {
                // Indexes root dir into temp using breadth-first traversal with 5 levels deep.
                indexPathBreadthFirst(type, rootPath, temp, 5); 
            }
            
            Set<FileItem> items = new LinkedHashSet<>(temp);
            
            return new ArrayList<>(items);
        }

        /**
         * Indexes files using breadth-first traversal with Path API. Files are
         * added level by level: first all files in root, then all files one
         * folder deep, then two folders deep, etc.
         * 
         * @param type
         *            the Myster type
         * @param rootPath
         *            the root directory to index
         * @param filelist
         *            the list to add FileItems to
         * @param maxDepth
         *            maximum depth to traverse
         */
        private void indexPathBreadthFirst(MysterType type,
                                           Path rootPath,
                                           List<FileItem> filelist,
                                           int maxDepth) {
            if (!Files.isDirectory(rootPath) || !Files.exists(rootPath)) {
                LOGGER.warning("Invalid path sent to indexPathBreadthFirst: " + rootPath);
                return;
            }

            Queue<Path> pathsToProcess = new LinkedList<>();
            pathsToProcess.add(rootPath);

            while (!pathsToProcess.isEmpty() && !endFlag) {
                Path currentPath = pathsToProcess.poll();

                if (!Files.isDirectory(currentPath)) {
                    throw new IllegalStateException(currentPath + " is not a directory");
                }

                if (currentPath == null) {
                    throw new IllegalStateException("pathsToProcess is empty");
                }

                // because other processes might be screwing with the
                // directories behind our back
                if (!Files.exists(currentPath)) {
                    continue;
                }

                try (Stream<Path> paths = Files.list(currentPath)) {
                    // Process each path in the directory
                    paths.forEach(path -> {
                        if (endFlag)
                            return;

                        try {
                            if (Files.isDirectory(path) && FileItem.extractPathFromFileAndRoot(rootPath, path).size() < maxDepth) {
                                pathsToProcess.add(path);
                            } else {
                                // Add files immediately (files at this depth
                                // level)
                                if (FileFilter.isCorrectType(type, path, tdList)) {
                                    filelist.add(createFileItem(path));
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warning("Error processing path: " + path + " - "
                                    + e.getMessage());
                        }
                    });
                } catch (IOException e) {
                    LOGGER.warning("Error listing directory: " + currentPath + " - "
                            + e.getMessage());
                    continue;
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

        /**
         * Creates a FileItem from a path. Sub classes should over-ride this.
         * 
         * @param path
         *            to be the basis of this FileItem.
         * @return FileItem created from path.
         */
        private FileItem createFileItem(Path path) {
            FileItem fileItem = tdList.getType(StandardTypes.MPG3).equals(type) 
                ? new MPG3FileItem(rootPath, path) 
                : new FileItem(rootPath, path);
            
            hashProvider.findHashNonBlocking(path, new FileHashListener() {
                public void foundHash(FileHashEvent e) {
                    fileItem.setHash(e.getHashes());
                }
            });
            
            return fileItem; 
        }
    }

    /**
     * Returns true of the FileItem List is out of date. This function is called by assertFileList
     * and should oly be called by assertFileList
     */
    long timeoflastupdate = 0; // globalish Needed to make sure the list is not

    // too old.

    // NOTE: The user could also change the DIR to force and update... He could
    // also re-start Myster.
    private synchronized boolean isOld() {
        if (System.currentTimeMillis() - timeoflastupdate > (1000 * 60 * 60))
            return true; // If list is older than 1 hour...
        return false;
    }

    /**
     * Determines what path should be used as the root path. Should only be used by getPath();
     */
    private String getDefaultDirectoryPath() {
        String s = getDefaultDirectory().toAbsolutePath().toString();
        String separator = fileSystem.getSeparator();
        if (!s.endsWith(separator))
            s = s + separator;
        return s;
    }

    /*
     * Suggests a default root directory in the filing system. Should only be used by
     * getDefaultDirectoryPath();
     */
    private synchronized Path getDefaultDirectory() {
        Optional<TypeDescription> td = tdList.get(type);
        String prefix = td.isEmpty() ? "Misc" : td.get().getDescription();
        Path empty = fileSystem.getPath(MysterGlobals.getAppDataPath().getAbsolutePath(), prefix + " Downloads");
        int counter = 1;
        do {
            if (Files.exists(empty)) {
                if (Files.isDirectory(empty))
                    return empty; // here is where the routine should go most of
                // the time.
                else {
                    empty = fileSystem.getPath(type + " Downloads" + counter);
                    counter++;
                    // if (counter>1000) System.exit(0);//bam!
                }
            } else {
                break; // if file doesn't exist go make a dir.
            }
        } while (true);

        try {
            Files.createDirectories(empty);
        } catch (IOException e) {
            LOGGER.warning("Could not create directory: " + empty + " - " + e.getMessage());
        }

        return empty;

    }

    /**
     * Returns true if the path has been initialized, returns false if it hasen't.
     * 
     * @return <code>false</code> if the path in the preferences has been initialized. true
     *         otherwise.
     */
    private boolean hasSetPath() {
        return (local_prefs.get(PATH_PREF) != null);
    }


    /**
     * This function Merges Japaneese punctuation into a form that displays and matches in JAVA This
     * function should be called whenever the name or path of a file is read.
     * 
     * (code submited by heavy_baby@yahoo.co.jp)
     * 
     * @param text
     *            of a filename or path that needs merging.
     * @return String with punctuation merged
     */
    public static String mergePunctuation(String text) {
        // if
        // (Locale.getDefault().getDisplayLanguage().equals(Locale.JAPANESE.getDisplayLanguage())) {
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
        // } else {
        // return text;
        // }
    }

    public synchronized boolean isInitialized() {
        assertFileList();
        
        return initialized;
    }
}