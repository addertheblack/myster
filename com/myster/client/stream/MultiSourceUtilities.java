package com.myster.client.stream;

import java.io.IOException;
import java.io.File;
import java.awt.Frame;

import com.general.util.AnswerDialog;
import com.myster.mml.RobustMML;
import com.myster.search.MysterFileStub;
import com.myster.hash.FileHash;
import com.myster.net.MysterSocket;

public class MultiSourceUtilities {

	
	private static final String EXTENSION = ".i";
	public static File getFileToDownloadTo(MysterFileStub stub, Frame parentFrame) throws IOException {
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
			
			String answer = (new AnswerDialog(	parentFrame,
												"A file by the name of "+file.getName()+" already exists. What do you want to do.",
												new String[]{CANCEL_BUTTON, WRITE_OVER})
											  ).answer();
			if (answer.equals(CANCEL_BUTTON)) {
				throw new IOException("User Canceled.");
			} else if (answer.equals(WRITE_OVER)) {
				if (!file.delete()) {
					AnswerDialog.simpleAlert(parentFrame, "Could not delete the file.");
					throw new IOException("Could not delete file");
				}
			} else if (answer.equals(RENAME)) {
				file = askUserForANewFile(file.getName());
				
				if (file == null) throw new IOException ("User Cancelled");
			}
		}

		return file;
	}
	
	private static File askUserForANewFile(String name) throws IOException {
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
	
	public static FileHash getHashFromStats(RobustMML mml) throws IOException {
		String hashString = mml.get("/hash/" +  com.myster.hash.HashManager.MD5);
		
		if (hashString == null) throw new IOException("Stats MML does not contain the wanted info.");
		
		try {
			return  com.myster.hash.SimpleFileHash.buildFromHexString(com.myster.hash.HashManager.MD5, hashString);
		} catch (NumberFormatException ex) {
			throw new IOException("Stats MML is corrupt.");
		}
	}
	
	public static long getLengthFromStats(RobustMML mml) throws IOException {
		String fileLengthString = mml.get("/size");
		
		if (fileLengthString == null) throw new IOException("Stats MML does not contain the wanted info.");
		
		try {
			return Long.parseLong(fileLengthString);
		} catch (NumberFormatException ex) {
			throw new IOException("Stats MML is corrupt.");
		}
	}
	
	
	/*	
	private boolean assertLengthAndHash(MysterSocket socket) throws IOException {
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
	}*/
	
	public static void debug(String string) {
		System.out.println(string);
	}
}


	/*
	public synchronized boolean start() throws IOException {
		downloaders = new InternalSegmentDownloader[5];
	
		if (!assertLengthAndHash()) return false; //gets file length and hash values from inital remote server.
	
		progress.setTitle("MS Download: "+stub.getName());
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
				//end();
				progress.setVisible(false);
			}
		});
		
		//progress.setText("Downloading file "+stub.getName());
		
		//for (int i=0; i < downloaders.length; i++) {
		//	progress.setBarColor(new Color(0,(downloaders.length-i)*(255/downloaders.length),150), i+1);
		//}
		
		
		startCrawler();
		
		newDownload(stub, socket);
		
		return true;
	}
	*/
	
	/*
	
	
		private void startCrawler() {
		IPQueue ipQueue = new IPQueue();
		
		String[] startingIps = com.myster.tracker.IPListManagerSingleton.getIPListManager().getOnRamps();
		
		ipQueue.addIP(stub.getMysterAddress());
		ipQueue.getNextIP(); //this is so we don't start downloading from the first one again.
		
		for (int i = 0; i < startingIps.length; i++) {
			try { ipQueue.addIP(new MysterAddress(startingIps[i])); } catch (IOException ex) {ex.printStackTrace();}
		}
	
		crawler = new CrawlerThread(new MultiSourceHashSearch(stub.getType(), hash, new MSHashSearchListener()),
									stub.getType(),
									ipQueue,
									new Sayable() {
										public void say(String string) {
											System.out.println(string);
										}},
									null);
									
		crawler.start();
	}
	
	*/