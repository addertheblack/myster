package com.myster.hash;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;

import com.general.util.BlockingQueue;

import com.myster.util.ProgressWindow;
import com.general.util.Util;

/**
*	Facilities for passively determining the hash values of files.
*/

public class HashManager implements Runnable {
	public static final String MD5 = "md5";
	public static final String SHA1 = "sha1";
	
	private static final String[] hashTypes = {MD5, SHA1};
	
	private static HashManager hashManager;
	
	/**
	*	This should be called before any other functions are (ie, on startup)
	*/
	public static void init() {
		hashManager = new HashManager();
		(new Thread(hashManager)).start();
	}
	
	/**
	*	This routine could take a long time or not. If the file is cached the information is
	*	returned to the listener from the current thread.. Else the information is returned from
	*	the internal hashing thread. (Yipes, thread management in java is <-> to memory alloc
	*	free in C++ in pointless complexity)...
	*/
	public static void findHashNoneBlocking(File file,FileHashListener listener) {
		hashManager.findHash(file, listener);
	}
	
	// synchronous version is missing.. If found please fill in.
	
	/**
	*	Returns true if file is already hashed.
	*/
	public static boolean hashIsKnown(File file) {
		return false;
	}
	
	/**
	*	Returns the hash value of a file if it is cached. returns null otherwise.
	*/
	public static FileHash getHashFromCache(File file) {
		return null;
	}
	
	
	
	
	/////////////////////////////////////////end of static sub system\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
	
	private BlockingQueue workQueue;
	private HashCache oldHashes;
	private ProgressWindow progress;
	
	public HashManager() {
		workQueue = new BlockingQueue();
		workQueue.setRejectDuplicates(true);
		
		oldHashes = HashCache.getDefault();
		
		progress = new ProgressWindow("Hashing...");
		progress.setVisible(true);
	}
	
	public void findHash(File file, FileHashListener listener) {
		FileHash[] hashes = (FileHash[])(oldHashes.getHashesForFile(file));
		
		if (hashes == null) {
			if (file.exists()) {
				workQueue.add(new WorkingQueueItem(file, listener));
			} else {
				dispatchHashFoundEvent(listener, new FileHash[]{}, file); // file doens't EXIST!
			}
		} else {
			dispatchHashFoundEvent(listener, hashes, file);
		}
	}
	
	private void dispatchHashFoundEvent(FileHashListener listener, FileHash[] hashes, File file) {
		listener.fireEvent(new FileHashEvent(FileHashEvent.FOUND_HASH, hashes, file));
	}
	
	public void run() {
		MessageDigest[] digestArray = new MessageDigest[hashTypes.length];
		
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		
		for (;;) {
			try {
				for (int i = 0; i < digestArray.length; i++) {
					digestArray[i] = MessageDigest.getInstance(hashTypes[i]);
				}
				
				WorkingQueueItem item = (WorkingQueueItem)(workQueue.get());
				
				calcHash(item.file, digestArray);
				
				FileHash[] hashes = new FileHash[digestArray.length];
				for (int i = 0; i < hashes.length; i++) {
					hashes[i] = new SimpleFileHash(digestArray[i].getAlgorithm(), digestArray[i].digest());
				}
				
				oldHashes.putHashes(item.file, hashes);
				
				dispatchHashFoundEvent(item.listener, hashes, item.file);		
			} catch (NoSuchAlgorithmException ex) {
				ex.printStackTrace();
				// Should never happen
			} catch (InterruptedException ex) {
				ex.printStackTrace();
				// Should never happen.
			}
		}
	}
	
	public int getWorkQueueLength() {
		return workQueue.getSize();
	}
	
	private final void calcHash (File file, MessageDigest[] digests)  {
	
		InputStream in = null;
		
		try {
			in = new FileInputStream(file); //read only
		
		long currentByte = 0;
		
		byte[] buffer = new byte[64*1024]; //64k buffer
		
		progress.setMin(0);
		progress.setMax(file.length());
		progress.setText("Hashing file \""+file.getName()+"\"");
		
		
		//long sTime=System.currentTimeMillis();
		
		for (int bytesRead = in.read(buffer); bytesRead != -1; bytesRead = in.read(buffer)) {
			currentByte += bytesRead;
			
			addBytesToDigests(buffer, bytesRead, digests);
			
			//System.out.println(""+currentByte);
			progress.setValue(currentByte);
			//progress.setAdditionalText(Util.getStringFromBytes(currentByte));
		}
		} catch (IOException ex) {
			System.out.println("Could not read a file.");
		} finally {
			try {in.close();} catch (Exception ex) {} // don't care
		
			progress.setValue(0);
			progress.setText("Waiting for work...");
		}
	}
	
	private final static void addBytesToDigests(byte[] bytes, int endByte, MessageDigest[] digests) {
		for (int i = 0; i < digests.length; i++) {
			digests[i].update(bytes, 0, endByte);
			Thread.currentThread().yield();
		}
	}
	
	private static class WorkingQueueItem {
		public final File file;
		public final FileHashListener listener;
		
		public WorkingQueueItem (File file, FileHashListener listener) {
			this.file = file;
			this.listener = listener;
		}
	}
	
	//private static class 
}