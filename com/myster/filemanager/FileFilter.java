
package com.myster.filemanager;

import java.io.File;

import com.myster.type.MysterType;

class FileFilter {
	private static final int MPG3=0;
	private static final int MooV=1;
	private static final int TEXT=2;
	private static final int PICT=3;
	private static final int MACS=4;
	private static final int WINT=5;
	private static final int ROMS=6;
	private static final int RING=7;
	
	private static String[][] fileextensions=new String[][]{
		new String[]{
			".mp3",".mid",".midi",".gm",".au",".wav",".ogg",".snd",
			".mod",".s3m",".aif",".aiff",".aiff1",".m3d",".mpeghdr",
			".iff","aifc",".avr",".adpcm",".gsm",".sf",".kar",".s3m",
			".mtm",".swa",".m1a",".m2a",".mp2",".paf",".wve",".voc",".ra",".m4a"
		},
		new String[]{
			".mov",".avi",".asf",".rm",".ram",".mpg",".mp4",".mpeg",".3iv",
                        ".div",".xiv",".rm",".mpe",".swf",".wmv",".mp2v",".mlv",".mpv",
                        ".wm",".vob",".ifo"
                },
		new String[]{
                        ".txt",".doc",".abw",".rtf",".pdf",".ps",".htm",".html",".xml",".wp4"
		},
		new String[]{
                        ".jpg",".jpeg",".png",".gif",".jpe",".tif",".tiff",".bmp",
                        ".jp2",".ps",".psp",".eps",".xpm",".tga",".xcf",".pict"
		},
		new String[]{
                        ".bin",".hqx",".sit",".cpt",".dmg",".sea",".nfs",".sitx",".image",
                        ".img",".pkg",".smi"
		},
		new String[]{
                        ".exe",".zip",".gz",".tar",".z",".rmj",".lqt",".iso",".cue",
                        ".iso",".ccd",".rar",".ace",".cdr",".gzip",".lzh",".lha"
		},
		new String[]{
                        ".rom",".smc",".n64",".v64",".gba",".gb",".mod",".nes",".dsk"
		},
		new String[]{
                        ".csx", ".gsm", ".qcp", ".jmp", ".mid", ".midi", ".mmd"
		}
	};
	
	public static boolean isCorrectType(MysterType type, File file) {
		if (file.length()==0) return false; //all 0k files are bad.
		
		if (type.equals("MPG3")) {
			return getThingy(MPG3, file);
		} else if (type.equals("MooV")) {
			return getThingy(MooV, file);
		} else if (type.equals("TEXT")) {
			return getThingy(TEXT, file);
		} else if (type.equals("PICT")) {
			return getThingy(PICT, file);
		} else if (type.equals("MACS")) {
			return getThingy(MACS, file);
		} else if (type.equals("WINT")) {
			return getThingy(WINT, file);
		} else if (type.equals("ROMS")) {
			return getThingy(ROMS, file);
		}
		
		return !file.getName().endsWith(".i");
	}
	
	private static boolean getThingy(int type, File file) {
		//if (file.getName().indexOf(".")==-1) return false; 
		
		String fileName=file.getName().toLowerCase();
		
		for (int i=0; i<fileextensions[type].length; i++) {
			if (fileName.endsWith(fileextensions[type][i])) return true;
		}
		return false;
	}


}

