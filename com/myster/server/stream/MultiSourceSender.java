package com.myster.server.stream;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.DataInputStream;

import com.myster.net.MysterSocket;
import com.myster.type.MysterType;
import com.myster.filemanager.FileTypeListManager;
import com.myster.server.ConnectionContext;
import com.myster.server.DownloadInfo;
import com.myster.server.event.ServerDownloadDispatcher;
import com.myster.server.event.ServerDownloadEvent;

		//1) read in offset(long) + length(long)
		
		//send back queue position (int, negative if file not found) (not meta data) ->
		//cont until 0
		//read in continue <-
		
		//send file + meta data in the same protocol as older one.
		
		//When all of file has been sent ->
		
		//repeat from 1

public class MultiSourceSender extends ServerThread {
	public boolean endFlag = false;

	public static final int SECTION_NUMBER=88888; //testing port

	public int getSectionNumber() {
		return SECTION_NUMBER;
	}
	
	public Object getSectionObject() {
		return new ServerDownloadDispatcher();
	}

	public void section(ConnectionContext context) throws IOException {
		MultiSourceDownloadInstance download = new MultiSourceDownloadInstance((ServerDownloadDispatcher)(context.sectionObject));
		
		try {
			download.download(context.socket);
		} catch (IOException ex) {
			//panic time
			ex.printStackTrace();
		} finally {
			download.endBlock();
		}
	}


	private class MultiSourceDownloadInstance {
		ServerDownloadDispatcher dispatcher;
		
		String remoteIP = "??";
		String fileName = "??";
		MysterType type = new MysterType("????".getBytes());
		long fileLength = 0, startTime = System.currentTimeMillis(), amountDownloaded= 0;
		DownloadInfo downloadInfo;
		
		public MultiSourceDownloadInstance(ServerDownloadDispatcher dispatcher) {
			this.dispatcher 	= dispatcher;
			this.downloadInfo 	= new Stats();
			
			fireEvent(ServerDownloadEvent.SECTION_STARTED, -1);
		}
		
		
		public void download(MysterSocket socket) throws IOException {
			try {
				DataOutputStream out = socket.out;
				DataInputStream in = socket.in;
				
				type = new MysterType(in.readInt());
				
				fileName = in.readUTF();
				
				File file = FileTypeListManager.getInstance().getFile(type, fileName);
				
				if (file==null) {
					out.write(0);
					return ;
				} else {
					out.write(1);
				}
				
				long myCounter = 0;
				
				for (;;) {
					amountDownloaded=0;
					fireEvent(ServerDownloadEvent.STARTED, -1);
					startTime = System.currentTimeMillis();
					
					long offset = in.readLong();
					fileLength = in.readLong();
					
					if ((offset==0) && (fileLength==0)) break;
					
					if ((fileLength < 0) |
							(offset < 0) | 
							(fileLength + offset <= 0) | 
							(fileLength + offset > file.length())) {
						throw new IOException("Client sent garbage fileLengths and offsets of "+fileLength+" and " +offset);		
					}
					
					if (fileLength == -1) fileLength = file.length();
					
					if (myCounter > file.length()) throw new IOException("User has request more bytes than there are in the file!");
					
					out.write(0); //this would loop until 1
					fireEvent(ServerDownloadEvent.QUEUED, 0);
					if (in.read() != 1) break; //end
					
					sendFileSection(socket, file, offset, fileLength);
					
					myCounter += fileLength; //this is so a client cannot suck data forever.
					
					fireEvent(ServerDownloadEvent.FINISHED, -1);
				}
			} catch (IOException ex) {
				ex.printStackTrace();
				fireEvent(ServerDownloadEvent.FINISHED, -1);
				throw ex;
			} finally {
				//beep
			}
		}
		
		private void endBlock() {
			fireEvent(ServerDownloadEvent.SECTION_FINISHED, -1);
		}
		
		long CHUNK_SIZE = 4024;
		private void sendFileSection(MysterSocket socket, File file_arg, long offset, long length) throws IOException {
			RandomAccessFile file =  null;
			try {
				file = new RandomAccessFile(file_arg, "r");
				file.seek(offset);
				
				byte[] buffer = new byte[(int)CHUNK_SIZE];
				
				socket.out.write('d');
				socket.out.writeLong(length);
				
				
				for (long counter = 0; counter < (length); ) {
					long calcBlockSize = (length - counter < CHUNK_SIZE?length - counter:CHUNK_SIZE);
					
					if (endFlag) throw new DisconnectCommandException();
					
					file.readFully(buffer, 0, (int)calcBlockSize);
					
					socket.out.write(buffer, 0, (int)calcBlockSize);
					
					counter+=calcBlockSize;
					amountDownloaded = counter ; // for stats
				}
			
			} finally {
				if (file != null) file.close();
			}
		}
		
		private void fireEvent(int id, int queuePosition) {
			dispatcher.fireEvent(new ServerDownloadEvent(id, 
														remoteIP,
														getSectionNumber(),
														fileName,
														type.toString(),
														queuePosition, 
														amountDownloaded, 
														fileLength,
														downloadInfo));
		}
		
		private class Stats implements DownloadInfo {
			public double getTransferRate() {
				long elapsedTime = System.currentTimeMillis() - startTime;
				
				final int ONE_SECOND = 1000;
				
				if ((elapsedTime < ONE_SECOND) ||
					(amountDownloaded==0)) return 0;
					
				return (double)amountDownloaded / (double)(elapsedTime/ONE_SECOND);
			}
			
			public long getStartTime() {
				return startTime;
			}
			
			public long getAmountDownloaded() {
				return amountDownloaded;
			}
			
			public String getFileName() {
				return fileName;
			}
			
			public String getFileType() {
				return type.toString();
			}
			
			public long getFileSize() {
				return fileLength;
			}
			
			public void disconnectClient() {
				endFlag = true;
			}
		}
	}
	
	private class DisconnectCommandException extends RuntimeException  {
	
	}
}