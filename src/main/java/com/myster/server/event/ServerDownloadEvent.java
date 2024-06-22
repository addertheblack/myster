/**
 * ...
 */

package com.myster.server.event;

import com.myster.net.MysterAddress;
import com.myster.server.DownloadInfo;

public class ServerDownloadEvent extends ServerEvent {
    public static final int NO_QUEUE_POSTION = -1;
    public static final char NO_BLOCK_TYPE = '?';

    private final String type;
    private final String filename;
    private final char blockType;
    private final long fileSoFar;
    private final long filelength;
    private final DownloadInfo downloadInfo;
    private final int queuePosition;

    /**
     * 
     * @param id
     * @param addressOfRemote
     * @param section
     * @param filename
     * @param type
     * @param blockType or NO_DATA_OFFSET
     * @param filesofar
     * @param filelength
     * @param downloadInfo
     * @param queuePosition or NO_QUEUE_POSTION
     */
    public ServerDownloadEvent(MysterAddress addressOfRemote,
            int section, String filename, String type, char blockType, long filesofar,
            long filelength, DownloadInfo downloadInfo, int queuePosition) {
        super(addressOfRemote, section);
        this.type = type;
        this.filename = filename;
        this.blockType = blockType;
        this.fileSoFar = filesofar;
        this.filelength = filelength;
        this.downloadInfo = downloadInfo;
        this.queuePosition = queuePosition;
    }

    public char getBlockType() {
        return blockType;
    }

    public long dataSoFar() {
        return fileSoFar;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public String getFileName() {
        return filename;
    }

    public String getFileType() {
        return type;
    }

    public long getFileLength() {
        return filelength;
    }

    public DownloadInfo getDownloadInfo() {
        return downloadInfo;
    }
}