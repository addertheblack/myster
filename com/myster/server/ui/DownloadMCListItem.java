
/* 
	Main.java

	Title:			Server Stats Window Test App
	Author:			Andrew Trumper
	Description:	An app to test the server stats window
*/

package com.myster.server.ui;

import com.general.mclist.*;
import com.myster.server.event.*;
import com.myster.server.DownloadInfo;
import com.general.util.Util;


public class DownloadMCListItem extends MCListItemInterface {
	ServerDownloadDispatcher dispatcher;
	DownloadInfo info;
	String user="??";
	int status=LIMBO;
	int queuePosition=0;
	boolean endFlag;
	
	public final static int LIMBO=0;
	public final static int QUEUED=1;
	public final static int TRANSFERING=3;
	public final static int OVER=4;
	
	SortableString doneUser, doneFileName;
	SortableByte doneSize, doneProgress;
	SortableRate doneRate;

	public DownloadMCListItem(ServerDownloadDispatcher d) {
		dispatcher=d;
		d.addServerDownloadListener(new DownloadEventHandler());
	}
	
	public Object getObject() {
		return dispatcher;
	}
	
	public synchronized void disconnectClient() {
		endFlag = true;
		if (info == null) info.disconnectClient();
	}
	
	public synchronized  Sortable getValueOfColumn(int i) {
		if (status==OVER) {
			switch (i) {
				case 0:
					return doneUser;
				case 1:
					return doneFileName;
				case 2:
					return doneSize;
				case 3:
					return doneRate;
				case 4:
					return doneProgress;
				default :
					return new SortableString("Error");
			}
		} else if (info==null) {
			switch (i) {
				case 0:
					return new SortableString(user);
				case 1:
					return new SortableString("?");
				case 2:
					return new SortableByte(0);
				case 3:
					return new SortableRate(0);
				case 4:
					return new SortableByte(0);
				default :
					return new SortableString("Error");
			}		
		} else {
			switch (i) {
				case 0:
					return new SortableString(user);
				case 1:
					return new SortableString(info.getFileName());
				case 2:
					return new SortableByte(info.getFileSize());
				case 3:
					if (status==TRANSFERING) {
						return new SortableRate(((long)(info.getTransferRate())));
					} else if (status==QUEUED){
						return new SortableRate(-queuePosition);
					} else {
						return new SortableRate(SortableRate.UNKNOWN);
					}
				case 4:
					return new SortableByte(info.getAmountDownloaded());
				default :
					return new SortableString("Error");
			}
		}
	}
	
	public boolean equals(Object o) {
		try {
			DownloadMCListItem other=(DownloadMCListItem)o;
			if (this.getObject()==other.getObject()) return true;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return false;//buh
	}
	public void setUser(String s) {
		user=s;
	}
	
	public String getAddress() {
		return user;
	}
	
	
	private synchronized void done() { //needs to be synchronized with getValueColumn.
		if(status==OVER) return;
		setStatus(OVER);
		doneUser=new SortableString(user);
		doneFileName=new SortableString(info.getFileName());
		doneRate=new SortableRate(info.getFileSize()==info.getAmountDownloaded()?SortableRate.DONE:SortableRate.ABORTED);
		doneSize=new SortableByte(info.getFileSize());
		doneProgress=new SortableByte(info.getAmountDownloaded());
		info=null;//garbage collect all this older junk.
	}
	
	//is here for the check and the synchronization
	private synchronized void setStatus(int s) {
		if (s>=status) {
			status=s;
		}
	}
	
	public boolean isDone() {
		return (status==OVER);
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
				System.out.println("Here and "+info);
			}		
		}
		
		public void downloadStarted(ServerDownloadEvent e) {
			setStatus(TRANSFERING);
		}
		
		public void queued(ServerDownloadEvent e) {
			queuePosition=e.getQueuePosition();
			setStatus(QUEUED);
		}
	}
	
	public static class SortableRate extends SortableByte {
		public static final int DONE=-1000001;
		public static final int ABORTED=-1000002;
		public static final int UNKNOWN=-1000000;
		public static final int NOT_ENOUGH_DATA=-999999;
		public static final int WAITING=0;
		public SortableRate(long i) {
			super(i);
		}
	
		public String toString() {
			if (number==DONE) return "Done";
			else if (number==ABORTED) return "Aborted";
			else if (number==UNKNOWN) return "Negotiating";
			else if (number==NOT_ENOUGH_DATA) return "-";
			else if (number==0) return "Starting";
			else if (number<0) return (-number)+" in queue";
			else return ""+(Util.getStringFromBytes(number))+"/s";
			//return "impossible";
		}
	}
	

}