/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.server.stream;

import java.net.*;
import java.io.*;
import java.awt.*;

import com.general.util.*;

import com.myster.filemanager.*;
import com.myster.server.event.*;
import com.myster.server.DownloadInfo;
import com.myster.server.BannersManager;
import com.myster.server.ConnectionContext;
import com.myster.client.stream.StandardSuite;
import com.myster.net.MysterSocketFactory;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.pref.Preferences;
import com.myster.type.MysterType;

import com.myster.transferqueue.*;

public class FileSenderThread extends ServerThread {
	//public constants
	public static final int NUMBER=80;
	
	public int getSectionNumber() {
		return NUMBER;
	}
	
	public Object getSectionObject() { //session objects are sent to events
		return new ServerDownloadDispatcher();
	}
	
	public void section(ConnectionContext context) throws IOException {
		ServerTransfer transfer = new ServerTransfer(FileTypeListManager.getInstance(), (ServerDownloadDispatcher)(context.sectionObject));
		
		try {
			transfer.init(context.socket);
			
			
			if (kickFreeloaders()) {
				try {
					//if (!context.serverAddress.getIP().equals("127.0.0.1")) {
						StandardSuite.disconnectWithoutException(MysterSocketFactory.makeStreamConnection(context.serverAddress));
						throw new IOException("Test code");
					//}
				} catch (Exception ex) { //if host is not reachable it will end up here.
					ServerTransfer.freeloaderComplain(context.socket.out);
					throw new IOException("Client is a leech."); //bye bye..
				}
			}
			
			try {
				context.transferQueue.doDownload(transfer.getDownloader()); //wow
			} catch (MaxQueueLimitException ex) {
				throw new IOException("Cannot queue this download because queue is full");
			}
			
			/*
			if (false == false) {
				if (context.downloadQueue.addDownloadToQueue(transfer.getQueuedTransfer())) {		
					try {
						transfer.waitUntilDone();
					} catch (InterruptedException ex) {
						throw new IOException("Interrupted IO.");
					}
				} else {
					throw new IOException("Server downloads are overloaded."); //bye bye.. Server is over loaded.
				}
			}*/
		} catch (IOException ex) {
			transfer.cleanUp(); //does usefull things like fires an event to say download is dead.
			throw ex;
		} finally {
			transfer.endBlock();
		}
	}
	
	static FreeLoaderPref p;
	public static synchronized FreeLoaderPref getPrefPanel() {
		if (p==null) p=new FreeLoaderPref();
		return p;
	} 
	
	private static String freeloadKey="ServerFreeloaderKey/";
	public static boolean kickFreeloaders() {
		boolean b_temp=false;
		
		try {
			b_temp=Boolean.valueOf(Preferences.getInstance().get(freeloadKey)).booleanValue();
		} catch (NumberFormatException ex) {
			//nothing
		} catch (NullPointerException ex) {
			//nothing
		}
		return b_temp;
	}
	
	private static void setKickFreeloaders(boolean b) {
		Preferences.getInstance().put(freeloadKey, ""+b);
	}	

	public static class FreeLoaderPref extends Panel {	
		private final Checkbox freeloaderCheckbox;
		
		public FreeLoaderPref() {
			setLayout(new FlowLayout());
		
			freeloaderCheckbox=new Checkbox("Kick Freeloaders");
			add(freeloaderCheckbox);
		}
		
		public void save() {
			setKickFreeloaders(freeloaderCheckbox.getState());
		}
		
		public void reset() {
			freeloaderCheckbox.setState(kickFreeloaders());
		}
		
		public Dimension getPreferredSize() {
			return new Dimension(100, 1);
		}
	}
	
	
	public static class ServerTransfer {
		//Events
		ServerDownloadDispatcher dispatcher;
		DownloadInfo downloadInfo;
		
		//Server stats
		long bytessent=0;
		String filename="?";
		String filetype="?";
		long filelength=0;
		long starttime=1;
		MysterAddress remoteIP;
		long initialOffset=0;
		
		//Threading
		Semaphore sem=new Semaphore(0);
		
		//?
		boolean endflag=false;
		
