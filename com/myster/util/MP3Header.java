
package com.myster.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * This class is responsible for encapsulating all the crap needed for dealing
 * with MP3 header information.
 */

public class MP3Header {
    private static final int[][] bitratetable = {
    //  Ph 1L-I Ph1 L-II Ph1 L-III Ph2 L-I Ph2 L-II&III
            { 0, 0, 0, 0, 0 }, { 32000, 32000, 32000, 32000, 8000 },
            { 64000, 48000, 40000, 48000, 16000 },
            { 96000, 56000, 48000, 56000, 24000 },
            { 128000, 64000, 56000, 64000, 32000 },
            { 160000, 80000, 64000, 80000, 40000 },
            { 192000, 96000, 80000, 96000, 48000 },
            { 224000, 112000, 96000, 112000, 56000 },
            { 256000, 128000, 112000, 128000, 64000 },
            { 288000, 160000, 128000, 144000, 80000 },
            { 320000, 192000, 160000, 160000, 96000 },
            { 352000, 224000, 192000, 176000, 112000 },
            { 384000, 256000, 224000, 192000, 128000 },
            { 416000, 320000, 256000, 224000, 144000 },
            { 448000, 384000, 320000, 256000, 160000 }, { -1, -1, -1, -1, -1 }, };

    private final int[][] samplingarray = { { 44100, 48000, 32000, -1 },
            { 22050, 24000, 16000 } };

    private int bitrate;

    private boolean stereo;

    private int layer;

    private int samplingrate;

    private int MPEGversion;

    private boolean copyright;

    private String songName;

    private String artist;

    private String album;

    public static final int LAYERIII = 1;

    public static final int LAYERII = 2;

    public static final int LAYERI = 3;

    public static final int MPEG1 = 3;

    public static final int MPEG2 = 2;

    public static final int MPEG25 = 0;

    /**
     * constructor takes a MP3 file as an argument. If the file is not an MP3
     * file the routine will return an exception.
     */

