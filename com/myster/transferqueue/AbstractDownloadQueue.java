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

	private Vector downloads = new Vector();
	private LinkedList downloadQueue = new LinkedList();
	private int maxDownloads;
	private int maxDownloadQueueLength = UNLIMITED_QUEUE_LENGTH;
	
	protected AbstractDownloadQueue(int numberOfDownloadSpots) {
		maxDownloads = numberOfDownloadSpots;
	}
	
	public final void doDownload(Downloader download) throws IOException, MaxQueueLimitException {
		DownloadTicket ticket = registerDownload(download);
		
		try {
			waitForMyTurn(ticket);
			
			doDownload(ticket);
		} finally {
			unregisterDownload(ticket);
		}
	}
	
	public int getMaxTimeInterval() {
		return WAIT_TIME;
	}
	
	public final int getActiveDownloads() {
		return downloads.size();
	}
	
	public int getQueuedDownloads() {
		return downloadQueue.getSize();
	}
	
	public final int getDownloadSpots() {
		return maxDownloads;
	}
	
	/**
	*	Subsclasses wishing to save their values between program launches should over-ride 
	*	saveDownloadSpots(int newSpots)
	*/
	public final synchronized void setDownloadSpots(int newSpots) {
		maxDownloads = newSpots;
		
		saveDownloadSpotsInPrefs(newSpots);
		
		updateQueue();
	}
	
	public void saveDownloadSpotsInPrefs(int newSpots) {}

	public int getMaxQueueLength() {
		return maxDownloadQueueLength;
	}

	public void setMaxQueueLength(int numberOfAllowedSimutaneousDownloads) {
		maxDownloadQueueLength = numberOfAllowedSimutaneousDownloads;
	}
	
	private synchronized void waitForMyTurn(DownloadTicket ticket) throws IOException {
		while(true) {
			int lastQueuePosition;
			QueuedStats stats;
			synchronized (this) {
				if (ticket.isReadyToDownload()) return;
			
				stats = getQueuedStats(ticket);
			}
			
			
			lastQueuePosition = stats.getQueuePosition();
			ticket.getDownloader().queued(stats);
			
			
			
			synchronized (this) {
				if (ticket.isReadyToDownload()) return; //just before sleep double check
				
				if (lastQueuePosition != getQueuedStats(ticket).getQueuePosition()) continue;
				
				try {
					wait(WAIT_TIME);	//wait on Lock
				} catch(InterruptedException ex) {}
			}
		}

	}
	
	private QueuedStats getQueuedStats(DownloadTicket ticket) {
		return new QueuedStats(getQueuePosition(ticket));
	}
	
	private void doDownload(DownloadTicket ticket) throws IOException {
		ticket.getDownloader().download();
	}
	
	
	// moves tickets from queue to download slow and sets their "go" flag.
	// Also notifys the other thread in the queue if a change has occured..
	private synchronized void updateQueue() {
		while (isExistFreeSpot()) {
			DownloadTicket ticket = (DownloadTicket)downloadQueue.removeFromHead();
			
			if (ticket!=null) {
				downloads.addElement(ticket);
				ticket.setReadyToDownload();
				
				notifyAll();
			} else {
				break;
			}
		}
	}
	
	private synchronized boolean isExistFreeSpot() {
		return (maxDownloads == UNLIMITED_QUEUE_LENGTH ? true : (maxDownloads - downloads.size()) > 0);
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
		downloadQueue.remove(ticket); 		//if exists removes
		downloads.removeElement(ticket);	//if exists removes
		
		updateQueue();
	}
	
	private synchronized int getQueuePosition(DownloadTicket ticket) {
		return downloadQueue.getPositionOf(ticket) + 1;
	}
	
	private static class DownloadTicket {
		Downloader downloader;
		volatile boolean readyToDownload = false;
		
		public DownloadTicket(Downloader downloader) {
			this.downloader 	= downloader;
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