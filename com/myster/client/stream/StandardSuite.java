

package com.myster.client.stream;

import java.util.Vector;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.net.MysterAddress;
import com.myster.mml.RobustMML;
import com.myster.mml.MMLException;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.hash.FileHash;


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
	public static void downloadFile(MysterAddress ip, MysterFileStub stub) throws IOException {
		MysterSocket socket=null;
		try {
			socket=MysterSocketFactory.makeStreamConnection(ip);
			downloadFile(socket, stub);
		} finally {
			//disconnectWithoutException(socket);
		}
	}
	
	
	// should not be public
	private static void downloadFile(final MysterSocket socket, final MysterFileStub stub ) {
		(new Thread() { //routine is completely asynchronous
			public void run() {
				com.myster.util.FileProgressWindow progress = new com.myster.util.FileProgressWindow("Connecting..");
					
				progress.show();
				
				progress.addWindowListener(new WindowAdapter() {
					public void windowClosing(WindowEvent e) {
						progress.setVisible(false);
					}
				});
				
				try {

					MultiSourceDownload download = new MultiSourceDownload(socket, stub, progress);
					
					if (!download.start()) { //start leave MysterSocket valid
						DownloaderThread secondDownload = new DownloaderThread(socket, stub, progress);
						secondDownload.start();
					}
				} catch (IOException ex) {
					//nothing
					progress.setText("An error has occured ->" + ex.getMessage());
				} finally {
					try { socket.close(); } catch (Exception ex) {}
				}
			}
		}).start();
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
					
		socket.out.writeBytes(stub.getType().toString()); //this protocol sucks
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

	public static void checkProtocol(DataInputStream in) throws IOException {
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