package com.myster.client.stream;


import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.File;
import java.util.Stack;
import java.util.Hashtable;
import java.util.Enumeration;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import com.general.events.SyncEventDispatcher;
import com.general.events.EventDispatcher;
import com.general.events.EventListener;
import com.general.events.GenericEvent;
import com.general.util.LinkedList;
import com.general.util.AnswerDialog;

import com.myster.search.MultiSourceHashSearch;
import com.myster.search.HashSearchListener;
import com.myster.search.HashSearchEvent;
import com.myster.search.MysterFileStub;
import com.myster.search.CrawlerThread;
import com.myster.search.IPQueue;
import com.myster.util.FileProgressWindow;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;
import com.myster.hash.FileHash;
import com.myster.type.MysterType;
import com.myster.net.MysterSocketFactory;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;


/*
	Things to do
	
	-make it so that 1 queued download is canceled if there is an active download
	
	-make it so that searches don't stop
	
	-change the color of the bars to green again
	
	hook up queued message to something.
	
	hook up resumable download for multi source
	
	try to make it so that PR banner work without an image / URL being put.
	
	try to make server side hash search work (UDP)
*/

public class MultiSourceDownload implements Runnable, Controller {
	final MysterFileStub stub;					//starter stub (contains first address and filename and type)
	final FileHash hash;								//should be final but can't be...
	final long fileLength; 							//should be final but can't be...
	
	final HashSearchListener listener;
	final Hashtable downloaders;
	
	//File theFile;								//file downloading to!
	RandomAccessFile randomAccessFile;			//file downloading to!

	long fileProgress= 0;
	long bytesWrittenOut = 0;
	Stack unfinishedSegments = new Stack(); 	//it's a stack 'cause it doens't
												//matter what data structure so long
												//as add and remove are O(C).

	EventDispatcher dispatcher = new SyncEventDispatcher();
	
	boolean endFlag 	= false;	//this is set to true to tell MSSource to stop.
	boolean isDead 		= false;	//is set to true if cleanUp() has been called
	
	public static final int MULTI_SOURCE_BLOCK_SIZE = 512 * 1024;

	public MultiSourceDownload(MysterFileStub stub, FileHash hash, long fileLength, MSDownloadListener listener, RandomAccessFile randomAccessFile) {
		this.randomAccessFile	= randomAccessFile;
		this.stub 				= stub;
		this.hash				= hash;
		this.fileLength			= fileLength;
		
		this.downloaders			= new Hashtable();
		this.listener				= new MSHashSearchListener();
		
		addListener(listener);
	}
	
	//Can be done as a thread or not.
	public void run() {
		dispatcher.fireEvent(new MultiSourceEvent(MultiSourceEvent.START_DOWNLOAD, this));
	
		newDownload(stub);
		
		MultiSourceHashSearch.addHash(stub.getType(), hash, listener);
	}
	
	private synchronized void newDownload(MysterFileStub stub) {
		if (endFlag) return;
		
		InternalSegmentDownloader downloader = new InternalSegmentDownloader(this, stub);
	
		if (downloaders.get(downloader)!=null) return; //already have a downloader doing this file.
	
		downloaders.put(downloader, downloader);
		
		dispatcher.fireEvent(new MSSegmentEvent(MSSegmentEvent.START_SEGMENT, downloader));
			
		downloader.start();
	}
	
	public synchronized boolean isOkToQueue() {
		Enumeration enum = downloaders.elements();
		
		while (enum.hasMoreElements()) {
			InternalSegmentDownloader internalSegmentDownloader = (InternalSegmentDownloader)(enum.nextElement());
			
			if (internalSegmentDownloader.isActive()) return false;
		}
		
		return true;
	}
	
	//removes a download but doesn't stop a download. (so this should be called by downloads that have ended completely.
	public synchronized boolean removeDownload(SegmentDownloader downloader) {
		boolean result = (downloaders.remove(downloader)!=null);
		
		dispatcher.fireEvent(new MSSegmentEvent(MSSegmentEvent.END_SEGMENT, downloader));
		
		endCheckAndCleanup(); //check to see if cleanupNeeds to be called.
		
		return result;
	}
	
