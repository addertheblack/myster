package com.myster.client.stream;





public interface SegmentDownloader {

	public void addListener(SegmentDownloaderListener listener);

	public void removeListener(SegmentDownloaderListener listener);

	public boolean isDead();

}