    public MP3Header(File f) throws IOException {
        if (!f.exists())
            throw new IOException("File does no exist");

        RandomAccessFile in = new RandomAccessFile(f, "rw");

        try {

            int bytebuffer;
            byte workingbyte;
            int bitrateindex;
            int phase;

            //BYTE 1
            //byte 1 should be 1111-1111
            int counter = 0;
            do {
                bytebuffer = in.read();

                if (bytebuffer == -1)
                    error();

                workingbyte = (byte) bytebuffer;

                //XOR of 11111111 with 11111111 gives 00000000
                workingbyte = (byte) (workingbyte ^ 255);
                if (counter > 4096)
                    error();
                counter++;
            } while (workingbyte != 0); //for 1111-1111 is the start of the MP3

            //BYTE 2
            //111x-xxxx
            //7654 3210
            //bits 4 and 3 are the MPG version
            //bits 2 and 1 are the layer
            //bit 0 is an error detection code that can be ignored here.

            bytebuffer = in.read();

            if (bytebuffer == -1)
                error();

            //Quick Check:
            workingbyte = (byte) bytebuffer;

            workingbyte = (byte) (workingbyte >> 5);
            workingbyte = (byte) (workingbyte & 7);

            if (workingbyte != 7)
                error();

            //bits 4 and 3
            workingbyte = (byte) bytebuffer;

            workingbyte = (byte) (workingbyte >> 3);
            workingbyte = (byte) (workingbyte & 3);

            switch (workingbyte) {
            case 0:
                MPEGversion = MPEG25; //MPEG 2.5!
                break;
            case 1:
                MPEGversion = -1; //RESERVED
                break;
            case 2:
                MPEGversion = MPEG2;
                break;
            case 3:
                MPEGversion = MPEG1;
                break;
            default:
                error();
            }

            //bits 2 and 1
            workingbyte = (byte) bytebuffer;

            workingbyte = (byte) (workingbyte >> 1);
            workingbyte = (byte) (workingbyte & 3);

            switch (workingbyte) {
            case 0:
                layer = -1; //reserved
                break;
            case 1:
                layer = LAYERIII;
                break;
            case 2:
                layer = LAYERII;
                break;
            case 3:
                layer = LAYERI;
                break;
            default:
                error();
            }

            //BYTE 3
            //Byte 3 is
            //XXXX-XXXXX
            //7654 3210
            //7654 is bit rate index
            //bits 3 and 2 are sampling rate index
            //bit 1 is padding bit ignored here
            //bit 0 is private bit (does ???)

            //bits 7,6,5 and 4
            bytebuffer = in.read();

            if (bytebuffer == -1)
                error();

            workingbyte = (byte) bytebuffer;

            workingbyte = (byte) (workingbyte >> 4);
            workingbyte = (byte) (workingbyte & 15);

            bitrateindex = workingbyte;
            //phase==later;

            //bits 3 and 2:

            if (bytebuffer == -1)
                error();

            workingbyte = (byte) bytebuffer;

            workingbyte = (byte) (workingbyte >> 2);
            workingbyte = (byte) (workingbyte & 3);

            int samplingrateindex = -1;
            if (MPEGversion == MPEG1) {
                samplingrateindex = 0;
            } else {
                samplingrateindex = 1;
            }

            samplingrate = samplingarray[samplingrateindex][workingbyte];

            //BYTE 4
            //XXXX-XXXX
            //7654 3210
            //bits 7 and 6 are the channel mode
            //bits 5 and 4 are used for decoding the stereo (??)
            //bit 3 is a copyright flag
            //bit 2 is origninal material (ignored)
            //bits 1 and 0 are used for decoding somehow. (??)

            //bits 7 and 6
            bytebuffer = in.read();

            if (bytebuffer == -1)
                error();

            workingbyte = (byte) bytebuffer;

            workingbyte = (byte) (workingbyte >> 6); //bit shift right by 6
                                                     // bits
            workingbyte = (byte) (workingbyte & 3);

            if (workingbyte == 0 || workingbyte == 1 || workingbyte == 2)
                stereo = true;
            else
                stereo = false;

            //bit 3
            if (bytebuffer == -1)
                error();

            workingbyte = (byte) bytebuffer;

            workingbyte = (byte) (workingbyte >> 3); //bit shift right by 6
                                                     // bits
            workingbyte = (byte) (workingbyte & 1);

            copyright = ((workingbyte == 1) ? true : false);

            //bitrate CALCS!
            if (MPEGversion == MPEG1 && layer == LAYERI) {
                phase = 0;
            } else if (MPEGversion == MPEG1 && layer == LAYERII) {
                phase = 1;
            } else if (MPEGversion == MPEG1 && layer == LAYERIII) {
                phase = 2;
            } else if (MPEGversion == MPEG2 && layer == LAYERI) {
                phase = 3;
            } else if (MPEGversion == MPEG2 && layer == LAYERII) {
                phase = 4;
            } else {
                phase = 4;
            }

            bitrate = bitratetable[bitrateindex][phase];

            in.seek(f.length() - 128);
            byte[] threeBytes = new byte[3];
            in.readFully(threeBytes);
            String head = new String(threeBytes);
            if (head.equals("TAG")) {
                try {
                    //System.out.println("File has an ID3v1 header");

                    byte[] trackName = new byte[30];
                    in.seek(f.length() - 125);

                    in.read(trackName);
                    songName = trimMe(new String(trackName));

                    in.read(trackName);
                    artist = trimMe(new String(trackName));

                    in.read(trackName);
                    album = trimMe(new String(trackName));

                    //System.out.println("Song is "+songName+" by "+artist+"
                    // from the album "+album);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } finally {
            in.close();
        }
    }

    private static String trimMe(String p_toTrim) {
        String workingString = p_toTrim;
        int index = workingString.indexOf("\0");
        if (index == 0)
            return null;
        else if (index != -1)
            workingString = workingString.substring(0, index);

        for (int i = workingString.length(); i > 1; i--) {
            if (workingString.charAt(i - 1) != ' ') {
                workingString = workingString.substring(0, i);
                if (i == 1 && workingString.charAt(0) == ' ')
                    workingString = null;
                break;
            }
        }
        return workingString;
    }

    /**
     * returns true id MP3 is copyright and false otherwise.
     */

    public boolean isCopyright() {
        return copyright;
    }

    /** returns true if file is stereo and flase otherwise */
    public boolean isStereo() {
        return stereo;
    }

    /**
     * returns an int representing the Layer. Encoding is arbitrary so use
     * defined constants.
     */
    public int getLayer() {
        return layer;
    }

    /**
     * Returns a textual representation of the layer.
     */
    public String getLayerAsString() {
        switch (layer) {
        case LAYERI:
            return "layer I";
        case LAYERII:
            return "layer II";
        case LAYERIII:
            return "layer III";
        default:
            return "Unknown";
        }
    }

    /**
     * Returns the name of the MP3 file represented by this Object.
     */
    public String getMP3Name() {
        return songName;
    }

    public String getArtist() {
        return artist; //from ID3 tag
    }

    public String getAlbum() {
        return album;
    }

    /**
     * Returns the MPEG version. USe the constants defined in this object.
     */
    public int getMPEGVersion() {
        return MPEGversion;
    }

    /**
     * Returns a textual reprresentation of the MPEG version.
     */
    public String getMPEGVersionAsString() {
        switch (MPEGversion) {
        case MPEG25:
            return "MPEG 2.5";
        case MPEG2:
            return "MPEG 2";
        case MPEG1:
            return "MPEG 1";
        default:
            return "Unknown";
        }
    }

    /**
     * Returns the sampling rate in hz.
     */
    public int getSamplingRate() {
        return samplingrate;
    }

    /** returns the bit rate in bps */
    public int getBitRate() {
        return bitrate;
    }

    private void error() throws IOException {
        throw new IOException("Not an MP3 file");
    }

    public String toString() {
        return "Bitrate: " + bitrate + "bps | Sample Rate: " + samplingrate
                + "hz | Copyright: " + copyright + " | Stereo: " + stereo
                + " | " + getMPEGVersionAsString() + " | " + getLayerAsString();
    }

}