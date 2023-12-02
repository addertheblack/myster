package com.myster.filemanager;

import java.io.File;
import java.io.IOException;

import org.farng.mp3.MP3File;
import org.farng.mp3.id3.AbstractID3;

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
        MP3File mp3File = null;
        try {
            mp3File = new MP3File(file, false);
        } catch (Throwable ex) {
            System.err.println("Could not read ID3 tag info for: " + file);
            return;
        }

        
        
        if (file.getName().endsWith(".mp3")) {
            try {
                if (mp3File.seekMP3Frame()){
                    mml.put("/BitRate", "" + (mp3File.getBitRate() * 1000));
                    mml.put("/Hz", "" + mp3File.getFrequency());
                }
            } catch (IOException exception) {
                System.err.println("Problem seeking first MP3 music frame for: " + file);
            }
        }

        AbstractID3 id3Tag = mp3File.getID3v2Tag();
        if (id3Tag == null) {
            id3Tag = mp3File.getID3v1Tag();
            if (id3Tag == null) {
                return;
            }
        }
        
        String temp = id3Tag.getSongTitle();
        if (temp != null && !temp.equals("")) {
            mml.put("/ID3Name", temp);
        }

        String temp2 = id3Tag.getAuthorComposer();
        if (temp2 != null && !temp2.equals("")) {
            mml.put("/Artist", temp2);
        } else {
            temp2 = id3Tag.getLeadArtist();
            if (temp2 != null && !temp2.equals("")) {
                mml.put("/Artist", temp2);
            }
        }

        String temp1 = id3Tag.getAlbumTitle();
        if (temp1 != null && !temp1.equals("")) {
            mml.put("/Album", temp1);
        }
    }
}