package com.myster.filemanager;

import java.io.File;

import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.Mp3File;

//import org.farng.mp3.MP3File;
//import org.farng.mp3.id3.AbstractID3;

import com.myster.mml.MML;

/**
 * This class implements the different data needed by the MPG3 files.
 */
public class MPG3FileItem extends FileItem {
    private MML mmlRepresentation;

    public MPG3FileItem(File file) {
        super(file);
    }

    public synchronized MML getMMLRepresentation() {
        if (mmlRepresentation != null)
            return mmlRepresentation;

        mmlRepresentation = super.getMMLRepresentation();

        patchFunction2(mmlRepresentation, getFile());
        return mmlRepresentation;
    }

    // ugh.. for Mp3 stuff
    public static void patchFunction2(MML mml, File file) {
        Mp3File mp3File = null;
        try {
            mp3File = new Mp3File(file, 4096, false);
        } catch (Throwable ex) {
            System.err.println("Could not read ID3 tag info for: " + file);
            return;
        }

        if (file.getName().endsWith(".mp3")) {
            mml.put("/BitRate", "" + (mp3File.getBitrate() * 1000));
            mml.put("/Hz", "" + mp3File.getSampleRate());
            mml.put("/Vbr", "" + mp3File.isVbr());
        }

        ID3v1 id3Tag = mp3File.getId3v2Tag();
        if (id3Tag == null) {
            id3Tag = mp3File.getId3v1Tag();
            if (id3Tag == null) {
                return;
            }
        }
        
        String temp = id3Tag.getTitle();
        if (temp != null && !temp.equals("")) {
            mml.put("/ID3Name", temp);
        }

        String temp2 = id3Tag.getArtist();
        if (temp2 != null && !temp2.equals("")) {
            mml.put("/Artist", temp2);
        } else {
//            temp2 = id3Tag.getOriginalArtist();
//            if (temp2 != null && !temp2.equals("")) {
//                mml.put("/Artist", temp2);
//            }
        }

        String temp1 = id3Tag.getAlbum();
        if (temp1 != null && !temp1.equals("")) {
            mml.put("/Album", temp1);
        }
    }
}