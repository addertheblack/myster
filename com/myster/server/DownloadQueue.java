package com.myster.server;

import com.myster.server.QueuedTransfer;
import com.general.util.LinkedList;
import com.general.util.BlockingQueue;
import com.myster.util.MysterThread;
import com.myster.net.MysterAddress;
import com.myster.pref.Preferences;
import java.awt.Panel;

public class DownloadQueue {
	private DownloadBlockingQueue downloadQueue;
	private DownloadSpot[] activeDownload;
	private KeepAlive keepAlive;	//keeps the people in the queue alive!
	private PokerThread poker;
	private final int i_spots;	//represents the total number of download spots.
	
	public final static int MAX_SPOTS=10;
	public final static int MIN_SPOTS=2;
	
	private final static int DEFAULT_DOWNLOAD_SPOTS=2;
	private final static String PREF_PATH="downloadSpots";

	public DownloadQueue() {
		
		i_spots=getQueueLength();//Gets the queue length from prefs and sets its queue legnth to that.
		
		activeDownload=new DownloadSpot[i_spots];
		
		poker=new PokerThread();
		downloadQueue=new DownloadBlockingQueue(poker);
		keepAlive=new KeepAlive(downloadQueue);
		
		
		for (int i=0; i<activeDownload.length; i++) {
			activeDownload[i]=new DownloadSpot(downloadQueue,keepAlive);
			activeDownload[i].start();
		}
		
		poker.start();
		keepAlive.start();
	}

	/**
		Adds a download to the downlad queue. The dowload will sit in the queue until it is ready
		or until it is canceled.
	*/
	public synchronized boolean addDownloadToQueue(QueuedTransfer download) {
		if (downloadQueue.length()>MAX_SPOTS) {
			download.disconnect();
			return false;
		}
		downloadQueue.add(download);
		keepAlive.interrupt();
		return true;
	}
	
	protected static boolean setQueueLength(int i) { //sets the queue length in the !prefs!.
		if (i<=MAX_SPOTS&&i>=MIN_SPOTS) {
			Preferences.getInstance().put(PREF_PATH, ""+i);
			
			return true;
		}
		return false;
	}
	
	protected static int getQueueLength() {	//gets the queue length from the !prefs!.
		String s_spots=Preferences.getInstance().get(PREF_PATH);
		
		if (s_spots!=null) {
			try {
				return Integer.parseInt(s_spots);
			} catch (NumberFormatException ex) {}
		}
		return DEFAULT_DOWNLOAD_SPOTS;
	}
	
	private static class KeepAlive extends MysterThread {
		DownloadBlockingQueue downloadQueue;
		boolean endflag=false;
		
		public static final long WAIT_TIME=45; //in secs.
		
		public KeepAlive(DownloadBlockingQueue downloadQueue) {
			this.downloadQueue=downloadQueue;
		}
		
		public void run() {
			long waitTime=1;
			for (;;) {
				
				try {
					sleep(waitTime); //1 min
				} catch (InterruptedException ex) {
					if (endflag) return;
				}
				long lastRefresh=System.currentTimeMillis();
				try {
					if (downloadQueue.length()!=0) {
						System.out.println("Refreshed the list..");
						downloadQueue.refresh();
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					System.out.println("Something tried to kill the refresher..!");
				}
				
				long l_temp=WAIT_TIME*1000-(System.currentTimeMillis()-lastRefresh);
				waitTime=(l_temp>0?l_temp:1);
			}
		}
	}

	private static class DownloadBlockingQueue extends BlockingQueue {
		PokerThread poker;
		
		public DownloadBlockingQueue(PokerThread t) {
			poker=t;
		}
		
		public void refresh() {
			synchronized (list) {
				for (int i=0; i<list.getSize(); i++) {
					boolean err=false;
					try {
						if (!((QueuedTransfer)(list.getElementAt(i))).isDone()) { //if thread is down move onto the next one. (usefull)
							poker.plugClient((QueuedTransfer)(list.getElementAt(i)));
							((QueuedTransfer)(list.getElementAt(i))).refresh(i+1);
						} else {
							System.out.println("Dead transfer found.");
							err=true;
						}
					} catch (Exception ex) {
						err=true;
					}
					
					if (err) {
						((QueuedTransfer)(list.getElementAt(i))).disconnect();
						list.remove(((QueuedTransfer)(list.getElementAt(i))));
						i--; //One gone.
					}
					//try {Thread.currentThread().sleep(1000);} catch (InterruptedException ex) {}
				}
				poker.plugClient((QueuedTransfer)null);
			}
		}
	}
	
	private static class PokerThread extends MysterThread {
		QueuedTransfer client;
		
		public synchronized void run() {
			for(;;) {
				try {
					wait(10*1000);
				} catch (InterruptedException ex) {continue;}
				
				if (client!=null) {
					System.out.println("POKER: Waking up the refreshed thread.");
					client.disconnect();
				}
			}
		}
		
		public synchronized void plugClient(QueuedTransfer t) {
			interrupt();
			client=t;
		}	
	}
	
	private static class DownloadSpot extends MysterThread {
		BlockingQueue queue;
		int state=0;

		KeepAlive keepAlive;
		
		public static final int WAITING=0;
		public static final int WORKING=1;
		public static final int DYING=2;
		public static final int DEAD=3;
		
		DownloadSpot(BlockingQueue queue, KeepAlive a) {
			this.queue=queue;
			keepAlive=a;
		}
		
		public void run() {
			QueuedTransfer download=null;;
			for(;;) {
				try { 
					download=(QueuedTransfer)(queue.get());
				} catch (InterruptedException ex) {
					return; //exit, you are done.
				}
				if (download==null) continue; //bubble. These happen if an item is deleted from the middle of the download queue.
				download.startDownload(); //NOT start().
				keepAlive.interrupt();
			}
		}
	}

}