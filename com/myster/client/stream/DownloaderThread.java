/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/


// REQUIRES AN ITERATION:

// RED LIST:
// Assumes that closing the file is not nessesairy <-
// Is too closely coupled with ProgressWindow (should send events)

package com.myster.client.stream;

import java.net.*;
import java.io.*;
import java.awt.*;
import com.general.util.*;
import com.myster.filemanager.*;
import com.myster.net.MysterSocket;
import com.myster.net.MysterAddress;
import com.myster.search.MysterFileStub;
import com.myster.util.ProgressWindow;
import com.myster.util.FileProgressWindow;
import com.myster.util.ProgressWindowClose;
import com.myster.net.MysterSocketFactory;
import java.util.Locale;


/**
Downloads a files form a server. Needs a MysterFileStub (File location + type info)
to locate the file.

*/


public class DownloaderThread extends SafeThread {
	MysterFileStub file;
	long bytessent=0;
	DataInputStream in;
	DataOutputStream out;
	FileProgressWindow progress;
	RandomAccessFile o=null; //implements DataOutput Interface
	MysterSocket socket;
	
	
	File finalFile;
	File fileToWriteTo;
	
	
	final int BUFFERSIZE=2024;	//I like turnips
	
	//String downloadpath;
	long amountToSkip=0;
	
