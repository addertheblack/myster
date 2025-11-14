package com.myster.filemanager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;

class FileFilter {
    /*
     * // This stuff could be usefull in the future. private static final
     * Hashtable types = new Hashtable();
     * 
     * private static class Entry { String[] extensions; boolean archivable;
     * Entry(String[] extensions, boolean archivable) { this.extensions =
     * extensions; this.archivable = archivable; } }
     */

    //This routine is not speed optimal. I don't have enough files to know if
    // this is a problem
    //NOTE: I have modified it to get the extensions from the
    // TypeDescriptionList isn't of
    //some statically coded stuff
    
    /**
     * Path-based version (preferred)
     */
    public static boolean isCorrectType(MysterType type, Path path, TypeDescriptionList tdList) {
        try {
            if (Files.size(path) == 0) {
                return false; //all 0k files are bad.
            }
            String fileName = path.getFileName().toString();
            if (fileName.startsWith(".")) {
                return false; // hidden files should stay hidden
            }

            Optional<TypeDescription> typeDescriptionOptional = tdList.get(type);
            if (typeDescriptionOptional.isEmpty())
                return true; //no information on this type, allow everything.
            
            TypeDescription typeDescription = typeDescriptionOptional.get();
            String[] extensions = typeDescription.getExtensions();
            if (extensions.length == 0)
                return true;//no information on this type, allow everything.

            if (hasExtension(fileName, extensions))
                return true;

            if (typeDescription.isArchived() && isArchive(fileName)) {
                try {
                    ZipFile zipFile = new ZipFile(path.toFile());
                    try {
                        for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries
                                .hasMoreElements();) {
                            ZipEntry zipEntry = entries.nextElement();
                            if (hasExtension(zipEntry.getName(), extensions))
                                return true;
                        }
                    } finally {
                        zipFile.close();
                    }
                } catch (java.io.IOException e) {
                    return false;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean hasExtension(String filename, String[] extensions) {
        String lowFilename = filename.toLowerCase();
        for (int i = 0; i < extensions.length; i++)
            if (lowFilename.endsWith(extensions[i]))
                return true;
        return false;
    }
    
    private static boolean isArchive(String p_fileName) {
        String fileName = p_fileName.toLowerCase();
        return fileName.startsWith(".zip") || fileName.startsWith(".gz") || fileName.startsWith(".gzip");
    }
}

