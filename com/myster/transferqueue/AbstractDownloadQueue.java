package com.myster.transferqueue;

import java.io.IOException;
import java.util.Vector;

import com.general.util.LinkedList;

/**
*	Provides a generic queue implementation. Most TransferQueue
*	implementator will want to subclass this.
*/

public abstract class AbstractDownloadQueue extends TransferQueue {
	public static final int  UNLIMITED_QUEUE_LENGTH = -1;

	private static final int WAIT_TIME = 30*1000; //30 secs


	private Vector downloadSpots = new Vector();
	private LinkedList downloadQueue = new LinkedList();
	private int activeDownloadCount = 0;
	private int maxDownloadQueueLength = UNLIMITED_QUEUE_LENGTH;
	
	protected AbstractDownloadQueue(int numberOfDownloadSpots) {
		downloadSpots.setSize(numberOfDownloadSpots);
	}
	
	public final void doDownload(Downloader download) throws IOException, MaxQueueLimitException {
		DownloadTicket ticket = registerDownload(download);
		try {
			waitForMyTurn(ticket);
			
			synchronized (this) {activeDownloadCount++;} //slightly overkill but what the hell..
			doDownload(ticket);
			synchronized (this) {activeDownloadCount--;}
		} finally {
			unregisterDownload(ticket);
		}
	}
	
	public int getMaxTimeInterval() {
		return WAIT_TIME;
	}
	
	public final int getActiveDownloads() {
		return activeDownloadCount;
	}
	
	public int getQueuedDownloads() {
		return downloadQueue.getSize();
	}
	
	public final int getDownloadSpots() {
		return downloadSpots.size();
	}
	
	/**
	*	Subsclasses wishing to save their values between program launches should over-ride 
	*	saveDownloadSpots(int newSpots)
	*/
	public final void setDownloadSpots(int newSpots) {
		downloadSpots.setSize(newSpots);
	}
	
	public void saveDownloadSpotsInPrefs(int newSpots) {}

	public int getMaxQueueLength() {
		return maxDownloadQueueLength;
	}

	public void setMaxQueueLength(int numberOfAllowedSimutaneousDownloads) {
		maxDownloadQueueLength = numberOfAllowedSimutaneousDownloads;
	}
	
	private synchronized void waitForMyTurn(DownloadTicket ticket) throws IOException {
		while(! ticket.isReadyToDownload()) {
			ticket.getDownloader().queued(getQueuedStats(ticket));
			try {
				wait(WAIT_TIME);	//wait on Lock
			} catch(InterruptedException ex) {}
		}

	}
	
	private QueuedStats getQueuedStats(DownloadTicket ticket) {
		return new QueuedStats(getQueuePosition(ticket));
	}
	
	private void doDownload(DownloadTicket ticket) throws IOException {
		ticket.getDownloader().download();
	}
	
	private synchronized void updateQueue() {
		int freeSpot = getEmptyDownloadSpots();
		
		if (freeSpot != -1) { //but he murdered 23 babies! Time for a cool island song..
			DownloadTicket ticket = (DownloadTicket)downloadQueue.removeFromHead();
			downloadSpots.setElementAt(ticket, freeSpot);
			ticket.setReadyToDownload();
			notifyAll();
		}
	}
	
	private synchronized int getEmptyDownloadSpots() {
		if (getActiveDownloads() > downloadSpots.size()) return -1; //woops to many downloads already.
	
		for (int i = 0; i < downloadSpots.size() ; i++) {
			if (downloadSpots.elementAt(i) == null) return i;
		}
		
		return -1;
	}
	
	
	private synchronized DownloadTicket registerDownload(Downloader downloader) throws MaxQueueLimitException {
		if ((downloadQueue.getSize() >= maxDownloadQueueLength)
				&& (maxDownloadQueueLength != UNLIMITED_QUEUE_LENGTH)) throw new MaxQueueLimitException("Transfer Queue is already at max length");
		
		DownloadTicket ticket = new DownloadTicket(downloader);
	
		downloadQueue.addToTail(ticket);
		
		updateQueue();
		
		return ticket;
	}
	
	private synchronized void unregisterDownload(DownloadTicket ticket) {
		downloadQueue.remove(ticket);
		downloadSpots.removeElement(ticket);
		
		updateQueue();
	}
	
	private synchronized int getQueuePosition(DownloadTicket ticket) {
		return downloadQueue.getPositionOf(ticket) + 1;
	}
	
	private static class DownloadTicket {
		Downloader downloader;
		volatile boolean readyToDownload = false;
		
		public DownloadTicket(Downloader downloader) {
			this.readyToDownload 	= readyToDownload;
		}
		
		public Downloader getDownloader() {
			return downloader;
		}
		
		public boolean isReadyToDownload() {
			return readyToDownload;
		}
		
		public void setReadyToDownload() {
			readyToDownload = true; //can't become un-ready to download
		}
	}
}