	public DownloaderThread(MysterFileStub file) {
		this.file=file;
	}
	
	
	public void run() {
		progress=new FileProgressWindow();
		progress.setMax(100);
		progress.setMin(0);
		progress.setProgressBarNumber(2);
		progress.setValue(-1,FileProgressWindow.BAR_1);
		progress.setValue(-1,FileProgressWindow.BAR_2);
		progress.setVisible(true);
		progress.setBarColor(Color.magenta, 1);
		progress.addWindowListener(new ProgressWindowClose(this));
		progress.setTitle("Preparing to Download..");
		
		String theFileName=file.getName();
		if (theFileName.lastIndexOf(""+File.pathSeparator)!=-1) {
			if (theFileName.lastIndexOf(""+File.pathSeparator)+1==theFileName.length()) theFileName="";
			else theFileName=theFileName.substring(theFileName.lastIndexOf(""+File.pathSeparator)+1);
		}
		
		if (theFileName.lastIndexOf("/")!=-1) {
			if (theFileName.lastIndexOf("/")+1==theFileName.length()) theFileName="";
			else theFileName=theFileName.substring(theFileName.lastIndexOf("/")+1);
		}
		
		String temp=file.getType();
		if (temp==null) {
			progress.setText("No type selected Error...",FileProgressWindow.BAR_1);
			return;
		}
		String dp=FileTypeListManager.getInstance().getPathFromType(temp);
		File doubleDumbAssOnYou;
		if (dp!=null) doubleDumbAssOnYou=new File(dp);
		else doubleDumbAssOnYou=new File("I love roman peaches yeah");
		if (dp==null||!doubleDumbAssOnYou.isDirectory()) {
			progress.setText("Can't download file: the download directory for this type is not set?",FileProgressWindow.BAR_1);
			
			FileDialog dialog=new FileDialog(progress, "Where do you want to save this file? (NOTE: You can avoid seeing this dialog"+
					" by setting a download directory for this type in the download preferences.)", FileDialog.SAVE);
			dialog.setSize(400, 300);
	
			dialog.setFile(theFileName);
			dialog.show();
			String temppath=dialog.getDirectory();

			System.out.println(temppath);
			theFileName=dialog.getFile();
						if (theFileName==null) {
				progress.setText("User Canceled",FileProgressWindow.BAR_1);
				return;
			}
			dp=temppath;
			
		} else {
			File tempFile=new File(dp+theFileName);
			if (tempFile.exists()) {
				String it=(new AnswerDialog(progress, "The file "+theFileName+
						" already exists. Would you like to over-write the file or rename the file you're downloading?\n\nWarning: Over-writting will delete the current file contents.",
						new String[]{"Cancel", "Rename", "Over-Write"})).answer();
				if (it.equals("Cancel")) {
					progress.setText("User Canceled",FileProgressWindow.BAR_1);
					return;
				} else if (it.equals("Rename")) {
					String[] blah=askUser(progress, dp, theFileName);
					if (blah==null) return;
					dp=blah[0];
					theFileName=blah[1];
				} else if (it.equals("Over-Write")) {
					//nothing
				}
			}
		}
		

		
		if (!validateFile(dp,theFileName)) return; //handles global data

					//String[] blah=askUser(dp, theFileName);
					//dp=blah[0];
					//theFileName=blah[1];
					//validate

		do {
			try {
				o=new RandomAccessFile (fileToWriteTo, "rw");
				break;
			} catch (IOException ex) {
				String[] blah=askUser(progress, dp,theFileName);
				if (blah==null) return;
				dp=blah[0];
				theFileName=blah[1];
				if (!validateFile(dp,theFileName)) return; //handles global data
			}
		} while(true);
		
		long filesize;
		
		try {
			progress.setText("Connecting to server...", FileProgressWindow.BAR_1);
			socket=MysterSocketFactory.makeStreamConnection(new MysterAddress(file.getIP()));
			socket.setSoTimeout(3*60*1000);
		} catch (Exception ex) {
			progress.setText("Server at that IP/Domain name is not responding.", FileProgressWindow.BAR_1);
			return;
		}
	
		try {
			in=new DataInputStream(socket.getInputStream());
			out=new DataOutputStream(socket.getOutputStream());
		} catch (Exception ex) {
			progress.setText("Error oppening connection", FileProgressWindow.BAR_1);
			return;
		}
		CONNECTION: {
			progress.setText("Negotiating file transfer..",FileProgressWindow.BAR_1);
			
			
			
			try {
				out.writeInt(80);
				int ii=in.read();
				if (ii!=1) {
					System.out.println("Server says:"+ii);
					progress.setText("Server says it does not know how to send files (server is insane)",FileProgressWindow.BAR_1);
					break CONNECTION;
				}
				out.write(temp.getBytes());
				out.writeUTF(file.getName());
				//out.writeLong(0);		//For initial offset... Currently set to 0!

				
				
			} catch (Exception ex) {
				progress.setText("Error negotiating file transfer. Remote host is not setup right or under heavy load?",FileProgressWindow.BAR_1);
				ex.printStackTrace();
				break CONNECTION;
			}
			
			
			
			try {
				//File fileToWriteTo=//getBestFileName(file.getName());
				if (fileToWriteTo==null) {
					progress.setText("Canceling...");
					progress.hide();
					progress.dispose();
					break CONNECTION;
				}
				
				if (fileToWriteTo.length()!=0&&fileToWriteTo.length()>512) {
					amountToSkip=fileToWriteTo.length()-256;
					o.seek(amountToSkip);
					//bytessent=amountToSkip;
					out.writeLong(amountToSkip);		//For initial offset... Currently set to 0!
				} else {
					out.writeLong(0);		//For initial offset... Currently set to 0!
				}
				
				if (in.readInt()==0) {
					progress.setText("File Does not exist on remote server!",FileProgressWindow.BAR_1);
					
					break CONNECTION;
				}
				
				filesize=in.readLong();
			} catch (Exception ex) {ex.printStackTrace();
				progress.setText("The d/l dir for this type is invalid",FileProgressWindow.BAR_1);
				ex.printStackTrace();
				break CONNECTION;
			}
			
			progress.startBlock(FileProgressWindow.BAR_1,0,filesize+amountToSkip);
			progress.setValue(-1);
			progress.setPreviouslyDownloaded(amountToSkip, FileProgressWindow.BAR_1); //here because of the queue stuff
			progress.setText("Transfering: "+file.getName(),FileProgressWindow.BAR_1);
			progress.setTitle("Transfering: ("+file.getIP()+")"+file.getName());
			
			long length;
			char code;
			
			try {
				while (bytessent!=filesize) {
					if (in.readInt()!=6669) {
						progress.setText("Error.I didn't receive my sync Int; this is quite impossible.",FileProgressWindow.BAR_1);
						System.out.println("AGGHGHGHGHGHGHGHGGGGHGHGHGHGH!!!");
						break CONNECTION;
					}

					code=(char)in.readByte();
					length=in.readLong();
					switch (code) {
						case 'd':
							if (bytessent==0) progress.startBlock(FileProgressWindow.BAR_1,0,filesize+amountToSkip); //fixes queuing bug for the d/l rate
							//System.out.println("Getting data packet of size "+length);
							progress.setText("Getting data packet of size "+length+". I have gotten "+bytessent+" bytes so far",FileProgressWindow.BAR_1);
							receiveDataPacket(progress, o, length);
							break;
						case 'i':
							//progress.say("Getting Image Packet",FileProgressWindow.BAR_1);
							//System.out.println("Getting Image");
							receiveImage(progress, length);
							break;
						case 'q':
							progress.setText("Getting queue position", FileProgressWindow.BAR_1);
							if (length==4) {
								progress.setText("You are #" + in.readInt() + " in queue.");
							} else if (length==8) {
								progress.setText("You are #" + (int)(in.readLong()) + " in queue.");
							} else {
								byte[] b=new byte[(int)length];
								in.readFully(b);
							}
							break;
						default:
							progress.setText("Receiving unknown data of type: "+code,FileProgressWindow.BAR_1);
							byte[] b=new byte[(int)length];
							in.readFully(b);
							break;
					}
				}
			} catch (Exception ex) {
				progress.setText("ERROR! in file transfer. Did the remote server go off-line?",FileProgressWindow.BAR_1);
				//progress.done();
				ex.printStackTrace();
				break CONNECTION;
			}
			//progress.done();
			progress.setText("Done.");

			try {
				o.close();
			} catch (Exception ex) {}

			if (finalFile.exists()) finalFile.delete();
			while (!fileToWriteTo.renameTo(finalFile)) {
				String[] pathinfo=askUser(progress, finalFile.getPath(), finalFile.getName(), "There was an error renaming this file. Please enter a new name "+"that is lss than 31 characters. WARNING: pressing cancel now might make the file disapear.");
				if (pathinfo==null) {
					(new AnswerDialog(progress,"Error renaming intermediate .i file.\n" + 
				    		fileToWriteTo + " -> " + finalFile)).answer();
					break;
				}
				finalFile=new File(pathinfo[0], pathinfo[1]);
			}
		}
		try {
			o.close();
		} catch (Exception ex) {}
		try {
			out.close();
		} catch (Exception ex) {}
		try {
			in.close();
		} catch (Exception ex) {}
		
		try {
			socket.close();
		} catch (Exception ex) {}
	}
	
