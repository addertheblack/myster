/**
 * ...
 */

package com.myster.server.event;

import com.myster.net.MysterAddress;
import com.myster.server.DownloadInfo;

public class ServerDownloadEvent extends ServerEvent {
    public final static int SECTION_STARTED = -1;

    public final static int STARTED = 0;

    public final static int BLOCKSENT = 1;

    public final static int FINISHED = 2;

    public final static int QUEUED = 3;

    public final static int SECTION_FINISHED = 4;

    String type;

    String filename;

    int d; // cryptic one letter naming! Did I do this on purpose? Me: zuh.

    long filesofar;

    long filelength;

    DownloadInfo downloadInfo;

    //if id is 3 (QUEUED) the 'i' argument is queue position.
    public ServerDownloadEvent(int id, MysterAddress addressOfRemote,
            int section, String filename, String type, int i, long filesofar,
            long filelength, DownloadInfo downloadInfo) {
        super(id, addressOfRemote, section);
        this.type = type;
        this.filename = filename;
        this.d = i;
        this.filesofar = filesofar;
        this.filelength = filelength;
        this.downloadInfo = downloadInfo;
    }

    public int getBlockType() {
        return (getID() == QUEUED ? (int) 'q' : d);
    }

    public long dataSoFar() {
        return filesofar;
    }

    public int getQueuePosition() {
        return (getID() == QUEUED ? d : 0);
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