		//io
		private File file;
		private MysterSocket socket;
		private DataInputStream in, fin;
		private DataOutputStream out, fout;
		
		//Managers
		private FileTypeListManager typelist;
		
		//private constants
		private static final String IMAGE_DIRECTORY="Images/";
		private static final int BUFFERSIZE=8192;
		private static final int BURSTSIZE=512*1024;	
			
		protected ServerTransfer (FileTypeListManager typelist, ServerDownloadDispatcher dispatcher) {
			this.typelist	= typelist;
			this.dispatcher	= dispatcher;
			this.downloadInfo = new Stats();
			
			starttime=System.currentTimeMillis();
			
			fireEvent(ServerDownloadEvent.SECTION_STARTED,0);
		}
		
		public ServerDownloadDispatcher getDispatcher() {
			return dispatcher;//hurray!
		}
		
		//public QueuedTransfer getQueuedTransfer() {
		//	return new DQueuePrivateClass();
		//}
		
		public Downloader getDownloader() {
			return new DownloaderPrivateClass();
		}
		
		public static void freeloaderComplain(DataOutputStream out) throws IOException {
			byte[] queuedImage=new byte[4096];
			int sizeOfImage = 0;
			int tempint;
			
			InputStream qin=ServerTransfer.class.getResourceAsStream("firewall.gif");
			
			//loading image...
			
			do {
				tempint=qin.read(queuedImage, sizeOfImage,4096-sizeOfImage);
				if (tempint>0) sizeOfImage+=tempint;
			} while (tempint!=-1);
			
			//mapping errors.
			if (sizeOfImage==-1) sizeOfImage=-1;
			if (sizeOfImage==4096) sizeOfImage=-1;

			out.writeInt(6669);
			out.writeByte('i');
			out.writeLong(sizeOfImage);
			out.write(queuedImage, 0, sizeOfImage);
			
			sendURL(out, "http://www.mysternetworks.com/information/dl_error_faq.html");
		}
		
		
		byte[] queuedImage;
		int sizeOfImage=0;
		private void refresh(int position) throws IOException {
			
			
			if (endflag) throw new IOException("Thread is dead.");
			fireEvent(ServerDownloadEvent.QUEUED, position);
			try {
				if (queuedImage==null) { //if not loaded then load.
					queuedImage = new byte[4096];
					int tempint;
					InputStream qin=this.getClass().getResourceAsStream("queued.gif");
					
					//loading image...
					do {
						tempint=qin.read(queuedImage, sizeOfImage,4096-sizeOfImage);
						if (tempint>0) sizeOfImage+=tempint;
					} while (tempint!=-1);
					
					//mapping errors.
					if (sizeOfImage==-1) sizeOfImage=-1;
					if (sizeOfImage==4096) sizeOfImage=-1;
				}
				
				sendQueue(position);
			
				out.writeInt(6669);
				out.writeByte('i');
				out.writeLong(sizeOfImage);
				out.write(queuedImage, 0, sizeOfImage);
	
			} catch (IOException ex) {
				throw ex;
			} catch (RuntimeException ex) {
				ex.printStackTrace();
				throw ex;
			}
		}
	
		private void waitUntilDone() throws InterruptedException {
			sem.getLock();
		}
	
		
		
		private void init(MysterSocket socket) throws IOException {
			this.socket=socket; //io
			in=new DataInputStream(socket.getInputStream());
			out=new DataOutputStream(socket.getOutputStream());
		
			remoteIP=new MysterAddress(socket.getInetAddress());	//stats
			
			starttime=System.currentTimeMillis();

			MysterType type = new MysterType(in.readInt());
			
			filetype="" + type;			//stats
			filename=in.readUTF();	//stats
			file=typelist.getFile(type, filename);	//io
			filelength=file.length();			//stats
			initialOffset=in.readLong();		//initial offset for restarting file transfers half way done!
			bytessent=initialOffset;			//hereafter refered to as....
			
			if (file==null||(file.length()-initialOffset)<0) {		//File does not exist or if initialoffset is larger than the file!
				out.writeInt(0);
				//loop..
				endflag=true; //<-- signals CM that thread should continue. Bit of a hack.
				cleanUp();

			}
			else {
				out.writeInt(1);
				out.writeLong(file.length()-initialOffset);			//Sends size of file that remains to be sent.
			}
		}
		
		
		/**
		 * 	Protcal->
		 *	Get TYPE and FILENAME and initial offset (long)
		 *	Send 1 or 0 (Int);
		 *	if 1 send LONG of filelength
		 *	send char (data type 'd' for data 'g' for graphic 'u' for URL)
		 *  send lenght of data being sent
		 *  send that data... etc.. until all of length of data (or 'd') has been sent.	
		 *
		 * eg: get ("MPG3" -> "song.mp3" -> 0)
		 *     send (3000000 -> 'i' -> 20000 -> data -> 'u' -> 13 -> <data> -> 'd' -> 1000000 -> <data>
		 * -> 'i' -> 21000 -> <data> -> 'd' -> 200000 -> <data> -> <done>.
		 */
		