	private static String[] askUser(ProgressWindow progress, String dp, String theFileName, String a) {
		String ask=(a==null?"What do you want to call this file?":a);
	
		String [] s=new String[2];
		FileDialog dialog=new FileDialog(progress, ask, FileDialog.SAVE);
		dialog.setSize(400, 300);

		dialog.setDirectory(dp);
		dialog.setFile(theFileName);
		dialog.show();
		String temppath=dialog.getDirectory();
		theFileName=dialog.getFile();
		if (theFileName==null) {
			progress.setText("User Canceled",FileProgressWindow.BAR_1);
			return null;
		}
		dp=temppath;
		s[0]=dp;
		s[1]=theFileName;
		
		return s;
	}
	
	private static String[] askUser(ProgressWindow p, String dp, String theFileName) {
		return askUser(p, dp, theFileName, null);
	}

	private boolean validateFile(String dp, String theFileName) {
		finalFile=new File(dp+theFileName);
	
		fileToWriteTo=new File(dp+theFileName+".i");
		
		if (fileToWriteTo.exists() && (fileToWriteTo.length()!=0)) {
			
			
			String it=(new AnswerDialog(progress, "A file by this name already exists. Would you like to restart this download or continue?" , new String[]{"restart", "continue", "cancel"})).answer();
			
			if (it.equals("restart")) {
				if (!(fileToWriteTo.delete())) {
					(new AnswerDialog(progress, "Could not restart the download.\n\n"+theFileName+".i could not be deleted")).answer();
					progress.setText("Canceled",FileProgressWindow.BAR_1);
					return false;
				}
			} else if (it.equals("continue")) {
				//nothing
			} else {
				progress.setText("User Canceled",FileProgressWindow.BAR_1);
				return false;
			}
		}
		
		return true;
	}
	
	
	//////////////////Data Packet Stuff start
	private int receiveDataPacket(ProgressWindow progress, DataOutput out, long size) {
		byte[] buffer=new byte[BUFFERSIZE];
		
		progress.startBlock(FileProgressWindow.BAR_2,0,(int)size);
		progress.setText("Transfering a Block of the File.", FileProgressWindow.BAR_2);
		for (int i=0; i<(size/BUFFERSIZE); i++) {
			if (readWrite(progress, out, BUFFERSIZE, buffer)==-1) return -1;
			
			progress.setValue(i*BUFFERSIZE, FileProgressWindow.BAR_2);
			
		}
		if (readWrite(progress, out, (int)(size%BUFFERSIZE), buffer)==-1) return -1;
		
		progress.setValue(size, FileProgressWindow.BAR_2);

		return (int)size;
	}
	
