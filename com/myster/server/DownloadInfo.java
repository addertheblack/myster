/**
 moo I am a useless comment.
*/


package com.myster.server;

public interface DownloadInfo {
	public double getTransferRate();
	public long getStartTime();
	public long getAmountDownloaded();
	public String getFileName();
	public String getFileType();
	public long getFileSize();
	public long getInititalOffset();
	public void disconnectClient();
}