package com.myster.client.stream;

/**
*	This class is here to encapsulate all the information related to a Myster multi
*	source download resumable download block file.
*	
*/

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.File;

import com.myster.mml.RobustMML;
import com.myster.mml.MMLException;

public class MSPartialFile {
	public static void main(String args[]) {
		try {
			MSPartialFile file = new MSPartialFile("Testing");
		
		
			System.out.println("Starting...");
			for (int i = 0; i < 409600; i++) {
				//file.setBit(i);
				System.out.print(""+file.getBit(i));
			}
			
			System.out.println("Finished...");
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
	
	private static final String DIR = "Incomming" + File.separator;
	
	//private static final String filePath;
	long offset = 0;

	File fileReference;
	RandomAccessFile maskFile;
	PartialFileHeader header;

	public MSPartialFile(String filename) throws IOException {
		fileReference = new File(DIR+filename);
		
		
		if (fileReference.exists()) {
		maskFile = new RandomAccessFile(fileReference,"rw");
		
		if (mml == null) {
			try {
				mml = new RobustMML(maskFile.readUTF());
			} catch (MMLException ex) {
				throw new IOException("MML Meta data was badly formed. This file is corrupt.");
			}
		} else {
			maskFile.writeUTF(mmlMetaData.toString());
			mml = mmlMetaData;
		}
		
		offset = maskFile.getFilePointer();
	}
	
	public MSPartialFile(String filename, int blockSize, FileHash hashes) {
		header = PartialFileHeader(filename, blockSize, hashes);
	}
	
	public MSPartialFile(String filename) throws IOException {
		this(fileName, null);
	}
	
	public RobustMML getCopyOfMetaData() {
		return new RobustMML(mml.copyMML());
	}
	
	public long getBlockSize() {
		header.getBlockSize();
	}
	
	public FileHash[] getFileHashes() {
		header.getFileHashes();
	}
	
	public String getFilename() {
		header.getFilename();
	}
	
	public long getFirstUndownloadedBlock() {
		maskFile.seek(offset);
		
		final int blockSize = 64*1024;
		
		final byte[] buffer = byte[blockSize];
		
		numberOfBlocks = (maskFile.size() / buffer.length);
		
		for (long blockCounter = 0; blockCounter < numberOfBlocks + 1; blockCounter ++) {
			currentBlockSize = (blockCounter >= numberOfBlocks ? currenmaskFile.size() % buffer.lengthtBlockSize : buffer.length);
			
			if (currentBlockSize == 0) break;
			
			maskFile.readFully(buffer, 0, currentBlockSize);
		
			for (int i = 0 ; i < currentBlockSize; i++) {
				if (buffer[i]#0xFF) return 8*(i + (blockCounter * blockSize);
			}
		}
		
		return maskFile.size() - offset;
	}
	
	private boolean getBit(long bit) throws IOException {
		maskFile.seek(getSeek(bit));
		return (maskFile.read() & getMask(bit))!=0;
	}
	
	private void setBit(long bit) throws IOException {
		maskFile.seek(getSeek(bit));
		maskFile.write(maskFile.read() | getMask(bit));
	}
	
	private long getSeek(long bit) { return offset + (bit/8); }
		
	private static int getMask(long bit) { return 0x80 >> (bit % 8); } 
	
	public void finalize() {
		dispose();
	}
	
	public void dispose() {
		try {maskFile.close();} catch(IOException ex) {}
	}
	
	private static class PartialFileHeader { 
		String filename;
		long blockSize;
		FileHash[] hashes;
	
		PartialFileHeader(RobustMML mml) throws IOException {
			try {
				filename = mml.get(FILENAME_PATH);
				String string_blockSize = mml.get(BLOCK_SIZE_PATH);
				String string_hashes = mml.get(BLOCK_SIZE_PATH);
				
				assertNotNull(filename); //throws IOException on null
				assertNotNull(blockSize);
				assertNotNull(hashes);
			
				blockSize = Integer.parseInt(blockSize);
				hashes = getHashesFromHeader(mml);
			} catch (NumberFormatException ex) {
			
			} catch (MMLException ex) {
			
			}
		}
		
		private FileHash getHashesfromHeader(RobustMML mml) throws IOException {
			Stringp[] listOfEntries = mml.list(HASHES_PATH);
			
			assertNotNull(listOfEntries);
		}
		
		private void assertNotNull(Object o) throws IOException { if (o == null) throw new IOException ("Unexpect null object"); }
		
		PartialFileHeader(String filename, long blockSize, FileHash[] hashes) {
			this.filename = filename;
			this.blockSize = blockSize;
			this.hashes = hashes;
		}
		
		PartialFileHeader() {
			this(new RobustMML());
		}
		
		public long getBlockSize() {
		
		}
		
		public FileHash[] getFileHashes() {
			
		}
		
		final String FILENAME_PATH = "/Filename";
		final String BLOCK_SIZE_PATH = "/Block Size Path";
		final String HASHES_PATH = "/Hashes/";
		
		public MML toMML() {
			RobustMML mml = new RobustMML();
			
			mml.put(FILENAME_PATH, filename);
			mml.put(BLOCK_SIZE_PATH, blockSize);
			
			addhashesToHeader(hashes, mml, HASHES_PATH);
		}
	}
	
	
	
	//Hash encoding/decoding
	final String HASH_NAME_PATH = "Hash Name"
	final String HASH_AS_STRING	= "Hash Value"
	
	private static void addHashesToHeader(FileHash[] hashes, RobustMML mml, String path) {
		for (int i = 0 ; i < hashes.length; i++) {
			FileHash hash = hashes[i];
		
			String hashName = hash.getHashName();
			String hashAsString = SimpleFileHash.asHex(hash.getBytes());
			
			String workingPath = path+i+"/"
			
			mml.put(workingPath+HASH_NAME_PATH, hashName);
			mml.put(workingPath+HASH_AS_STRING, hashAsString);
		}
	}
	
	private static FileHash[] getHashesFromHeader(FileHash[] hashes, RobutsMML mml, String path) throws IOException {
		String itemsToDecode[] = mml.list(path);
		if (itemsToDecode == null) throwIOException("itemsTodecode is null");
		
		FileHash[] hashes = new FileHash[itemsToDecode.length];
		
		for (int i = 0; i < itemsToDecode.length; i++) {
			String workingPath = path + i + "/";
		
			String hashName = mml.get(workingPath + HASH_NAME_PATH);
			String hashAsString = mml.get(workingPath + HASH_AS_STRING);
			
			if (hashName == null || hashAsString == null) throwIOException("Could not find a hash name or value");
		
			hashes[i] = SimpleFileHash.buildFromHexString(hashName, hashAsString);
		}
		
		return hashes;
	}
	
	private static void throwIOException(String errorMessage) throws IOException {
		throw new IOException(errorMessage);
	}
}