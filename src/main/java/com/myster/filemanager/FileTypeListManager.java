/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

/**
 * The FileTypeListManager, Better known simply as the the FileManager provides
 * a set of standard interfaces for accessing the user's file library.
 * <p>
 * From a design perspective the File Manager is a fascade object that manages
 * all the independent File Lists, one for each Myster type.
 * <p>
 * All public methods in this Object can be considered stable and available to
 * any plugin writter.
 */

package com.myster.filemanager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.myster.hash.FileHash;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionEvent;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeListener;

public class FileTypeListManager {
    private final Map<MysterType, FileTypeList> fileListMap; // Map of type -> FileTypeList
    private final HashProvider hashProvider;
    private final TypeDescriptionList tdList;

    public static final String PATH = "/File Lists/"; //Path the File Lists

    /**
     * Constructor that initializes the FileTypeListManager and registers
     * as a listener for type enable/disable events.
     */
    public FileTypeListManager(HashProvider hashProvider, TypeDescriptionList tdList) {
        this.hashProvider = hashProvider;
        this.tdList = tdList;
        this.fileListMap = new HashMap<>();

        // Initialize file lists for all currently enabled types
        TypeDescription[] list = tdList.getEnabledTypes();
        for (TypeDescription typeDesc : list) {
            MysterType type = typeDesc.getType();
            FileTypeList fileList = new FileTypeList(type, PATH, hashProvider, tdList);
            fileList.getNumOfFiles(); // This forces the list to load
            fileListMap.put(type, fileList);
        }

        // Register as listener for future type changes
        tdList.addTypeListener(new TypeListenerImpl());
    }


    /**
     * Gets a File Type List from a type. This routine is only used internally.
     * The reason it is not accessible is to hide implementation details of the
     * FileManager. That and to keep the FileManager's interface as simple as
     * possible
     * 
     * @param type a Myster file type as a String. @return The FileTypeList for
     * that type if it exists; null otherwise.
     */
    public FileTypeList getFileTypeList(MysterType type) {
        return fileListMap.get(type);
    }

    /**
     * Can detect if the file type is known by the FileManager. (Does not return
     * false if type is not shared!)
     * 
     * @param type
     *            a Myster file type
     * @return <code>true</code> is the file type has a corresponding
     *         FileTypeList object; <code>false</code> otherwise. returns true
     *         even if type has been set to "not shared".
     */
    public boolean isAMember(MysterType type) {
        if (getFileTypeList(type) == null)
            return false;
        return true;
    }

    /**
     * Gets the Names or all the files currently available for download under
     * this type. NOTE: The function call name of getDirList(byte a[]) is
     * somewhat misleading as it does not list a directory but all files For a
     * type. The reasone for the odd name is historical and has to do with the
     * original paradigm being that all Myster servers are 2 level directory
     * structures with the first level being type and the second level being the
     * files inside that type. Hence the name and the parameters.
     * <p>
     * NOTE: In the Myster protocol, when files are listed, these file names are
     * not so much file names as a way of identifying a unique file given a
     * type.
     * 
     * @param type
     *            MysterType
     * @return an array of Myster file identifiers. In this implementation, a
     *         list of file names. (else null if invalid fil type)
     */
    public String[] getDirList(MysterType type) {
        FileTypeList list = getFileTypeList(type);
        if (list == null)
            return null; //err.

        return list.getFileListAsStrings(); //FileTypeList should do the
        // processing for this.
        //It should return an ARRAY! full of file names! No Spaces!
    }

    public String[] getDirList(MysterType type, String queryStr) {
        FileTypeList list = getFileTypeList(type);
        if (list == null)
            return null;
        return list.getFileListAsStrings(queryStr);
    }

    /**
     * Gets a list of file reference strings for a type and an array of file
     * hashes. A file will be retruned only if it contains all file hashes.
     * 
     * @param type
     *            MysterType
     * @param hashes
     *            hashes to look for
     * @return an array of Myster file identifiers. In this implementation, a
     *         list of file names. (else null if invalid fil type)
     */
    public FileItem getFileFromHash(MysterType type, FileHash hashes) {
        FileTypeList list = getFileTypeList(type);
        if (list == null)
            return null;

        FileItem item = list.getFileFromHash(hashes);
        if (item==null) {
            return null;
        }
     
        // can't do this because it is too slow
        // Do not enable pls
//        if (!Files.exists(item.getPath())) {
//            return null;
//        }

        return item;
    }

    /**
     * Gets a java.io.File object from a Myster type and Unique file identifyer
     * string (a file name).
     * <p>
     * <b>
     * NOTE: In the Myster protocol, when files are listed, these file names are
     * not so much file names as a way of identifying a unique file given a
     * type.</b>
     * 
     * @param type
     *            a Myster file type
     * @param fileNameReference
     *            Unique file identifyer string
     * @return a java.io.File object that points to the File in question.
     */
    public File getFile(MysterType type, String fileNameReference) {
        FileTypeList list = getFileTypeList(type);
        if (list == null)
            return null; //err.

        FileItem fileItem = list.getFileItemFromString(fileNameReference);

        if (fileItem == null)
            return null;

        return fileItem.getFile();
    }

