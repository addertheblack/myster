package com.myster.filemanager;

import helliker.id3.ID3v2FormatException;
import helliker.id3.MP3File;

import java.io.File;

import com.myster.mml.MML;

/**
 * This class implements the different data needed by the MPG3 files.
 */
public class MPG3FileItem extends FileItem {
    private MML mmlRepresentation;

    /**
     *  
     */
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

    //ugh.. for Mp3 stuff
    public static void patchFunction2(MML mml, File file) {
        MP3File mp3File = null;
        try {
            mp3File = new MP3File(file);
        } catch (Exception ex) {
            return;
        }

        if (file.getName().endsWith(".mp3")) {
            mml.put("/BitRate", "" + (mp3File.getBitRate()*1000));
            mml.put("/Hz", "" + mp3File.getSampleRate());
        }

        try {
            String temp = mp3File.getTitle();
            if (temp != null && !temp.equals("")) {
                mml.put("/ID3Name", temp);
            } else {
                temp = mp3File.getTrackString();
                if (temp != null && !temp.equals("")) {
                    mml.put("/ID3Name", temp);
                }
            }
        } catch (ID3v2FormatException ex) {
        }

        try {
            String temp = mp3File.getComposer();
            if (temp != null && !temp.equals("")) {
                mml.put("/Artist", temp);
            } else {
                temp = mp3File.getArtist();
                if (temp != null && !temp.equals("")) {
                    mml.put("/Artist", temp);
                }
            }
        } catch (ID3v2FormatException ex) {
        }

        try {
            String temp = mp3File.getAlbum();
            if (temp != null && !temp.equals("")) {
                mml.put("/Album", temp);
            }
        } catch (ID3v2FormatException ex) {
        }
        // System.out.println(""+tag);
    }

//    // ugh.. mp3 stuff
//    private static void patchFunction(MML mml, File file) {
//        MP3Header head = null;
//        try {
//            head = new MP3Header(file);
//        } catch (Exception ex) {
//            return;
//        }
//
//        mml.put("/BitRate", "" + head.getBitRate());
//        mml.put("/Hz", "" + head.getSamplingRate());
//
//        String temp = head.getMP3Name();
//        if (temp != null) {
//            mml.put("/ID3Name", temp);
//        } else {
//            patchFunction2(mml, file);
//            return;
//        }
//
//        temp = head.getArtist();
//        if (temp != null) {
//            mml.put("/Artist", temp);
//        }
//
//        temp = head.getAlbum();
//        if (temp != null) {
//            mml.put("/Album", temp);
//        }
//
//        head = null; //go get 'em GC...
//    }
}