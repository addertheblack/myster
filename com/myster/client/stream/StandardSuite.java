

package com.myster.client.stream;

import java.util.Vector;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.File;

import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.net.MysterAddress;
import com.myster.mml.RobustMML;
import com.myster.mml.MMLException;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.hash.FileHash;

import com.myster.util.FileProgressWindow;
/**
	Contains many of the more common (simple!) stream based connection sections.
*/

public class StandardSuite {

	public static Vector getSearch(MysterAddress ip, MysterType searchType, String searchString) throws IOException {
		MysterSocket socket=null;
		try {
			socket=MysterSocketFactory.makeStreamConnection(ip);
			return getSearch(socket,searchType, searchString);
		} finally {
			disconnectWithoutException(socket);
		}
	}
	
	public static Vector getSearch(MysterSocket socket, MysterType searchType,String searchString) throws IOException {
		Vector searchResults=new Vector();
		
		socket.out.writeInt(35);
		
		checkProtocol(socket.in);
		
		socket.out.write(searchType.getBytes());
		socket.out.writeUTF(searchString);

		for (String temp=socket.in.readUTF(); !temp.equals(""); temp=socket.in.readUTF())
				searchResults.addElement(temp);
				
		return searchResults;
	}

	public static Vector getTopServers(MysterAddress ip, MysterType searchType) throws IOException {
		MysterSocket socket=null;
		try {
			socket=MysterSocketFactory.makeStreamConnection(ip);
			return getTopServers(socket,searchType);
		} finally {
			disconnectWithoutException(socket);
		}
	}
	
	public static Vector getTopServers(MysterSocket socket, MysterType searchType) throws IOException {
		Vector ipList=new Vector();
		
		socket.out.writeInt(10);	//Get top ten the 10 is the command code... not the length of the list!
		
		checkProtocol(socket.in);
		
		socket.out.write(searchType.getBytes());
		
		for (String temp=socket.in.readUTF(); !temp.equals(""); temp=socket.in.readUTF()) {
			ipList.addElement(temp);
		}
			
		return ipList;
	}

	public static Vector getTypes(MysterAddress ip) throws IOException {
		MysterSocket socket=null;
		try {
			socket=MysterSocketFactory.makeStreamConnection(ip);
			return getTypes(socket);
		} finally {
			disconnectWithoutException(socket);
		}
	}


	public static Vector getTypes(MysterSocket socket) throws IOException {
		Vector container=new Vector();
	
		socket.out.writeInt(79);

		checkProtocol(socket.in);

		for (String temp=socket.in.readUTF(); !temp.equals(""); temp=socket.in.readUTF()) {
			container.addElement(temp);
		}
		
		return container;
	}

	public static RobustMML getServerStats(MysterSocket socket) throws IOException {
		socket.setSoTimeout(90000); //? Probably important in some way or other.

		socket.out.writeInt(101); 

		checkProtocol(socket.in);

		try {
			return new RobustMML(socket.in.readUTF());
		} catch (MMLException ex) {
			throw new ProtocolException("Server sent a corrupt MML String");
		}
	}

	public static RobustMML getServerStats(MysterAddress ip) throws IOException  { //should be abstracted at some point.
		MysterSocket socket=null;
		try {
			socket=MysterSocketFactory.makeStreamConnection(ip);
			return getServerStats(socket);
		} finally {
			disconnectWithoutException(socket);
		}
	}
	
	/**
	*	downloadFile downloads a file by starting up a MultiSourceDownload or Regular old style download
	*	whichever is appropriate.
	*	<p>
	*	THIS ROUTINE IS ASYNCHRONOUS!
	*/
	public static void downloadFile(final MysterAddress ip, final MysterFileStub stub) {
		(new DownloadThread(ip, stub)).start();
	}
	
	private static class DownloadThread extends com.myster.util.MysterThread {
		private MysterAddress ip;
		private MysterFileStub stub;
	
		public DownloadThread(MysterAddress ip, MysterFileStub stub) {
			this.ip = ip;
			this.stub = stub;
		}
	
		public void run() {
			MysterSocket socket=null;
			try {
				socket=MysterSocketFactory.makeStreamConnection(ip);
			} catch (Exception ex) {
			 	com.general.util.AnswerDialog.simpleAlert("Could not connect to server.");
			 	return;
			}
			
			try {
				downloadFile(socket, stub);
			} catch (IOException ex) {
				//..
			} finally {
				disconnectWithoutException(socket);
			}
			
		}
		
