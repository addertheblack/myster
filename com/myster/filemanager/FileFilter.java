
package com.myster.filemanager;

import java.io.File;
import java.util.Hashtable;
import com.myster.type.MysterType;
import java.util.Enumeration;
import java.util.zip.*;

class FileFilter {
	
	private static final Hashtable types = new Hashtable();

	private static class Entry {
		String[] extensions;
		boolean archivable;
		Entry(String[] extensions, boolean archivable) {
			this.extensions = extensions;
			this.archivable = archivable;
		}
	}
	
	static {
		types.put(new MysterType("MPG3"), 
							new Entry(new String[]{
								".mp3",".mid",".midi",".gm",".au",".wav",".ogg",".snd",
								".mod",".s3m",".aif",".aiff",".aiff1",".m3d",".mpeghdr",
								".iff","aifc",".avr",".adpcm",".gsm",".sf",".kar",".s3m",
								".mtm",".swa",".m1a",".m2a",".mp2",".paf",".wve",".voc",".ra",".m4a"
							}, false));
		types.put(new MysterType("MooV"),
							new Entry(new String[]{
								".mov",".avi",".asf",".rm",".ram",".mpg",".mp4",".mpeg",".3iv",
								".div",".xiv",".rm",".mpe",".swf",".wmv",".mp2v",".mlv",".mpv",
								".wm",".vob",".ifo"
							}, false));
		types.put(new MysterType("TEXT"), 
							new Entry(new String[]{
								".txt",".doc",".abw",".rtf",".pdf",".ps",".htm",".html",".xml",".wp4"
							}, false));
		types.put(new MysterType("PICT"),
							new Entry(new String[]{
								".jpg",".jpeg",".png",".gif",".jpe",".tif",".tiff",".bmp",
								".jp2",".ps",".psp",".eps",".xpm",".tga",".xcf",".pict"}, false));
		types.put(new MysterType("MACS"), 
							new Entry(new String[]{
								".bin",".hqx",".sit",".cpt",".dmg",".sea",".nfs",".sitx",".image",
								".img",".pkg",".smi"}, false));
		types.put(new MysterType("WINT"), 
							new Entry(new String[]{
								".exe",".zip",".gz",".tar",".z",".rmj",".lqt",".iso",".cue",
								".iso",".ccd",".rar",".ace",".cdr",".gzip",".lzh",".lha"}, false));
		types.put(new MysterType("ROMS"), 
							new Entry(new String[]{
								".rom",".smc",".n64",".v64",".gba",".gb",".mod",".nes",".dsk", ".fig", ".sfc", ".swc", ".058", ".078"
							}, true));
		types.put(new MysterType("RING"), 
							new Entry(new String[]{
								".csx", ".gsm", ".qcp", ".jmp", ".mid", ".midi", ".mmd"
							}, false));
	}
	
	public static boolean isCorrectType(MysterType type, File file) {
		if (file.length()==0) return false; //all 0k files are bad.
		Entry entry = (Entry)types.get(type);
		if (hasExtension(file.getName(), entry.extensions))
			return true;

		if (entry.archivable)
		{
			try
			{
				ZipFile zipFile = new ZipFile(file);
				try
				{
					for(Enumeration entries = zipFile.entries(); entries.hasMoreElements(); )
					{
						ZipEntry zipEntry = (ZipEntry)entries.nextElement();
						if (hasExtension(zipEntry.getName(), entry.extensions))
							return true;
					}
				} finally { zipFile.close(); }
			}
			catch(java.io.IOException e) { return false; }
		}
		return false;
	}

	private static boolean hasExtension(String filename, String[] extensions)
	{
		String lowFilename = filename.toLowerCase();
		for(int i=0; i<extensions.length; i++)
			if (lowFilename.endsWith(extensions[i])) 
				return true;
		return false;
	}
}