		private void startDownload() {
			try {
				fireEvent(ServerDownloadEvent.STARTED, -1); //-1 == queued.
				if (endflag) throw new IOException("toss and catch");
				sendFile(file, out);
				
				try {
					in.close();
					out.close();
					socket.close();
				} catch (Exception ex) {
					//again.. I can't do anything if it fails...
					//But would still like to try and close them if the first one fails.
				}
			} catch (Exception ex) {
				try {
					socket.close();
				} catch (Exception exp) {
					//again.. I can't do anything if it fails...
					//But would still like to try and close them if the first one fails.
				}
			} finally {
				fireEvent(ServerDownloadEvent.FINISHED, (char)-1);
				cleanUp();
			}
		}
		
		private boolean duplicate=false;
		private void cleanUp() {
			if (duplicate) return;
			sem.signal();
			duplicate=true; //just so this is not called twice.
			endflag=true;
		}
		
		private void cleanUpFromError() {
			try {
				socket.close();
			} catch (Exception exp) {
				//again.. I can't do anything if it fails...
				//But would still like to try and close them if the first one fails.
			}
		}
	

		/**
		 * Sends the file..
		 */
		private void sendFile(File f, DataOutputStream out) throws Exception{
			//Opens connection to file...
			try {
				try {
					fin=new DataInputStream(new FileInputStream(f));
					long temp=fin.skip(bytessent);	//bytes sent should be 0 but may be not since initial offset is set to bytes sent.
					if (temp!=bytessent) throw new Exception("Skip() method not working right. found bug in API.AGHH");
				} catch (Exception ex) {
					throw ex;
				}
	
				starttime=System.currentTimeMillis();
				do {
					sendImage(out); //sends URL too.
				} while (sendDataPacket()==BURSTSIZE);
			} finally {
				try {fin.close(); } catch (Exception ex) {}
			}
		}
		
		
	
		//code 'd'
		private int sendDataPacket() {
			long bytesremaining=(BURSTSIZE<(filelength-bytessent)) ? BURSTSIZE : filelength-bytessent;
			try {
				out.writeInt(6669);
				out.writeByte('d');
				out.writeLong(bytesremaining);
			} catch (Exception ex) {return -1;}
			
		
			byte[] buffer=new byte[BUFFERSIZE];
			
			for (int j=0; j<(bytesremaining/BUFFERSIZE); j++) {
				if (readWrite(fin, BUFFERSIZE, buffer)==-1) return -1;
			}
			
			if (readWrite(fin,(int)(bytesremaining%BUFFERSIZE), buffer)==-1) return -1;
			//System.gc();
			return (int)bytesremaining;
		}
	
		//code 'm' (not used?)
		private void sendMessage(String m) throws IOException {
			byte[] bytes=m.getBytes();
			long length=bytes.length;
			out.writeInt(6669);
			out.write('m');
			out.writeLong(length);
			out.write(bytes);
		}
		
		//code 'q'
		private void sendQueue(int i) throws IOException {
			out.writeInt(6669);
			out.writeByte('q');
			out.writeLong(4); //an int is 4 bytes.
			out.writeInt(i);
		}
	
		//A utility method that allows one to send a banner URL from an Image name
		public static void sendURLFromImageName(DataOutputStream out, String imageName) throws IOException {
			String url = BannersManager.getURLFromImageName(imageName);
			
			if (url == null) return;
			
			sendURL(out, url);
		}
		