			// should not be public
		private void downloadFile(final MysterSocket socket, final MysterFileStub stub ) throws IOException {
			final com.myster.util.FileProgressWindow progress = new com.myster.util.FileProgressWindow("Connecting..");
			
			progress.setTitle("Downloading " + stub.getName());
			progress.setText("Starting...");
			
			progress.show();
			
			progress.addWindowListener(new java.awt.event.WindowAdapter() {
				public void windowClosing(java.awt.event.WindowEvent e) {
					StandardSuite.DownloadThread.this.flagToEnd();
					
					progress.hide();
				}
			});
			
			try {
				progress.setText("Getting File Statistics...");
				
				if (endFlag) return;
				RobustMML mml = getFileStats(socket, stub);
			
				progress.setText("Trying to use multi-source download...");
				
				final boolean DONT_USE_MULTISOURCE = false;
				if (DONT_USE_MULTISOURCE) throw new IOException ("Toss and catch: Multisource download diabled");
				
				if (endFlag) return;
				
				FileHash hash = MultiSourceUtilities.getHashFromStats(mml);
				
				final File theFile = MultiSourceUtilities.getFileToDownloadTo(stub, progress);
				
				synchronized (StandardSuite.DownloadThread.this) {
					if (endFlag) return;
					
					final MSPartialFile partialFile = MSPartialFile.create(stub.getName(), stub.getType(), MultiSourceDownload.DEFAULT_CHUNK_SIZE,new FileHash[]{hash}, MultiSourceUtilities.getLengthFromStats(mml));
					
					msDownload = new MultiSourceDownload(stub, hash, MultiSourceUtilities.getLengthFromStats(mml), new MSDownloadHandler(progress, theFile), new RandomAccessFile(theFile, "rw"), partialFile);
					msDownload.addListener(new MSDownloadListener() { //warning cut and paste code from MSPArtialFile 
						public void endDownload(MultiSourceEvent event) {
							if (event.getMultiSourceDownload().isCancelled()) {
								partialFile.done();
							}
						}
					
						public void doneDownload(MultiSourceEvent event) {
						
							try {
								if (! MultiSourceUtilities.moveFileToFinalDestination(theFile, progress)) throw new IOException("");
							} catch (IOException ex) {
								com.general.util.AnswerDialog.simpleAlert(progress, "Error: Couldn't move and rename file. "+ex); //yuck
							}
						}
					});
				}
				
				msDownload.run();
			} catch (IOException ex) {
				ex.printStackTrace();
			
				try {
					progress.setText("Trying to use normal download...");
					
					synchronized (StandardSuite.DownloadThread.this) {
						if (endFlag);
					
						secondDownload = new DownloaderThread(MysterSocketFactory.makeStreamConnection(stub.getMysterAddress()), stub, progress);
					}
					secondDownload.run();
				} catch (IOException exp) {
					progress.setText("Could not download file by either method...");
				}
			}
		}
	
		MultiSourceDownload msDownload;
		DownloaderThread secondDownload;
		boolean endFlag;
		
		public synchronized void flagToEnd() {
			endFlag = true;
			
			if (msDownload!=null) msDownload.cancel();
			
			if (secondDownload!=null) secondDownload.end();
		}
		
		public void end() {
			flagToEnd();
			
			//try {
			//	join();
			//} catch (InterruptedException ex) {}
		}
	}
	

	
	public static RobustMML getFileStats(MysterAddress ip, MysterFileStub stub) throws IOException  {
		MysterSocket socket=null;
		try {
			socket=MysterSocketFactory.makeStreamConnection(ip);
			return getFileStats(socket, stub);
		} finally {
			disconnectWithoutException(socket);
		}
	}
	
	public static RobustMML getFileStats(MysterSocket socket, MysterFileStub stub) throws IOException  {
		socket.out.writeInt(77);
		
		checkProtocol(socket.in);
					
		socket.out.writeInt(stub.getType().getAsInt()); //this protocol sucks
		socket.out.writeUTF(stub.getName());

		try {
			return new RobustMML(socket.in.readUTF());
		} catch (MMLException ex) {
			throw new ProtocolException("Server sent a corrupt MML String.");
		}
	}
	
	
	public static String getFileFromHash(MysterAddress ip, MysterType type, FileHash hash) throws IOException  {
		return getFileFromHash(ip, type, new FileHash[]{hash});
	}
	
	public static String getFileFromHash(MysterAddress ip, MysterType type, FileHash[] hashes) throws IOException  {
		MysterSocket socket=null;
		try {
			socket=MysterSocketFactory.makeStreamConnection(ip);
			return getFileFromHash(socket, type, hashes);
		} finally {
			disconnectWithoutException(socket);
		}
	}
	
	public static String getFileFromHash(MysterSocket socket, MysterType type, FileHash hash) throws IOException  {
		return getFileFromHash(socket, type, new FileHash[]{hash});
	}
	
	//Returns "" if file is not found or name of file if file is found.
	public static String getFileFromHash(MysterSocket socket, MysterType type, FileHash[] hashes) throws IOException  {
		socket.out.writeInt(150);
		
		checkProtocol(socket.in);
		
		socket.out.writeInt(type.getAsInt());
		
		for (int i = 0; i < hashes.length; i++) {
			socket.out.writeUTF(hashes[i].getHashName());
			
			socket.out.writeShort(hashes[i].getHashLength());
			
			byte[] byteArray = hashes[i].getBytes();

			socket.out.write(byteArray,0,byteArray.length);
		}
		
		socket.out.writeUTF("");
		
		return socket.in.readUTF();

	}

	public static void checkProtocol(DataInputStream in) throws IOException, UnknownProtocolException { //this should have its own exception type
		int err=in.read();
		
		if (err==-1) throw new IOException("Server disconnected");
		
		if (err!=1) {
			throw new UnknownProtocolException(err,"Protocol is not understood. (none 1 response)");
		}
	}
	
	public static void disconnect(MysterSocket socket) throws IOException {
		//try {
			socket.out.writeInt(2);
			socket.in.read();	
		//} catch (IOException ex) {}
		
		try {socket.close();} catch (Exception ex) {}
	}
	
	public static void disconnectWithoutException(MysterSocket socket) {
		try {
			socket.out.writeInt(2);
			socket.in.read();	
		} catch (Exception ex) {}
		
		try {socket.close();} catch (Exception ex) {}
	}
}