	private int readWrite(ProgressWindow progress, DataOutput out, int size, byte[] buffer) {
		if (size==0) return 0;
		try {
			in.readFully(buffer,0, (int)size);
			if ((int)size!=BUFFERSIZE) progress.setText("Finishing up the transfer..");
			out.write(buffer,0, (int)size);
		} catch (Exception ex) {progress.setText("Transmission error!"); System.out.println("Transmission error"); return -1;}
		
		bytessent+=size;
		progress.setText("Transfered: "+Util.getStringFromBytes(bytessent), FileProgressWindow.BAR_1);
		progress.setValue(bytessent+amountToSkip, FileProgressWindow.BAR_1);
		return size;
	}
	//////////////////Data Packet Stuff end
	
	private void receiveImage(ProgressWindow progress, long size) {
		try {
			progress.setText("Getting Image....",  FileProgressWindow.BAR_2);
			progress.makeImage(getDataBlock(progress, size));
		} catch (Exception ex) {
			System.out.println(""+ex);
			ex.printStackTrace();
			return;
		}
	}
	
	/**
	*	Can get a data block for anything, but is used only for the Image.
	*
	*/	
	private byte[] getDataBlock(ProgressWindow progress, long size) throws Exception{
		byte[] buffer=new byte[(int)size];
		int amounttoread;
		

		progress.startBlock(FileProgressWindow.BAR_2, 0, (int)size);
		
		
		try { 
			amounttoread=0;
			for (int i=0; i<size; i+=amounttoread ){
				progress.setValue(i, FileProgressWindow.BAR_2);
				
				//Calculates amount to read
				if ((size-i)<BUFFERSIZE) {
					amounttoread=(int)(size-i);
				} else {
					amounttoread=BUFFERSIZE;
				}
				
				//Reads the data
				try {
					in.readFully(buffer, i,  amounttoread);
				} catch (Exception ex) {
					System.out.println("Error READING block data. Tranfer interrupted.");
					progress.setText("Error READING block data. Tranfer interrupted.");
					throw ex;
				}
			}
		 } catch (Exception ex) {
			System.out.println("Error getting block data. Tranfer interrupted.");
			progress.setText("Error getting block data. Tranfer interrupted.");
			throw ex;
		}
		return buffer;
	}
	/*
	private File getBestFileName(String s) {
		File file=new File(downloadpath+s);
		if (file.exists()) {
			
			String[] temp={"restart", "continue", "cancel"};
			String it=(new AnswerDialog(progress, "A file by this name already exists. Would you like to restart this download or continue?" , temp)).answer();
			
			if (it.equals("restart")) {
				file.delete();
				return file;
			} else if (it.equals("continue")) {
				return file;
			} else {
				return null;
			}
		}
		//return file
		return getBestFileName(file);//new File(downloadpath+s));
	}*/
	/*
	private	File getBestFileName(File f) {
		if (!(f.exists())) return f;
		
		String front=getFront(f.getName());
		String back	=getBack(f.getName());
		
		for (int i=1; i<1000; i++) {
			f=new File(downloadpath+front+i+back);
			if (!(f.exists())) return f;
		}
		System.out.println("there are 1000 files with similar names");
		return null;
	}
	
	private String getFront(String s) {
		if (s.lastIndexOf(".")==s.length()-4){
			return s.substring(0,(s.lastIndexOf(".")));
		}
		return s;
	}
	
	private String getBack(String s) {
		if (s.lastIndexOf(".")==s.length()-4){
			return s.substring(s.lastIndexOf("."));
		}
		return "";
	}
	*/
	
	public void stopping() { 
		progress.setText("Download Cancled by user!",FileProgressWindow.BAR_1);
		try {in.close();} catch (Exception ex) {}
		try {out.close();} catch (Exception ex) {}
		try {socket.close();} catch (Exception ex) {}
		try {o.close();} catch (Exception ex) {}
	}
	
	public void end() {
		stopping();
		super.end();
	}
	
}