		//code 'u'
		public static void sendURL(DataOutputStream out, String url) throws IOException {
			out.writeInt(6669);
			out.write('u');
			out.writeInt(0);  //padding 'cause writeUTF preceed the UTF with a short.
			out.writeShort(0);
			out.writeUTF(url);
		}
	
		//code 'i'
		public static void sendImage(DataOutputStream out) throws IOException {
			DataInputStream in = null;
			File file;
			
			
			String imageName = BannersManager.getNextImageName();
			
			if (imageName == null) return;
			
			file = BannersManager.getFileFromImageName(imageName);
		
			if (file==null) { //is needed (Threading issue)
				return;
			}
			
			try {
				in=new DataInputStream(new FileInputStream(file));

			
				byte[] bytearray=new byte[(int)file.length()];

				in.readFully(bytearray, 0, (int)file.length());
				
				out.writeInt(6669);
				out.write('i');
				out.writeLong(bytearray.length);
				out.write(bytearray);
				
				sendURLFromImageName(out, imageName);
			} finally {
				try {
					in.close();
				} catch (Exception ex) {}
			}
			
		}
		
		
		private int readWrite(DataInputStream in, int size, byte[] buffer) {
			if (size==0) return 0;
			try {
				in.readFully(buffer,0,size);
				out.write(buffer,0,size);
				bytessent+=size;
			} catch (IOException ex) {return -1;}
			return size;
		}
		
		private int read(BufferedInputStream i, byte array[]) {
			try {
				return i.read(array, 0, BUFFERSIZE);
			} catch (Exception ex) {
				return -1;
			}
		}
		
		public MysterAddress getRemoteIP() {
			return new MysterAddress(socket.getInetAddress()); //!
		}
		
		private void disconnect() {
			endflag=true;
			cleanUpFromError();
			cleanUp();
		}
		
		public void endBlock() {
			fireEvent(ServerDownloadEvent.SECTION_FINISHED, (char)-1);
			endflag=true;
		}
		
		private void fireEvent(int id, int c) {
			dispatcher.fireEvent(new ServerDownloadEvent(id, 
														remoteIP,
														NUMBER,
														filename,
														filetype,
														c, 
														bytessent-initialOffset, 
														filelength,
														downloadInfo));
		}

	
	
		private class Stats implements DownloadInfo {
			public double getTransferRate(){
				try {
					return (bytessent-initialOffset)/((System.currentTimeMillis()-starttime)/1000);
				} catch (Exception ex) {}
				return 0;
			}
			
			
			public long getStartTime() {
				return starttime;
			}
			
			public long getAmountDownloaded() {
				return bytessent;
			}
			
			public long getInititalOffset() {
				return initialOffset;
			}
			
			public String getFileName() {
				return filename;
			}
			
			public String getFileType() {
				return filetype;
			}
			
			public long getFileSize() {
				return filelength;
			}
			
			public MysterAddress getRemoteIP() {
				return FileSenderThread.ServerTransfer.this.getRemoteIP();
			}
			
			public void disconnectClient() {
				FileSenderThread.ServerTransfer.this.disconnect();
			}
			
			public boolean isDone() {
				return FileSenderThread.ServerTransfer.this.endflag;
			}
		}
		
			/*
		private class DQueuePrivateClass implements QueuedTransfer {
		
			public void refresh(int i) throws IOException {
				FileSenderThread.ServerTransfer.this.refresh(i);
			}
			
			public void disconnect() {
				FileSenderThread.ServerTransfer.this.disconnect();
			}
			
			public void startDownload() {
				FileSenderThread.ServerTransfer.this.startDownload();
			}
			
			public boolean isDone() {
				return FileSenderThread.ServerTransfer.this.endflag;
			}
		}*/
		
		private class DownloaderPrivateClass implements Downloader {
		
			public void queued(QueuedStats queuedStats) throws IOException {
				FileSenderThread.ServerTransfer.this.refresh(queuedStats.getQueuePosition());
			}
			
			public void download() {
				FileSenderThread.ServerTransfer.this.startDownload();
			}
		}
	}
}