	/**
	*	call this everytime you think you might have done something that has stopped the download (ie: everytime a download has been removed
	*	or the download has been canceled.) The routines checks to see if the download and all helper thread have stopped and if it has it
	*	calls the final cleanup routine
	*/
	private synchronized boolean endCheckAndCleanup() {
		if ((endFlag) & (downloaders.size()==0)) {
			cleanUp();
			
			return true;
		}
		
		return false;
	}
	
	public synchronized  WorkSegment getNextWorkSegment() {
		if (unfinishedSegments.size() > 0) return (WorkSegment)(unfinishedSegments.pop());
		
		long readLength = (fileLength - fileProgress > MULTI_SOURCE_BLOCK_SIZE ? MULTI_SOURCE_BLOCK_SIZE : fileLength - fileProgress);

		System.out.println("Main Thread -> Adding Work Segment "  +fileProgress + " " + readLength);
		WorkSegment workSegment = new WorkSegment((readLength==0?0:fileProgress), readLength);
		
		fileProgress+=readLength;
		
		return workSegment;
	}
	
	public synchronized void receiveExtraSegments(WorkSegment[] workSegments) {
		for (int i=0; i < workSegments.length; i++) {
			unfinishedSegments.push(workSegments[i]);
		}
	}
	
	public synchronized void receiveDataBlock(DataBlock dataBlock) {
		try {
			randomAccessFile.seek(dataBlock.offset);
		
			randomAccessFile.write(dataBlock.bytes);
			
			bytesWrittenOut+=dataBlock.bytes.length;
			
			dispatcher.fireEvent(new MultiSourceEvent(MultiSourceEvent.PROGRESS, this));
			
			if (isDone()) {
				flagToEnd(); //we're done.
			}
		} catch (IOException ex) {
			ex.printStackTrace(); //fix
		}
	}
	
	public synchronized long getProgress() {
		return bytesWrittenOut;
	}
	
	public synchronized long getLength() {
		return fileLength;
	}
	
	public MysterFileStub getStub() {
		return stub;
	}
	
	public synchronized boolean isDead() {
		return isDead;
	}
	
	/**
	*	returns true if all of the file has been downloaded
	*/
	public synchronized boolean isDone() {
		return (bytesWrittenOut == fileLength);
	}
	
	//call when downlaod has completed sucessfully.
	private synchronized void done() {
		dispatcher.fireEvent(new MultiSourceEvent(MultiSourceEvent.DONE_DOWNLOAD, this));
	}
	
	//if is synchronized will cause deadlocks
	public void addListener(MSDownloadListener listener) {
		dispatcher.addListener(listener);
	}
	
	//if is synchronized will cause deadlocks
	public void removeListener(MSDownloadListener listener) {
		dispatcher.removeListener(listener);
	}
	
	public synchronized void flagToEnd() {
		if (endFlag) return; //shouldn't be called twice..
	
		endFlag = true;
	
		Enumeration enumeration = downloaders.elements();
		
		while (enumeration.hasMoreElements()) {
			InternalSegmentDownloader downloader = (InternalSegmentDownloader)enumeration.nextElement();
			
			downloader.flagToEnd();
		}
		
		endCheckAndCleanup(); //just in case there was no downloader threads.
	}
	
	public void end() {
		flagToEnd();
		
		while(! isDead) { //wow, is crap
			try {
				Thread.sleep(100);
			} catch (InterruptedException ex) {
				return;
			}
		}
	}
	
	// Instead of putting a finally in the run() method, wen can put stuff in here.
	// This method will only be called once right at the end of the download
	private synchronized void cleanUp() {
		MultiSourceHashSearch.removeHash(stub.getType(), hash, listener);
		
		try {randomAccessFile.close();} catch (Exception ex) {} // assert file is closed
		
		isDead = true;
		
		dispatcher.fireEvent(new MultiSourceEvent(MultiSourceEvent.END_DOWNLOAD, this));
		
		if (isDone()) {
			done();
		}
	}
	
