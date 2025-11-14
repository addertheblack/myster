package com.myster.filemanager;

import java.io.File;
import java.nio.file.Path;

import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;

import com.myster.mml.MessagePack;

/**
 * This class implements the different data needed by the MPG3 files.
 */
public class MPG3FileItem extends FileItem {
    private MessagePack messagePackRepresentation;

    public MPG3FileItem(Path root, Path path) {
        super(root, path);
    }

    public synchronized MessagePack getMessagePackRepresentation() {
        if (messagePackRepresentation != null)
            return messagePackRepresentation;

        messagePackRepresentation = super.getMessagePackRepresentation();

        patchFunction2(messagePackRepresentation, getPath());
        return messagePackRepresentation;
    }

    public static void patchFunction2(MessagePack messagePack, Path path) {
        Mp3File mp3File = null;
        try {
            mp3File = new Mp3File(path.toFile(), 4096, false);
        } catch (Throwable ex) {
            System.err.println("Could not read ID3 tag info for: " + path);
            return;
        }

        if (path.toFile().getName().endsWith(".mp3")) {
            // Use appropriate numeric types instead of strings for better efficiency
            messagePack.putLong("/BitRate", mp3File.getBitrate() * 1000L);
            messagePack.putLong("/Hz", mp3File.getSampleRate());
            messagePack.putBoolean("/Vbr", mp3File.isVbr());
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
            messagePack.putString("/ID3Name", temp);
        }

        String temp2 = id3Tag.getArtist();
        if (temp2 != null && !temp2.equals("")) {
            messagePack.putString("/Artist", temp2);
        }

        String temp1 = id3Tag.getAlbum();
        if (temp1 != null && !temp1.equals("")) {
            messagePack.putString("/Album", temp1);
        }
        
        if (id3Tag instanceof ID3v2 id3v2Tag) {
            temp2 = id3v2Tag.getOriginalArtist();
            if (temp2 != null && !temp2.equals("")) {
                messagePack.putString("/OriginalArtist", temp2);
            }
        }
    }
}