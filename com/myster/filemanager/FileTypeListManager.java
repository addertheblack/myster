/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

/**
*	The FileTypeListManager, Better known simply as the the FileManager provides a 
*	set of standard interfaces for accessing the user's file library.
*<p>
* 	From a design perspective the File Manager is a fascade object that manages all the 
*	independent File Lists, one for each Myster type.
*<p>
*	All public methods in this Object can be considered stable and available to any plugin
*	writter.
*/

package com.myster.filemanager;


import java.io.*;
import com.myster.pref.Preferences;
import java.util.Vector;
import com.myster.filemanager.ui.FMIChooser;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeDescription;
import com.myster.type.MysterType;
import com.myster.hash.FileHash;


public class FileTypeListManager{
	private static FileTypeListManager f;				//For singleton.
	private FileTypeList filelist[];					//An array of all file lists. 1 Per type.
	
	public static String PATH="/File Lists/";			//Path the File Lists information is stored in the prefs.
														//Each File List decides what information it will store
														//under this path.
	
	
	/**
	*	Gets an instance of the current File manager. This routine uses a varient of the singleton
	*   desing pattern with dynamic loading.
	*/
	public synchronized static FileTypeListManager getInstance() {
		if (f==null) f=new FileTypeListManager();
		return f;
	}
	
	/*
	*	Private Constructor. Does nothing except call initFileTypeListManager() routine.
	*/
	private FileTypeListManager() {
		initFileTypeListManager();
		
		Preferences.getInstance().addPanel(new FMIChooser(this));
	}
	
	
	/*
	*	Loads the list of types using the loadTypeAndDescriptionList routine and creates a new
	*	FileTypeList for each of the types. So we have a type->FileList behavior..
	*	This routine is only called by FileTypeListManager().
	*/
	private void initFileTypeListManager() {
		TypeDescription[] list = TypeDescriptionList.getDefault().getEnabledTypes();
		filelist=new FileTypeList[list.length];
		for (int i=0;i<list.length;i++) {
			filelist[i]=new FileTypeList(list[i].getType(), PATH);	//This code is redundent with... This code.
		} 
	}
	
	/*
	*	Gets a File Type List from a type. This routine is only used internally. The reasone it is not 
	*	accessable is to hide implementaion details of the FileManager. That and to keep the FileManager's interface
	*	as simple as possible
	*
	*	@param   type   a Myster file type as a String.
    *	@return  The FileTypeList for that type if it exists; null otherwise.
    *	@since   JDK1.0
	*/
	private FileTypeList getFileTypeList(MysterType type) {
		for (int i=0; i<filelist.length; i++) {
			if (filelist[i].getType().equals(type)) return filelist[i]; 
		}
		return null;
	}
	
	/**
	*	Can detect if the file type is known by the FileManager. (Does not return false if type is not shared!)
	*
	*	@param	a[] a Myster file type as a byte[4].
	* 	@return	<code>true</code> is the file type has a corresponding FileTypeList object;
	*			<code>false</code> otherwise. returns true even if type has been set to "not shared".
	*/
	public boolean isAMember(MysterType type) {
		if (getFileTypeList(type)==null) return false;
		return true;
	}
	
	/**
	*	Gets the Names or all the files currently available for download under this type. NOTE: The function
	*	call name of getDirList(byte a[]) is somewhat misleading as it does not list a directory but all files
	*	For a type. The reasone for the odd name is historical and has to do with the original paradigm being that
	*	all Myster servers are 2 level directory structures with the first level being type and the second level
	*	being the files inside that type. Hence the name and the parameters.
	*<p>
	*	NOTE: In the Myster protocol, when files are listed, these file names are not so much file names as
	*	a way of identifying a unique file given a type.
	*
	*	@param	a MysterType
	*	@return	an array of Myster file identifiers. In this implementation, a list of file names. (else null if invalid fil type)
	*/
	public String[] getDirList(MysterType type) {
		FileTypeList list=getFileTypeList(type);
		if (list==null) return null;	//err.
		
		return list.getFileListAsStrings(); //FileTypeList should do the processing for this.
											//It should return an ARRAY! full of file names! No Spaces!
	}
	

    public String[] getDirList(MysterType type, String queryStr) {
        FileTypeList list = getFileTypeList(type);
        if (list == null) return null;
        return list.getFileListAsStrings(queryStr);
    }
    
    
    /**
	*	Gets a list of file reference strings for a type and an array of
	*	file hashes. A file will be retruned only if it contains all
	*	file hashes.
	*
	*	@param	a MysterType
	*	@return	an array of Myster file identifiers. In this implementation, a list of file names. (else null if invalid fil type)
	*/
    public FileItem getFileFromHash(MysterType type, FileHash hashes) {
    	FileTypeList list = getFileTypeList(type);
        if (list == null) return null;
        return list.getFileFromHash(hashes);
    }


