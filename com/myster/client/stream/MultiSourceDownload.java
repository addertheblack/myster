package com.myster.client.stream;


import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.File;
import java.util.Stack;
import java.awt.Color;

import com.general.events.SyncEventDispatcher;
import com.general.events.EventDispatcher;
import com.general.events.EventListener;
import com.general.events.GenericEvent;
import com.general.util.LinkedList;
import com.general.util.Timer;

import com.myster.search.MultiSourceHashSearch;
import com.myster.search.HashSearchListener;
import com.myster.search.HashSearchEvent;
import com.myster.search.MysterFileStub;
import com.myster.search.CrawlerThread;
import com.myster.search.IPQueue;
import com.myster.util.ProgressWindow;
import com.myster.util.Sayable;
import com.myster.hash.FileHash;
import com.myster.type.MysterType;
import com.myster.net.MysterSocketFactory;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;

public class MultiSourceDownload {
	private static volatile int counter = 0; 
	
	final MysterFileStub stub;
	final FileHash hash;
	RandomAccessFile file;
	LinkedList  dataQueue = new LinkedList();

	
	InternalSegmentDownloader[] downloaders;
	
	Timer timer;
	
	long fileProgress= 0;
	long bytesWrittenOut = 0;
	long fileLength = 81415201; //for testing.
	ProgressWindow progress;
	//SimpleHash
	
	CrawlerThread crawler;
	EventDispatcher dispatcher = new SyncEventDispatcher();
	
	public static final int MULTI_SOURCE_BLOCK_SIZE = 512 * 1024;
	public static final String TEST_TYPE="MooV";

	public MultiSourceDownload(MysterFileStub stu, FileHash hash) {
		this.stub = stub;
		this.hash = hash;
	}
	
	private synchronized void newDownload(MysterFileStub stub) {
		for (int i = 0; i < downloaders.length; i++) {
			if (downloaders[i] == null) {
				downloaders[i] = new InternalSegmentDownloader(stub, i);
				
				downloaders[i].addListener(new SegmentDownloaderHandler(i));
				
				downloaders[i].start();
				
				return;
			}
		}
	}
	
	private synchronized void removeDownload(int barNumber) {
		downloaders[barNumber] = null;
	}

