
package com.myster.filemanager;

import java.io.File;
import java.util.Enumeration;
import java.util.zip.*;

import com.myster.type.*;

class FileFilter {
	/*
	// This stuff could be usefull in the future.
	private static final Hashtable types = new Hashtable();

	private static class Entry {
		String[] extensions;
		boolean archivable;
		Entry(String[] extensions, boolean archivable) {
			this.extensions = extensions;
			this.archivable = archivable;
		}
	}
	*/
	
	//This routine is not speed optimal. I don't have enough files to know if this is a problem
	//NOTE: I have modified it to get the extensions from the TypeDescriptionList isn't of 
	//some statically coded stuff
	public static boolean isCorrectType(MysterType type, File file) {
		if (file.length()==0) return false; //all 0k files are bad.

		TypeDescription typeDescription = TypeDescriptionList.getDefault().get(type);
		if (typeDescription == null) return true; //no information on this type, allow everything.
		String[] extensions = typeDescription.getExtensions(); //getExtensions is slow so we only want to exce it once.
		if (extensions == null) return true;//no information on this type, allow everything.
		
		if (hasExtension(file.getName(), extensions))//entry.extensions))
			return true;

		if (typeDescription.isArchived()) {
			try {
				ZipFile zipFile = new ZipFile(file);
				try {
					for(Enumeration entries = zipFile.entries(); entries.hasMoreElements(); ) {
						ZipEntry zipEntry = (ZipEntry)entries.nextElement();
						if (hasExtension(zipEntry.getName(), extensions))
							return true;
					}
				} finally { zipFile.close(); }
			} catch(java.io.IOException e) { return false; }
		}
		return false;
	}

	private static boolean hasExtension(String filename, String[] extensions) {
		String lowFilename = filename.toLowerCase();
		for(int i=0; i<extensions.length; i++)
			if (lowFilename.endsWith(extensions[i])) 
				return true;
		return false;
	}
}

