
package com.myster.filemanager;

import java.io.File;

class FileFilter {
	private static final int MPG3=0;
	private static final int MooV=1;
	private static final int TEXT=2;
	private static final int PICT=3;
	private static final int MACS=4;
	private static final int WINT=5;
	
	private static String[][] fileextensions=new String[][]{
		new String[]{
			".mp3",".mid",".midi",".gm",".au",".wav",".ogg", ".snd",
			".mod",".s3m",".aif",".aiff",".aiff1",".m3d",".mpeghdr",
			".iff","aifc",".avr",".adpcm",".gsm",".sf",".kar",".s3m",
			".mtm",".swa",".m1a",".m2a",".mp2",".paf",".wve",".voc",".ra"
		},
		new String[]{
			".mov",".avi",".asf",".rm",".ram",".mpg",".mp4",".mpeg",".3iv"
		},
		new String[]{
			".txt",".doc",".abw",".rtf", ".pdf",".ps",".htm",
			".html",".xml",".wp4"
		},
		new String[]{
			".jpg",".jpeg",".gif",".png",".pict",".psd",".tif",".tiff",".bmp"
		},
		new String[]{
			".bin",".hqx",".sit",".cpt",".dmg",".sea",".nfs"
		},
		new String[]{
			".zip",".exe",".rar",".lzh",".iso"
		}
	};
	
	public static boolean isCorrectType(String type, File file) {
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