	public void start() {
		downloaders = new InternalSegmentDownloader[5];
	
		progress = new ProgressWindow("Downloading..");
		progress.setProgressBarNumber(downloaders.length);
		progress.show();
		
		for (int i=0; i < downloaders.length; i++) {
			//downloaders[i] = new InternalSegmentDownloader(
			//		stub,
			//		counter++);
			//downloaders[i].addListener(new SegmentDownloaderHandler(i));
			
			progress.setBarColor(new Color(0,(downloaders.length-i)*(255/downloaders.length),150), i);
			
			//downloaders[i].start();
		}
		
		
		try {
			file = new RandomAccessFile(new File("downloadedFile.avi"), "rw");
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		
		IPQueue ipQueue = new IPQueue();
		try { ipQueue.addIP(new MysterAddress("emaline.homeip.net")); } catch (IOException ex) {ex.printStackTrace();}
		try { ipQueue.addIP(new MysterAddress("bluedevil.homeip.net")); } catch (IOException ex) {ex.printStackTrace();}
		
		
		
		//moo.mpg -> "b23b9188a98a3d16854f4167a58e3114"
		crawler = new CrawlerThread(new MultiSourceHashSearch(new MysterType(TEST_TYPE.getBytes()), hash, 
										new HashSearchListener() {
											public void startSearch(HashSearchEvent event) {
												System.out.println("Search Lstnr-> Start search");
											}
											
											public void searchResult(HashSearchEvent event) {
												MysterFileStub stub = event.getFileStub();
												
												System.out.println(stub==null?"Search Lstnr-> No file with that hash here.":"Search Lstnr-> Got result "+stub);
											
												if (stub!=null) {
													newDownload(stub);
													System.out.println("Search Lstnr-> Starting another segment downloader (another source)");
												}
											}
											
											public void endSearch(HashSearchEvent event) {
												System.out.println("Search Lstnr-> End search");
											}
										}
									),
									new MysterType(TEST_TYPE.getBytes()),
									ipQueue,
									new Sayable() {
										public void say(String string) {
											System.out.println(string);
										}},
									null);
									
		crawler.start();
	}
	
	private class SegmentDownloaderHandler extends SegmentDownloaderListener {
		final int bar;
		
		public SegmentDownloaderHandler(int bar) {
			this.bar = bar;
		}
		
		public void connected(SegmentDownloaderEvent e) {
			progress.setValue(0, bar);
		}
		
		public void queued(SegmentDownloaderEvent e) {
			progress.setText("You are in queue position "+e.getQueuePosition(), bar);
		}
		
		public void startSegment(SegmentDownloaderEvent e) {
			progress.setMin(e.getOffset(), bar);
			progress.setMax(e.getOffset()+e.getLength(), bar);
			progress.setValue(e.getOffset(), bar);
		}
		
		public void downloadedBlock(SegmentDownloaderEvent e) {
			progress.setValue(e.getProgress()+e.getOffset(), bar);
		}
		
		public void endSegment(SegmentDownloaderEvent e) {
			progress.setValue(progress.getMax(bar), bar);
		}
		
		public void endConnection(SegmentDownloaderEvent e) {
			progress.setValue(0, bar);
			progress.setText("Connection ended.", bar);
		}
	}

	
	Stack stack = new Stack(); //it's a stack 'cause it doens't matter what data structure so long as add and remove are O(C).
	public synchronized  WorkSegment getNextWorkSegment() {
		if (stack.size() > 0) return (WorkSegment)(stack.pop());
		
		long readLength = (fileLength - fileProgress > MULTI_SOURCE_BLOCK_SIZE ? MULTI_SOURCE_BLOCK_SIZE : fileLength - fileProgress);

		System.out.println("Main Thread -> Adding Work Segment "  +fileProgress + " " + readLength);
		WorkSegment workSegment = new WorkSegment((readLength==0?0:fileProgress), readLength);
		
		fileProgress+=readLength;
		
		return workSegment;
	}
	
	public synchronized void receiveDataBlock(DataBlock dataBlock) {
		try {
			file.seek(dataBlock.offset);
		
			file.write(dataBlock.bytes);
		} catch (IOException ex) {
			ex.printStackTrace(); //fix
		}
	}
	
	public synchronized void receiveExtraSegments(WorkSegment[] workSegments) {
		for (int i=0; i < workSegments.length; i++) {
			stack.push(workSegments[i]);
		}
	}
	
	public void addListener(MultiSourceDownloadListener listener) {
		dispatcher.addListener(listener);
	}
	
	public void removeListener(MultiSourceDownloadListener listener) {
		dispatcher.removeListener(listener);
	}
	
	private class InternalSegmentDownloader extends Thread implements SegmentDownloader{
		MysterFileStub 	stub;
		
		boolean deadFlag = false;
		
		boolean endFlag = false;
		
		private static final int CHUNK_SIZE = 1*1024;
		
		private int downloadNumber;
			
		EventDispatcher dispatcher = new SyncEventDispatcher();

	
		public InternalSegmentDownloader(MysterFileStub stub, int downloadNumber) {
			super(""+downloadNumber);
		
			this.stub 			= stub;
			this.downloadNumber = downloadNumber;
		}
		
		public void addListener(SegmentDownloaderListener listener) {
			dispatcher.addListener(listener);
		}
		
		public void removeListener(SegmentDownloaderListener listener) {
			dispatcher.removeListener(listener);
		}
	
		public void run() {
			MysterSocket socket ;
			try {
				socket = MysterSocketFactory.makeStreamConnection(stub.getMysterAddress());
				
				fireEvent(SegmentDownloaderEvent.CONNECTED, 0, 0, 0, 0);
				
				System.out.println("Work Thread "+getName()+" -> Sending Section Type");
				socket.out.writeInt(com.myster.server.stream.MultiSourceSender.SECTION_NUMBER);
				
				System.out.println("Work Thread "+getName()+" -> Checking Protocol");
				com.myster.client.stream.StandardSuite.checkProtocol(socket.in);
				
				System.out.println("Work Thread "+getName()+" -> Doing Header");
				doHeader(socket);
				
				for (;;) {
					WorkSegment workSegment = getNextWorkSegment(); //(WorkSegment)workQueue.removeFromHead();

					if (doWorkBlock(socket, workSegment)==false) return; //this is for kill signals.. there are also exceptions
					
					
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				finishUp();
			}
		}
		
		private void fireEvent(int id, long offset, long progress, int queuePosition, long length) {
			dispatcher.fireEvent(new SegmentDownloaderEvent(id, offset, progress, queuePosition, length, downloadNumber));
		}
		
		private synchronized void finishUp() {
			deadFlag = true;
			
			receiveExtraSegments(new WorkSegment[]{});
			
			fireEvent(SegmentDownloaderEvent.END_CONNECTION, 0,0,0,0);
			
			System.out.println("Thread "+getName()+" -> Finished.");
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
		
		private boolean doWorkBlock(MysterSocket socket, WorkSegment workSegment) throws IOException {
			long bytesDownloaded = 0;
			
			System.out.println("Work Thread "+getName()+" -> Reading data "+workSegment.startOffset+" "+workSegment.length);
			
			socket.out.writeLong(workSegment.startOffset);
			socket.out.writeLong(workSegment.length);
			
			if (workSegment.isEndSignal()) {
				com.myster.client.stream.StandardSuite.disconnect(socket);
				return false;
			}
			
			System.out.println("Work Thread "+getName()+" -> Reading in QueuePostion");
			for (;;) {
				byte temp_byte = (byte)socket.in.read();
				
				if (temp_byte==0) break;
				
				fireEvent(SegmentDownloaderEvent.QUEUED, 0, 0, temp_byte, 0);
				
				boolean endFlag = this.endFlag;
				
				socket.out.write(endFlag?0:1);
				if (endFlag) throw new IOException("Was told to end.");
			}
			
			socket.out.write(1);
			
			while (bytesDownloaded < workSegment.length) {
				System.out.println("Work Thread "+getName()+" -> Reading in Type");
				byte type = (byte)socket.in.read();
				
				System.out.println("Work Thread "+getName()+" -> Reading in Block Length");
				long blockLength = socket.in.readLong();
				
				System.out.println("Work Thread "+getName()+" -> Block Length: "+blockLength);
				
				fireEvent(SegmentDownloaderEvent.START_SEGMENT, workSegment.startOffset, 0, 0, blockLength);
				
				switch (type) {
					case 'd':
						System.out.println("Work Thread "+getName()+" -> Downloading start");
						downloadDataBlock(socket, workSegment.startOffset, blockLength, workSegment.length);
						System.out.println("Work Thread "+getName()+" -> Downloading finished");
						bytesDownloaded+=blockLength;
						break;
					default:
						System.out.println("Work Thread "+getName()+" -> Unknown type"+type);
						socket.in.skip(blockLength);
						break;
				}
			}
			
			fireEvent(SegmentDownloaderEvent.END_SEGMENT, workSegment.startOffset,workSegment.startOffset+workSegment.length, 0, workSegment.length);
			
			return true;

		}
		
		private void downloadDataBlock(MysterSocket socket, long offset, long length, long segmentLength) throws IOException {
			long bytesDownloaded = 0;
			try {
				for (bytesDownloaded = 0 ; bytesDownloaded < length; ) {
					long calcBlockSize = (length - bytesDownloaded < CHUNK_SIZE?length-bytesDownloaded:CHUNK_SIZE);
					
					byte[] buffer = new byte[(int)calcBlockSize]; //could be made more efficient by using a pool.
					
					socket.in.readFully(buffer);
					
					receiveDataBlock(new DataBlock(bytesDownloaded + offset, buffer));
					
					bytesDownloaded += calcBlockSize; //this line doesn't throw an IO exception
					
					fireEvent(SegmentDownloaderEvent.DOWNLOADED_BLOCK, offset, bytesDownloaded, 0, segmentLength); //nor this.
				}
			} catch (IOException ex) {
				receiveExtraSegments(new WorkSegment[]{new WorkSegment(offset+bytesDownloaded, segmentLength+bytesDownloaded)});
				throw ex;
			}
		}
		
		public void endWhenPossible() {
			endFlag = true;
		}
	}
	
	//Unsafe object due to lack of immutable arrays.
	private static class DataBlock {
		public final byte[] bytes;
		public final long offset;
		
		public DataBlock(long offset, byte[] bytes) {
			this.offset	= offset;
			this.bytes	= bytes ;
		}
	}
	
	private static class WorkSegment {
		public final long startOffset, length;
		
		public WorkSegment(long startOffset, long length) {
			this.startOffset=startOffset;
			this.length=length;
		}
		
		public boolean isEndSignal() {
			return (startOffset==0) && (length==0);
		}
	}

}

class SegmentDownloaderListener extends EventListener {
	/*
		Using the interface below this is the transition table.
		1 -> 2 | 3 | 6
		2 -> 3 | 6
		3 -> 4 | 6
		4 -> 4 | 5 | 6
		5 -> 2 | 3 | 6
		6 -> end
	*/
	
	public void fireEvent(GenericEvent e) {
		SegmentDownloaderEvent event = (SegmentDownloaderEvent)e;
		
		switch (event.getID()) {
			case SegmentDownloaderEvent.CONNECTED:
				connected(event);
				break;
			case SegmentDownloaderEvent.QUEUED:
				queued(event);
				break;
			case SegmentDownloaderEvent.START_SEGMENT:
				startSegment(event);
				break;
			case SegmentDownloaderEvent.DOWNLOADED_BLOCK:
				downloadedBlock(event);
				break;
			case SegmentDownloaderEvent.END_SEGMENT:
				endSegment(event);
				break;
			case SegmentDownloaderEvent.END_CONNECTION:
				endConnection(event);
				break;
			default:
				err();
				break;
		}
	}
	
	public void connected(SegmentDownloaderEvent e) {}			//1
	public void queued(SegmentDownloaderEvent e) {}				//2
	public void startSegment(SegmentDownloaderEvent e) {}		//3
	public void downloadedBlock(SegmentDownloaderEvent e) {}	//4
	public void endSegment(SegmentDownloaderEvent e) {}			//5
	public void endConnection(SegmentDownloaderEvent e) {}		//6
}


//For progress window stats.
//immutable
class SegmentDownloaderEvent extends GenericEvent {
	public static final int CONNECTED 			= 1;
	public static final int QUEUED 				= 2;
	public static final int START_SEGMENT 		= 3;
	public static final int DOWNLOADED_BLOCK	= 4;
	public static final int END_SEGMENT			= 5;
	public static final int END_CONNECTION 	= 6;

	final long 				offset;
	final long 				progress;
	final int 				queuePosition;
	final long				length;
	final int				refNumber;
	
	public SegmentDownloaderEvent(int id, long offset, long progress, int queuePosition, long length, int refNumber) {
		super(id);
		
		this.offset			= offset;
		this.progress		= progress;
		this.queuePosition	= queuePosition;
		this.length			= length;
		this.refNumber		= refNumber;
	}
	
	public int getQueuePosition() {
		return queuePosition;
	}
	
	public long getOffset() {
		return offset;
	}
	
	public long getProgress() {
		return progress;
	}
	
	public long getLength() {
		return length;
	}
	
	public int getReferenceNumber() {
		return refNumber;
	}
}

interface SegmentDownloader {
	public void addListener(SegmentDownloaderListener listener);
	public void removeListener(SegmentDownloaderListener listener);
	public boolean isDead();
}