	private class MSHashSearchListener extends HashSearchListener {
		public void startSearch(HashSearchEvent event) {}
		
		public void searchResult(HashSearchEvent event) {
			MysterFileStub stub = event.getFileStub();
			
			MultiSourceUtilities.debug(stub==null?"Search Lstnr-> No file with that hash here.":"Search Lstnr-> Got result "+stub);
		
			if (stub!=null) {
				newDownload(stub);
				MultiSourceUtilities.debug("Search Lstnr-> Starting another segment downloader (another source)");
			}
		}
		
		public void endSearch(HashSearchEvent event) {}
	}

}





class InternalSegmentDownloader extends MysterThread implements SegmentDownloader{
	// Properties
	MysterFileStub 	stub;
	MysterSocket socket;
	Controller controller;
	
	//working variables
	WorkingSegment workingSegment;

	//Utility variables
	EventDispatcher dispatcher = new SyncEventDispatcher();
	
	//Utility working variables
	boolean endFlag 	= false;
	boolean deadFlag 	= false;
	boolean isActive	= false;	//is active says if the SegmentDownloader is actively downloading a file or if the download is queued
	
	//Static variables
	private static int instanceCounter = 0;
	
	//Constants
	private static final int CHUNK_SIZE = 2*1024;

	public InternalSegmentDownloader(Controller controller, MysterFileStub stub) {
		super("SegmentDownloader "+(instanceCounter++)+" for "+stub.getName());
	
		this.stub 			= stub;
		this.controller		= controller;
	}
	
	public void addListener(SegmentDownloaderListener listener) {
		dispatcher.addListener(listener);
	}
	
	public void removeListener(SegmentDownloaderListener listener) {
		dispatcher.removeListener(listener);
	}

	public void run() {
		try {
			socket = MysterSocketFactory.makeStreamConnection(stub.getMysterAddress());
			//else socket = this.socket; //yuck.
			
			fireEvent(SegmentDownloaderEvent.CONNECTED, 0, 0, 0, 0);
			
			debug("Work Thread "+getName()+" -> Sending Section Type");
			socket.out.writeInt(com.myster.server.stream.MultiSourceSender.SECTION_NUMBER);
			
			debug("Work Thread "+getName()+" -> Checking Protocol");
			com.myster.client.stream.StandardSuite.checkProtocol(socket.in); //throws Exception if bad
			
			debug("Work Thread "+getName()+" -> Doing Header");
			doHeader(socket);
			
			for (;;) {
				if (endFlag) return;
			
				WorkSegment workSegment = controller.getNextWorkSegment(); //(WorkSegment)workQueue.removeFromHead();

				if (workSegment == null) return;
				
				workingSegment = new WorkingSegment(workSegment);

				if (doWorkBlock(socket, workingSegment)==false) return; //this is for kill signals.. there are also exceptions
				
				workingSegment = null;
			}
		} catch (UnknownProtocolException ex) {
			com.myster.client.stream.StandardSuite.disconnectWithoutException(socket);
			
			debug("Server doesn't understand multi-source download."); //some sort of exception should be thrown here so that callers can catch this.
		} catch (IOException ex) {
			ex.printStackTrace(); //this code can handle exceptions so this is really here to see if anything unexpected has occured
		} finally {
			try {
				socket.close();
			} catch (Exception ex) {}
			
			finishUp();
		}
	}
	
	//Offset is offset within the file
	//lenght is the length of the current segment
	//progress is the progress through the segment (exclusing offset)
	private void fireEvent(int id, long offset, long progress, int queuePosition, long length) {
		dispatcher.fireEvent(new SegmentDownloaderEvent(id, this, offset, progress, queuePosition, length, stub));
	}
	
