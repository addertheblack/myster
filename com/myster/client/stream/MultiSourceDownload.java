package com.myster.client.stream;


import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.File;
import java.util.Stack;
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
import com.myster.util.Sayable;
import com.myster.hash.FileHash;
import com.myster.type.MysterType;
import com.myster.net.MysterSocketFactory;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;

public class MultiSourceDownload {
	final FileProgressWindow progress;
	final MysterSocket socket;
	final MysterFileStub stub;
	FileHash hash;								//should be final but can't be...
	long fileLength; 							//should be final but can't be...
		
	File theFile;								//file downloading to
	RandomAccessFile randomAccessFile;			//file downloading to

	InternalSegmentDownloader[] downloaders;
	
	long fileProgress= 0;
	long bytesWrittenOut = 0;
	Stack unfinishedSegments = new Stack(); 	//it's a stack 'cause it doens't
												//matter what data structure so long
												//as add and remove are O(C).

	CrawlerThread crawler;
	EventDispatcher dispatcher = new SyncEventDispatcher();
	
	boolean stopDownload = false;
	
	public static final int MULTI_SOURCE_BLOCK_SIZE = 512 * 1024;

	public MultiSourceDownload(MysterSocket socket, MysterFileStub stub, FileProgressWindow progress) {
		this.socket 		= socket;
		this.stub 			= stub;
		this.progress 		= progress;
	}
	
	private synchronized void newDownload(MysterFileStub stub, MysterSocket socket) {
		if (stopDownload) return;
		
		for (int i = 0; i < downloaders.length; i++) {
			if ((downloaders[i]!=null) && (downloaders[i].getMysterFileStub().equals(stub))) {
				return;
			}
		}
		
		for (int i = 0; i < downloaders.length; i++) {
			if (downloaders[i] == null) {
				if (i+2 > progress.getProgressBarNumber()) {
					progress.setProgressBarNumber(i+2);
					progress.setBarColor(new Color(0,(downloaders.length-i)*(255/downloaders.length),150), i+1);
				}
				
				downloaders[i] = new InternalSegmentDownloader(stub, i);
				
				if (socket!=null) downloaders[i].setSocket(socket); //YUCK!
				
				downloaders[i].addListener(new SegmentDownloaderHandler(i+1));
				
				downloaders[i].start();
				
				return;
			}
		}
	}
	
	private synchronized void newDownload(MysterFileStub stub) {
		newDownload(stub, null);
	}
	
	//remoes a download but doesn't stop a download.
	private synchronized void removeDownload(int barNumber) {
		downloaders[barNumber] = null;
	}
	
	private synchronized void flagToEnd() {
		stopDownload = true;
		
		if (crawler!=null) crawler.flagToEnd();
		
		for (int i=0; i<downloaders.length; i++) {
			if (downloaders[i]!=null) downloaders[i].endWhenPossible();
		}

		cleanUp();
	}
	
	public void end() {
		InternalSegmentDownloader[] downloadersCopy = new InternalSegmentDownloader[downloaders.length];
		
		flagToEnd();
		
		synchronized (this) {
			for (int i = 0; i < downloaders.length; i++) {
				downloadersCopy[i] = downloaders[i]; //have to do this else threading sync problems...
			}
		}
		
		for (int i = 0; i < downloadersCopy.length; i++) {
			try {
				if (downloaders[i]!=null) downloaders[i].end();
			} catch (InterruptedException ex) {
				return;
			}
		}
	}
	
