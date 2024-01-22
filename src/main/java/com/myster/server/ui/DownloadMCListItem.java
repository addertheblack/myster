/*
 * Main.java
 * 
 * Title: Server Stats Window Test App Author: Andrew Trumper Description: An app to test the server
 * stats window
 */

package com.myster.server.ui;

import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableByte;
import com.general.mclist.SortableString;
import com.general.util.Util;
import com.myster.client.stream.msdownload.MultiSourceUtilities;
import com.myster.server.DownloadInfo;
import com.myster.server.event.ServerDownloadDispatcher;
import com.myster.server.event.ServerDownloadEvent;
import com.myster.server.event.ServerDownloadListener;

public class DownloadMCListItem extends MCListItemInterface<ServerDownloadDispatcher> {
    private ServerDownloadDispatcher dispatcher;

    private DownloadInfo info;

    private String user = "??";

    private int status = LIMBO;

    private int queuePosition = 0;

    private boolean endFlag;

    private final static int LIMBO = 0;

    private final static int QUEUED = 1;

    private final static int TRANSFERING = 3;

    private final static int DONE_NO_ERROR = 4;

    private final static int ABORTED = 5;

    private SortableString doneUser, doneFileName;

    private SortableByte doneSize, doneProgress;

    private SortableRate doneRate;

    public DownloadMCListItem(ServerDownloadDispatcher d) {
        dispatcher = d;
        d.addServerDownloadListener(new DownloadEventHandler());
    }

    public ServerDownloadDispatcher getObject() {
        return dispatcher;
    }

    public synchronized void disconnectClient() {
        endFlag = true;
        if (info != null)
            info.disconnectClient();
    }

    public synchronized Sortable<?> getValueOfColumn(int i) {
        if (isDone()) {
            switch (i) {
            case 0:
                return doneUser;
            case 1:
                return doneFileName;
            case 2:
                return doneRate;
            case 3:
                return doneProgress;
            case 4:
                return doneSize;
            default:
                return new SortableString("Error");
            }
        } else if (info == null) {
            switch (i) {
            case 0:
                return new SortableString(user);
            case 1:
                return new SortableString("?");
            case 2:
                return new SortableRate(0);
            case 3:
                return new SortableByte(0);
            case 4:
                return new SortableByte(0);
            default:
                return new SortableString("Error");
            }
        } else {
            switch (i) {
            case 0:
                return new SortableString(user);
            case 1:
                return new SortableString(info.getFileName());
            case 2:
                if (status == TRANSFERING) {
                    return new SortableRate(((long) (info.getTransferRate())));
                } else if (status == QUEUED) {
                    return new SortableRate(-queuePosition);
                } else {
                    return new SortableRate(SortableRate.UNKNOWN);
                }
            case 3:
                return new SortableByte(info.getAmountDownloaded());
            case 4:
                return new SortableByte(info.getFileSize());
            default:
                return new SortableString("Error");
            }
        }
    }

    public boolean equals(Object o) {
        try {
            DownloadMCListItem other = (DownloadMCListItem) o;
            if (this.getObject() == other.getObject())
                return true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;//buh
    }

    public void setUser(String s) {
        user = s;
    }

    public String getAddress() {
        return user;
    }

    private synchronized void done() { //needs to be synchronized with
        // getValueColumn.
        if (isDone())
            return;
        if (info.getFileSize() == info.getAmountDownloaded()) {
            setStatus(DONE_NO_ERROR);
        } else {
            setStatus(ABORTED);
        }
        doneUser = new SortableString(user);
        doneFileName = new SortableString(info.getFileName());
        doneRate = new SortableRate(status == DONE_NO_ERROR ? SortableRate.DONE
                : SortableRate.ABORTED);
        doneSize = new SortableByte(info.getFileSize());
        doneProgress = new SortableByte(info.getAmountDownloaded());
        info = null;//garbage collect all this older junk.
    }

    //is here for the check and the synchronization
    private synchronized void setStatus(int s) {
        if (s >= status) {
            status = s;
        }
    }

    /**
     * Returns true if the download has finished.
     * 
     * @return true if the download has finished (ie: we won't send any more data). isDone() returns
     *         true even if the connection was aborted prematurely.
     */
    public boolean isDone() {
        return (status == DONE_NO_ERROR) || (status == ABORTED);
    }

    /**
     * Returns whether or not the user downloaded any data during this download. This is used to
     * indicate if the download was aborted before the setup was finished: usually because the user
     * was a leech.
     * 
     * @return true if the download represented by this object's has a status of OVER and is
     *         considered DONE and has not transfered any file data. Returns false otherwise.
     */
    public synchronized boolean isTrivialDownload() {
        if (status != DONE_NO_ERROR)
            return false;
        return ((Long) doneProgress.getValue()).longValue() == 0;
    }

    private class DownloadEventHandler extends ServerDownloadListener {
        public void downloadSectionFinished(ServerDownloadEvent e) {
            done();
        }

        public void downloadSectionStarted(ServerDownloadEvent e) {
            synchronized (DownloadMCListItem.this) {
                info = e.getDownloadInfo();
                if (endFlag) {
                    info.disconnectClient();
                }
                MultiSourceUtilities.debug("Here and " + info);
            }
        }

        public void downloadStarted(ServerDownloadEvent e) {
            setStatus(TRANSFERING);
        }

        public void queued(ServerDownloadEvent e) {
            queuePosition = e.getQueuePosition();
            setStatus(QUEUED);
        }
    }

    public static class SortableRate extends SortableByte {
        public static final int DONE = -1000001;

        public static final int ABORTED = -1000002;

        public static final int UNKNOWN = -1000000;

        public static final int NOT_ENOUGH_DATA = -999999;

        public static final int WAITING = 0;

        public SortableRate(long i) {
            super(i);
        }

        public String toString() {
            if (number == DONE)
                return "Done";
            else if (number == ABORTED)
                return "Aborted";
            else if (number == UNKNOWN)
                return "Negotiating";
            else if (number == NOT_ENOUGH_DATA)
                return "-";
            else if (number == 0)
                return "Starting";
            else if (number < 0)
                return (-number) + " in queue";
            else
                return "" + (Util.getStringFromBytes(number)) + "/s";
            //return "impossible";
        }
    }

}