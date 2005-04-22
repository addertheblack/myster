package com.myster.filemanager;

import helliker.id3.ID3v2FormatException;
import helliker.id3.ID3v2Frames;
import helliker.id3.ID3v2Tag;

import java.io.File;

import com.myster.mml.MML;
import com.myster.util.MP3Header;

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
        ID3v2Tag tag = null;
        try {
            tag = new ID3v2Tag(file, 0);
        } catch (Exception ex) {
            patchFunction(mml, file);
            return;
        }

        try {
            String temp = tag.getFrameDataString(ID3v2Frames.TITLE);
            if (temp != null && !temp.equals("")) {
                mml.put("/ID3Name", temp);
            }
        } catch (ID3v2FormatException ex) {
        }

        try {
            String temp = tag.getFrameDataString(ID3v2Frames.COMPOSER);
            if (temp != null && !temp.equals("")) {
                mml.put("/Artist", temp);
            } else {
                temp = tag.getFrameDataString(ID3v2Frames.LEAD_PERFORMERS);
                if (temp != null && !temp.equals("")) {
                    mml.put("/Artist", temp);
                }
            }
        } catch (ID3v2FormatException ex) {
        }

        try {
            String temp = tag.getFrameDataString(ID3v2Frames.ALBUM);
            if (temp != null && !temp.equals("")) {
                mml.put("/Album", temp);
            }
        } catch (ID3v2FormatException ex) {
        }

        // System.out.println(""+tag);

    }

    // ugh.. mp3 stuff
    private static void patchFunction(MML mml, File file) {
        MP3Header head = null;
        try {
            head = new MP3Header(file);
        } catch (Exception ex) {
            return;
        }

        mml.put("/BitRate", "" + head.getBitRate());
        mml.put("/Hz", "" + head.getSamplingRate());

        String temp = head.getMP3Name();
        if (temp != null) {
            mml.put("/ID3Name", temp);
        } else {
            patchFunction2(mml, file);
            return;
        }

        temp = head.getArtist();
        if (temp != null) {
            mml.put("/Artist", temp);
        }

        temp = head.getAlbum();
        if (temp != null) {
            mml.put("/Album", temp);
        }

        head = null; //go get 'em GC...
    }
}