	private static final String EXTENSION = ".i";
	private void getFileThingy() throws IOException {
		File directory = new File(com.myster.filemanager.FileTypeListManager.getInstance().getPathFromType(stub.getType()));
		File file = new File(directory.getPath()+File.separator+stub.getName()+EXTENSION);
		
		if (!directory.isDirectory()) {
			file = askUserForANewFile(file.getName());
			
			if (file == null) throw new IOException("User Cancelled");
		}
		
		while (file.exists())  {
			final String
					CANCEL_BUTTON 	= "Cancel",
					WRITE_OVER		= "Write-Over",
					RENAME			= "Rename";
			
			String answer = (new AnswerDialog(	progress,
												"A file by the name of "+file.getName()+" already exists. What do you want to do.",
												new String[]{CANCEL_BUTTON, WRITE_OVER})
											  ).answer();
			if (answer.equals(CANCEL_BUTTON)) {
				throw new IOException("User Canceled.");
			} else if (answer.equals(WRITE_OVER)) {
				if (!file.delete()) {
					AnswerDialog.simpleAlert(progress, "Could not delete the file.");
					throw new IOException("Could not delete file");
				}
			} else if (answer.equals(RENAME)) {
				file = askUserForANewFile(file.getName());
				
				if (file == null) throw new IOException ("User Cancelled");
			}
		}

		randomAccessFile = new RandomAccessFile(file, "rw");
		theFile = file;
	}
	
	private File askUserForANewFile(String name) throws IOException {
		java.awt.FileDialog dialog = new java.awt.FileDialog(com.general.util.AnswerDialog.getCenteredFrame(),
															 "What do you want to save the file as?",
															 java.awt.FileDialog.SAVE);
		dialog.setFile(name);
		dialog.setDirectory(name);
		
		dialog.show();
		
		File directory = new File(dialog.getDirectory());
		
		if (dialog.getFile() == null) return null; //canceled.
		
		return new File(dialog.getDirectory()+File.separator+dialog.getFile()+EXTENSION);
	}
	
	private boolean assertLengthAndHash() throws IOException {
		try {
			com.myster.mml.RobustMML mml = StandardSuite.getFileStats(socket, stub);
			
			String hashString = mml.get(com.myster.filemanager.FileItem.HASH_PATH+com.myster.hash.HashManager.MD5);
			String fileLengthString = mml.get("/size");
			
			if (hashString == null || fileLengthString == null) return false;
			
			try {
				hash = com.myster.hash.SimpleFileHash.buildFromHexString(com.myster.hash.HashManager.MD5, hashString);
			} catch (NumberFormatException ex) {
				return false;
			}
			
			try {
				fileLength = Long.parseLong(fileLengthString);
			} catch (NumberFormatException ex) {
				return false;
			}
			
			return true;
		} catch (UnknownProtocolException ex) {
			return false;
		}
	}