	private void fireEvent(byte type, byte[] data) {
		dispatcher.fireEvent(new SegmentMetaDataEvent(type, data));
	}
	
	private synchronized void finishUp() {
		deadFlag = true;
		
		if ((workingSegment == null) || (workingSegment.isDone())) {
			controller.receiveExtraSegments(new WorkSegment[]{});
		} else {
			controller.receiveExtraSegments(new WorkSegment[]{workingSegment.getRemainingWorkSegment()});
		}
		
		fireEvent(SegmentDownloaderEvent.END_CONNECTION, 0,0,0,0);
		
		debug("Thread "+getName()+" -> Finished.");
		
		controller.removeDownload(this);
	}
	
	private void doHeader(MysterSocket socket) throws IOException {
		socket.out.writeInt(stub.getType().getAsInt());
		socket.out.writeUTF(stub.getName());
		
		if (socket.in.read()!=1) {
			com.myster.client.stream.StandardSuite.disconnect(socket);
			
			throw new IOException("Could not find file");
		}
	}
	
	public boolean isDead() {
		return deadFlag;
	}
	
	
	private boolean doWorkBlock(MysterSocket socket, WorkingSegment workingSegment) throws IOException {
		debug("Work Thread "+getName()+" -> Reading data "+workingSegment.workSegment.startOffset+" "+workingSegment.workSegment.length);
		
		socket.out.writeLong(workingSegment.workSegment.startOffset);
		socket.out.writeLong(workingSegment.workSegment.length);
		
		if (workingSegment.workSegment.isEndSignal()) {
			com.myster.client.stream.StandardSuite.disconnect(socket);
			return false;
		}
		
		debug("Work Thread "+getName()+" -> Reading in QueuePostion");
		for (;;) {
			RobustMML mml = null;
			
			try {
				mml = new RobustMML(socket.in.readUTF());
			} catch (MMLException ex) {
				throw new IOException("MML String was corrupt.");
			}
			
			try {
				int queuePosition = Integer.parseInt(mml.get(com.myster.server.stream.MultiSourceSender.QUEUED_PATH));
				
				String message = mml.get(com.myster.server.stream.MultiSourceSender.MESSAGE_PATH);
				
				//if (message!=null) progress.setText(message); ///! Stuff for event here
				
				debug("Queued pos ----> "+queuePosition+" "+ message);
				
				if (queuePosition == 0) {
					isActive = true; // now we're downloading
					break; //yippy! on to download!
				}
				
				isActive = false; // blagh! Looks like we're queued!
				
				if (! controller.isOkToQueue()) throw new IOException("Should not be queued!");
				
				fireEvent(SegmentDownloaderEvent.QUEUED, 0, 0, queuePosition, 0);
			} catch (NumberFormatException ex) {
				throw new IOException("Server sent garble as queue position -> "+mml);
			}

			if (endFlag) throw new IOException("Was told to end.");
		}
		
		fireEvent(SegmentDownloaderEvent.START_SEGMENT, workingSegment.workSegment.startOffset, 0, 0, workingSegment.workSegment.length);//this isn't in the right place

		while (workingSegment.progress < workingSegment.workSegment.length) {
			debug("Work Thread "+getName()+" -> Reading in Type");
			
			if (socket.in.readInt()!=6669) throw new IOException("Client/Server lost sync");
			
			byte type = (byte)socket.in.read();
			
			
			
			switch (type) {
				case 'd':
					//progress.setText("Starting transfer...");
					long blockLength = socket.in.readLong();
					
					debug("Work Thread "+getName()+" -> Downloading start");
					downloadDataBlock(socket, workingSegment, blockLength);
					debug("Work Thread "+getName()+" -> Downloading finished");

					break;
				default:
					fireEvent(type, getDataBlock(socket));
					break;
			}
		}
		
		fireEvent(SegmentDownloaderEvent.END_SEGMENT, workingSegment.workSegment.startOffset,workingSegment.workSegment.startOffset+workingSegment.workSegment.length, 0, workingSegment.workSegment.length);
		
		return true;
	}
	