    /**
     * Gets a com.myster.filemanager.FileItem object from a Myster type and
     * Unique file identifier string (a file name).
     * <p>
     * <b>
     * NOTE: In the Myster protocol, when files are listed, these file names are
     * not so much file names as a way of identifying a unique file given a
     * type.</b>
     * 
     * @param type
     *            of the file you want to get
     * @param fileNameReference
     *            Unique file identifier string
     * @return a java.io.File object that points to the File in question.
     */
    public FileItem getFileItem(MysterType type, String fileNameReference) {
        FileTypeList list = getFileTypeList(type);
        if (list == null)
            return null; //err.

        return list.getFileItemFromString(fileNameReference);
    }

    /**
     * Gets a listing of known, shared file types. Types returned here are not
     * gurenteed to contain any files, only that the type is known and has a
     * "shared" of true. (Myster allows the users the option of not sharing a
     * type. Types that aren't shared are still known by the FileManager but are
     * not shared and so don't appear here.)
     * 
     * @return a String[] of shared Myster file types.
     */
    public MysterType[] getFileTypeListing() {
        return fileListMap.values().stream()
                .filter(FileTypeList::isShared)
                .map(FileTypeList::getType)
                .toArray(MysterType[]::new);
    }

    /**
     * Gets the total number of shared files for a given type. Returns 0 if type
     * is unknown to File Manager.
     * 
     * @param type
     *            a Myster file type as a String
     * @return number of shared files for a type.
     */
    public int getNumberOfFiles(MysterType type) {
        FileTypeList list = getFileTypeList(type);
        if (list == null)
            return 0; //err.

        return list.getNumOfFiles();
    }

    /**
     * Gets the root directory path for a given type. The command is ignore if
     * the type is not known by the File Manager.
     * 
     * @param type
     *            a Myster file type
     * @return a path in the host filing system.
     */
    public String getPathFromType(MysterType type) {
        FileTypeList temp = getFileTypeList(type);
        if (temp == null)
            return null;
        String path = temp.getPath();
        return path;
    }

    /**
     * Sets the root directory path for a given type. The command is ignore if
     * the type is not known by the File Manager.
     * 
     * @param type
     *            a Myster file type
     * @param path
     *            path in the host filing system.
     */
    public void setPathFromType(MysterType type, String path) {
        FileTypeList temp = getFileTypeList(type);
        if (temp == null)
            return;
        temp.setPath(path);
    }

    /**
     * Users might want to disable sharing of a type while keeping the same
     * directory. This function enables of diables sharing of a type. If the
     * type doesn't exist, the command is ignored.
     * 
     * @param type
     *            a Myster file type to apply the boolean to
     * @param b
     *            a boolean value to share or unshare the type list. true shares
     *            the list, false unshares it.
     */
    public void setShared(MysterType type, boolean b) {
        FileTypeList list = getFileTypeList(type);
        if (list == null)
            return; //err.

        list.setShared(b);
    }

    /**
     * Users might want to disable sharing of a type while keeping the same
     * directory. This function enables or disables sharing of a type. If the
     * type doesn't exist, the command is ignored.
     * 
     * @param type
     *            a Myster file type to apply the boolean to
     * @return true if the type is shared, false if the type is not shared or
     *         isn't known by the File Manager.
     */
    public boolean isShared(MysterType type) {
        FileTypeList list = getFileTypeList(type);
        if (list == null)
            return false; //err.

        return list.isShared();
    }

    
    public boolean hasInitialized(MysterType type) {
        FileTypeList list = getFileTypeList(type);
        if (list == null)
            return false; // err.

        return list.isInitialized();
    }

    /**
     * Called when a type is enabled in the TypeDescriptionList.
     * Creates a new FileTypeList for the newly enabled type.
     */
    private void typeEnabled(TypeDescriptionEvent e) {
        MysterType type = e.getType();

        // Check if we already have this type (shouldn't happen, but defensive)
        if (fileListMap.containsKey(type)) {
            return;
        }

        // Create and initialize new FileTypeList
        FileTypeList fileList = new FileTypeList(type, PATH, hashProvider, tdList);
        fileList.getNumOfFiles(); // This forces the list to load
        fileListMap.put(type, fileList);
    }

    /**
     * Called when a type is disabled in the TypeDescriptionList.
     * Removes the FileTypeList for the disabled type.
     */
    private void typeDisabled(TypeDescriptionEvent e) {
        MysterType type = e.getType();

        // Remove the file list from our map
        fileListMap.remove(type);
    }

    private class TypeListenerImpl implements TypeListener {
        @Override
        public void typeEnabled(TypeDescriptionEvent e) {
            FileTypeListManager.this.typeEnabled(e);
        }

        @Override
        public void typeDisabled(TypeDescriptionEvent e) {
            FileTypeListManager.this.typeDisabled(e);
        }
    }
}