	public synchronized boolean start() throws IOException {
		downloaders = new InternalSegmentDownloader[5];
	
		if (!assertLengthAndHash()) return false;
	
		progress.setTitle("Downloading From Multiple Sources..");
		progress.setProgressBarNumber(1);
		
		try {
			getFileThingy();
		} catch (IOException ex) {
			progress.hide();
			throw ex;
		}
		
		
		progress.setBarColor(Color.blue, 0);
		progress.startBlock(FileProgressWindow.BAR_1, 0, fileLength);
		progress.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				flagToEnd();
				end();
				progress.setVisible(false);
			}
		});
		
		progress.setText("Downloading file "+stub.getName());
		
		//for (int i=0; i < downloaders.length; i++) {
		//	progress.setBarColor(new Color(0,(downloaders.length-i)*(255/downloaders.length),150), i+1);
		//}
		
		
		startCrawler();
		
		newDownload(stub, socket);
		
		return true;
	}
	
	private void startCrawler() {
		IPQueue ipQueue = new IPQueue();
		
		String[] startingIps = com.myster.tracker.IPListManagerSingleton.getIPListManager().getOnRamps();
		
		ipQueue.addIP(stub.getMysterAddress());
		ipQueue.getNextIP(); //this is so we don't start downloading from the first one again.
		
		for (int i = 0; i < startingIps.length; i++) {
			try { ipQueue.addIP(new MysterAddress(startingIps[i])); } catch (IOException ex) {ex.printStackTrace();}
		}
	
		crawler = new CrawlerThread(new MultiSourceHashSearch(stub.getType(), hash, 
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
												startCrawler();
											}
										}
									),
									stub.getType(),
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
			progress.startBlock(bar, e.getOffset(), e.getOffset()+e.getLength());
			progress.setPreviouslyDownloaded(e.getOffset(), bar);
			progress.setValue(e.getOffset(), bar);
			progress.setText("Downloading from "+e.getMysterFileStub().getMysterAddress(), bar);
		}
		
		public void downloadedBlock(SegmentDownloaderEvent e) {
			progress.setValue(e.getProgress()+e.getOffset(), bar);
		}
		
		public void endSegment(SegmentDownloaderEvent e) {
			progress.setValue(progress.getMax(bar), bar);
		}
		
		public void endConnection(SegmentDownloaderEvent e) {
			progress.setValue(0, bar);
			progress.setText("Searching for new source...", bar);
		}
	}

	
	public synchronized  WorkSegment getNextWorkSegment() {
		if (unfinishedSegments.size() > 0) return (WorkSegment)(unfinishedSegments.pop());
		
		long readLength = (fileLength - fileProgress > MULTI_SOURCE_BLOCK_SIZE ? MULTI_SOURCE_BLOCK_SIZE : fileLength - fileProgress);

		System.out.println("Main Thread -> Adding Work Segment "  +fileProgress + " " + readLength);
		WorkSegment workSegment = new WorkSegment((readLength==0?0:fileProgress), readLength);
		
		fileProgress+=readLength;
		
		return workSegment;
	}
	
	public synchronized void receiveDataBlock(DataBlock dataBlock) {
		try {
			randomAccessFile.seek(dataBlock.offset);
		
			randomAccessFile.write(dataBlock.bytes);
			
			bytesWrittenOut+=dataBlock.bytes.length;
			progress.setValue(bytesWrittenOut);
			
			if (bytesWrittenOut == fileLength) {
				done();
			}
		} catch (IOException ex) {
			ex.printStackTrace(); //fix
		}
	}
	
	//call when downlaod has completed sucessfully.
	private synchronized void done() {
		cleanUp();
		progress.setText("File transfer complete.");
		
		File someFile = null;
		//try {
		
			String path = theFile.getAbsolutePath();
			someFile = new File(path.substring(0, path.length()-1));
			System.out.println(path.substring(0, path.length()-1));

		
		if (someFile.exists()) {
			AnswerDialog.simpleAlert(progress, "Could not rename file from \""+theFile.getName()+"\" to \""+someFile.getName()+"\" because a file by that name already exists.");
			return;
		}
		
		if (!theFile.renameTo(someFile)) {
			AnswerDialog.simpleAlert(progress, "Could not rename file from \""+theFile.getName()+"\" to \""+someFile.getName()+"\" because an unspecified error occured.");
			return;
		}
		
		progress.setText("File downloaded");
	}
	
	//call when download is over but not done.
	private synchronized void cleanUp() {
		try {randomAccessFile.close();} catch (IOException ex) {}
		
		if (crawler!=null) crawler.flagToEnd();
	}
	
	public synchronized void receiveExtraSegments(WorkSegment[] workSegments) {
		for (int i=0; i < workSegments.length; i++) {
			unfinishedSegments.push(workSegments[i]);
		}
	}
	
	public void addListener(MSDownloadListener listener) {
		dispatcher.addListener(listener);
	}
	
	public void removeListener(MSDownloadListener listener) {
		dispatcher.removeListener(listener);
	}
	
	private class InternalSegmentDownloader extends Thread implements SegmentDownloader{
		MysterFileStub 	stub;
		MysterSocket	socket;
		
		boolean deadFlag = false;
		
		boolean endFlag = false;
		
		private static final int CHUNK_SIZE = 2*1024;
		
		private int downloadNumber;
			
		EventDispatcher dispatcher = new SyncEventDispatcher();

	
		public InternalSegmentDownloader(MysterFileStub stub, int downloadNumber) {
			super("SegmentDownloader "+downloadNumber+" for "+stub.getName());
		
			this.stub 			= stub;
			this.downloadNumber = downloadNumber;
		}
		
		public void addListener(SegmentDownloaderListener listener) {
			dispatcher.addListener(listener);
		}
		
		public void removeListener(SegmentDownloaderListener listener) {
			dispatcher.removeListener(listener);
		}
		
		public void setSocket(MysterSocket socket) {
			if (this.socket==null) this.socket = socket;
		}
		
		public MysterFileStub getMysterFileStub() {
			return stub;
		}
	
		public void run() {
			MysterSocket socket = null;
			try {
				if (this.socket==null) socket = MysterSocketFactory.makeStreamConnection(stub.getMysterAddress());
				else socket = this.socket; //yuck.
				
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
				try {
					socket.close();
				} catch (Exception ex) {}
				finishUp();
			}
		}
		
		private void fireEvent(int id, long offset, long progress, int queuePosition, long length) {
			dispatcher.fireEvent(new SegmentDownloaderEvent(id, offset, progress, queuePosition, length, downloadNumber, stub));
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
				
				socket.out.write(1); //we are never polite
				
				if (temp_byte==0) break;
				
				fireEvent(SegmentDownloaderEvent.QUEUED, 0, 0, temp_byte, 0);

				if (endFlag) throw new IOException("Was told to end.");
			}
			

			while (bytesDownloaded < workSegment.length) {
				System.out.println("Work Thread "+getName()+" -> Reading in Type");
				byte type = (byte)socket.in.read();
				
				
				
				switch (type) {
					case 'd':
						long blockLength = socket.in.readLong();
						
						fireEvent(SegmentDownloaderEvent.START_SEGMENT, workSegment.startOffset, 0, 0, blockLength);
						
						System.out.println("Work Thread "+getName()+" -> Downloading start");
						downloadDataBlock(socket, workSegment.startOffset, blockLength, workSegment.length);
						System.out.println("Work Thread "+getName()+" -> Downloading finished");
						bytesDownloaded+=blockLength;
						break;
					case 'i':
						progress.setURL("");
						progress.makeImage(getDataBlock(socket));
						break;
					case 'u':
						socket.in.readInt(); //if this is not 0 is error.
						socket.in.readShort(); //if this is not 0 is error.
						String url = socket.in.readUTF(); //UTFs are preceeded by 16-bit short
						System.out.println("url received :"+url);
						progress.setURL(url);
						break;
					default:
						System.out.println("Work Thread "+getName()+" -> Unknown type"+type);
						socket.in.skip(socket.in.readLong());
						break;
				}
			}
			
			fireEvent(SegmentDownloaderEvent.END_SEGMENT, workSegment.startOffset,workSegment.startOffset+workSegment.length, 0, workSegment.length);
			
			return true;

		}
		
		private byte[] getDataBlock(MysterSocket socket) throws IOException {
			byte[] buffer = new byte[(int)socket.in.readLong()];
			
			socket.in.readFully(buffer);
			
			return buffer;
		}
		
		private void downloadDataBlock(MysterSocket socket, long offset, long length, long segmentLength) throws IOException {
			long bytesDownloaded = 0;
			try {
				for (bytesDownloaded = 0 ; bytesDownloaded < length; ) {
					long calcBlockSize = (length - bytesDownloaded < CHUNK_SIZE?length-bytesDownloaded:CHUNK_SIZE);
					
					byte[] buffer = new byte[(int)calcBlockSize]; //could be made more efficient by using a pool.
					
					if (endFlag) throw new IOException("was asked to end");
					
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
		
		public void end() throws InterruptedException {
			endWhenPossible();
			
			join();
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
	final MysterFileStub	stub;
	
	public SegmentDownloaderEvent(int id, long offset, long progress, int queuePosition, long length, int refNumber, MysterFileStub stub) {
		super(id);
		
		this.offset			= offset;
		this.progress		= progress;
		this.queuePosition	= queuePosition;
		this.length			= length;
		this.refNumber		= refNumber;
		this.stub		= stub;
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
	
	public MysterFileStub getMysterFileStub() {
		return stub;
	}
}

interface SegmentDownloader {
	public void addListener(SegmentDownloaderListener listener);
	public void removeListener(SegmentDownloaderListener listener);
	public boolean isDead();
}