	public boolean isActive() {
		return isActive;
	}
	
	private byte[] getDataBlock(MysterSocket socket) throws IOException {
		byte[] buffer = new byte[(int)socket.in.readLong()];
		
		socket.in.readFully(buffer);
		
		return buffer;
	}
	
	private void downloadDataBlock(MysterSocket socket, WorkingSegment workingSegment, long length) throws IOException {
		long bytesDownloaded = 0;

		for (bytesDownloaded = 0 ; bytesDownloaded < length; ) {
			long calcBlockSize = (length - bytesDownloaded < CHUNK_SIZE?length-bytesDownloaded:CHUNK_SIZE);
			
			byte[] buffer = new byte[(int)calcBlockSize]; //could be made more efficient by using a pool.
			
			if (endFlag) throw new IOException("was asked to end");
			
			socket.in.readFully(buffer);
			
			controller.receiveDataBlock(new DataBlock(workingSegment.getCurrentOffset(), buffer));
			
			bytesDownloaded += calcBlockSize; //this line doesn't throw an IO exception
			workingSegment.progress += calcBlockSize;
			
			fireEvent(SegmentDownloaderEvent.DOWNLOADED_BLOCK, workingSegment.workSegment.startOffset, workingSegment.getProgress(), 0, workingSegment.workSegment.length); //nor this.
		}
	}
	
	public void flagToEnd() {
		endFlag = true;
		
		try { socket.close(); } catch (Exception ex) {}
	}
	
	public void end()  {
		flagToEnd();
		
		try {join();} catch(InterruptedException ex) {}
	}
	
	public int hashCode() {
		return stub.getMysterAddress().hashCode();
	}
	
	public boolean equals(Object o) {
		InternalSegmentDownloader other = null;
		try {
			other = (InternalSegmentDownloader)o;
		} catch (ClassCastException ex) {
			return false;
		}
		
		return (stub.getMysterAddress().equals(other.stub.getMysterAddress()));
	}
	
	private static void debug(String string) {
		//System.out.println(string);
	}
	
	
	/**
	*	A workign segment is a work segment with the amount progress throught it.
	*/
	private static class WorkingSegment {
		public final WorkSegment workSegment;
		private long progress = 0;
		
		public WorkingSegment(WorkSegment workSegment) {
			this.workSegment = workSegment;
		}
		
		public long getProgress() { return progress; }
		public long setProgress(long progress) { return (this.progress = progress); } //using chaining to make chaining easy
		
		/**
		*	Returns the remaining part of this work segment as a WorkSegment;
		*
		*	This call returns a stupid no-op work segment if it's done. Call "isDone" to make sure the segment hasen't been finished 
		*	before using this method.
		*/
		public WorkSegment getRemainingWorkSegment() { return new WorkSegment(workSegment.startOffset+progress, workSegment.length-progress); }
		
		public boolean isDone() { return (progress == workSegment.length) ; }
		
		public long getCurrentOffset() { return (workSegment.startOffset + progress); }
	}
}

//Unsafe object due to lack of immutable arrays.
class DataBlock {
	public final byte[] bytes;
	public final long offset;
	
	public DataBlock(long offset, byte[] bytes) {
		this.offset	= offset;
		this.bytes	= bytes ;
	}
}

class WorkSegment {
	public final long startOffset, length;
	
	public WorkSegment(long startOffset, long length) {
		this.startOffset=startOffset;
		this.length=length;
	}
	
	public boolean isEndSignal() {
		return (startOffset==0) && (length==0);
	}
}


interface Controller {
	public WorkSegment getNextWorkSegment();
	public void receiveExtraSegments(WorkSegment[] workSegments);
	public void receiveDataBlock(DataBlock dataBlock);
	public boolean removeDownload(SegmentDownloader downloader);
	public boolean isOkToQueue(); //returns false if it's not ok to queue.
}