	/**
	*	Gets a java.io.File object from a Myster type and Unique file identifyer string (a file name).
	*
	*	NOTE: In the Myster protocol, when files are listed, these file names are not so much file names as
	*	a way of identifying a unique file given a type.
	*
	*	@param	a[] a Myster file type as a byte[4]; a 
	*	@param	Unique file identifyer string
	*	@return	a java.io.File object that points to the File in question.
	*/
	public File getFile(MysterType type, String s) {
		FileTypeList list=getFileTypeList(type);
		if (list==null) return null;	//err.
		
		FileItem fileItem = list.getFileItemFromString(s);
		
		if (fileItem == null) return null;
		
		return fileItem.getFile();
	}
	
	/**
	*	Gets a com.myster.filemanager.FileItem object from a Myster type and Unique file identifyer string (a file name).
	*<p>
	*	NOTE: In the Myster protocol, when files are listed, these file names are not so much file names as
	*	a way of identifying a unique file given a type.
	*
	*	@param	MysterType of the file you want to get
	*	@param	Unique file identifyer string
	*	@return	a java.io.File object that points to the File in question.
	*/
	public FileItem getFileItem(MysterType type, String s) {
		FileTypeList list=getFileTypeList(type);
		if (list==null) return null;	//err.
		
		return list.getFileItemFromString(s);
	}
	
	/**
	*	Gets a listing of known, shared file types. Types returned here are not gurenteed to contain any files,
	*	only that the type is known and has a "shared" of true. (Myster allows the users the option of not sharing a type.
	*	Types that aren't shared are still known by the FileManager but are not shared and so don't appear here.)
	*
	*	@return	a String[] of shared Myster file types.
	*/
	public MysterType[] getFileTypeListing() {
		//This routine uses the old vector copied to an array trick, since the number of shared Items is not known until later
		//so the list is put into a vector intialy then copied to an array.
		Vector workinglist=new Vector(filelist.length);	//since the size of the final vector will always be <= filelist.length.
		
		for (int i=0; i<filelist.length;i++) {
			if (filelist[i].isShared()) workinglist.addElement(filelist[i].getType());
		}
		
		MysterType[] array=new MysterType[workinglist.size()];
		for (int i=0; i<array.length; i++) {
			array[i]=((MysterType)(workinglist.elementAt(i)));
		}
		return array;
	}
	
	/**
	*	Gets the total number of shared files for a given type. Returns 0 if type is unknown to File Manager.
	*
	*	@param	type a Myster file type as a String
	*	@return	number of shared files for a type.
	*/
	public int getNumberOfFiles(MysterType type) {
		FileTypeList list=getFileTypeList(type);
		if (list==null) return 0;	//err.
		
		return list.getNumOfFiles();
	}
	
	/**
	*	Gets the root directory path for a given type. The command is ignore if the type is not known by the File Manager.
	*
	*	@param	type a Myster file type
	*	@return	a path in the host filing system.
	*/
	public String getPathFromType(MysterType type) {
		FileTypeList temp=getFileTypeList(type);
		if (temp==null) return null;
		String path=temp.getPath();
		return path;
	}
	
	/**
	*	Sets the root directory path for a given type. The command is ignore if the type is not known by the File Manager.
	*
	*	@param	type a Myster file type
	*	@param	a path in the host filing system.
	*/
	public void setPathFromType(MysterType type, String path) {
		FileTypeList temp=getFileTypeList(type);
		if (temp==null) return;
		temp.setPath(path);
	}

	/**
	*	Users might want to disable sharing of a type while keeping the same directory. This function
	*	enables of diables sharing of a type. If the type doesn't exist, the command is ignored.
	*
	*	@param	type a Myster file type to apply the boolean to
	*	@param	b a boolean value to share or unshare the type list. true shares the list, false unshares it.
	*/
	public void setShared(MysterType type, boolean b) {
		FileTypeList list=getFileTypeList(type);
		if (list==null) return;	//err.
		
		list.setShared(b);
	}
	
	/**
	*	Users might want to disable sharing of a type while keeping the same directory. This function
	*	enables of diables sharing of a type. If the type doesn't exist, the command is ignored.
	*
	*	@param	type a Myster file type to apply the boolean to
	*	@return	true if the type is shared, false if the type is not shared or isn't known by the File Manager.
	*/
	public boolean isShared(MysterType type) {
		FileTypeList list=getFileTypeList(type);
		if (list==null) return false;	//err.
		
		return list.isShared();	
	} 

}
