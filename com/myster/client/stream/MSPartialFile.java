package com.myster.client.stream;

/**
*	This class is here to encapsulate all the information related to a Myster multi
*	source download resumable download block file.
*	
*/

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.File;
import java.io.FilenameFilter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import com.myster.mml.RobustMML;
import com.myster.mml.MMLException;
import com.myster.hash.FileHash;
import com.myster.hash.SimpleFileHash;

public class MSPartialFile {
	public static void main(String args[]) { //broken test case
		/*
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
		*/
	}
	
	private static final String DIR = "Incomming" + File.separator;
	
	
	
	//////////// STATIC SUB SYSTEM \\\\\\\\\\\\\\\\
	public static MSPartialFile recreate(File file) throws IOException {
		if (! file.exists()) throw new IOException("File does not exist");
		
		RandomAccessFile maskFile = new RandomAccessFile(file,"rw");
		
		RobustMML mml;
		try {
			mml = new RobustMML(maskFile.readUTF());
		} catch (MMLException ex) {
			throw new IOException("MML Meta data was badly formed. This file is corrupt.");
		}
		
		return new MSPartialFile(maskFile, new PartialFileHeader(mml));
	}
	
	public static MSPartialFile create(String filename, int blockSize, FileHash[] hashes) throws IOException {
		File fileReference = new File(DIR+filename);
		
		RandomAccessFile maskFile 	= new RandomAccessFile(fileReference,"rw");
		PartialFileHeader header 	= new PartialFileHeader(filename, blockSize, hashes);
		
		maskFile.write(header.toBytes());
	
		return new MSPartialFile(maskFile, header);
	}
	
	public static MSPartialFile[] list() throws IOException {
		File dir = new File(DIR);
		
		String[] file_list = dir.list(new FilenameFilter() { //I love this idea. way to go java guys. pitty there's no half decent way to make it generic (yet?)
			public boolean accept(File dir, String name) {
				if (! name.endsWith(".p")) return false;
			
				File file = new File(dir, name);
				
				if (file.isDirectory()) return false;
				
				return true;
			}
		});
	
		MSPartialFile[] msPartialFiles = new MSPartialFile[file_list.length];
		
		for (int i = 0; i < file_list.length; i++) {
			msPartialFiles[i] = recreate(new File(dir, file_list[i]));
		}
		
		return msPartialFiles;
	}
	
	
	
	
	
	
	
	
	
	
	
	///////////////// OBJECT SYSTEM \\\\\\\\\\\\\\
	
	//private static final String filePath;
	long offset = 0;

	File fileReference;
	RandomAccessFile maskFile;
	PartialFileHeader header;
	

	
	private MSPartialFile(RandomAccessFile maskFile, PartialFileHeader header) {
		this.maskFile 	= maskFile;
		this.header 	= header;
		
		this.offset 	= header.getOffset();
	}
	
	public RobustMML getCopyOfMetaData() {
		return new RobustMML(header.toMML());
	}
	
	public long getBlockSize() {
		return header.getBlockSize();
	}
	
	public FileHash[] getFileHashes() {
		return header.getFileHashes();
	}
	
	public String getFilename() {
		return header.getFilename();
	}
	
	public long getFirstUndownloadedBlock() throws IOException {
		maskFile.seek(offset);
		
		final int blockSize = 64*1024;
		
		final byte[] buffer = new byte[blockSize];
		
		int numberOfBlocks = (int)(maskFile.length() / buffer.length); //DANGER! UNSAFE CAST TO INT! THIS CODE CAN FAIL FOR LARGE FILE SIZES! (very, very large but whatever)
		
		for (long blockCounter = 0; blockCounter < numberOfBlocks + 1; blockCounter ++) {
			int currentBlockSize = (int)(blockCounter >= numberOfBlocks ? maskFile.length() % buffer.length : buffer.length); //UNSAFE CAST
			
			if (currentBlockSize == 0) break;
			
			maskFile.readFully(buffer, 0, currentBlockSize);
		
			for (int i = 0 ; i < currentBlockSize; i++) {
				if (buffer[i]!=0xFF) return 8*(i + (blockCounter * blockSize));
			}
		}
		
		return maskFile.length() - offset;
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
				
				assertNotNull(filename); //throws IOException on null
				assertNotNull(string_blockSize);
			
				blockSize = Integer.parseInt(string_blockSize);
				hashes = getHashesFromHeader(mml,HASHES_PATH);
			} catch (NumberFormatException ex) {
				throw new IOException(""+ex);
			}
		}
		
		//private FileHash getHashesfromHeader(RobustMML mml) throws IOException {
		//	Stringp[] listOfEntries = mml.list(HASHES_PATH);
			
		//	assertNotNull(listOfEntries);
		//}
		
		private void assertNotNull(Object o) throws IOException { if (o == null) throw new IOException ("Unexpect null object"); }
		
		PartialFileHeader(String filename, long blockSize, FileHash[] hashes) {
			this.filename = filename;
			this.blockSize = blockSize;
			this.hashes = hashes;
		}
		
		public long getBlockSize() {
			return blockSize;
		}
		
		public String getFilename() {
			return filename;
		}
		
		public FileHash[] getFileHashes() {
			FileHash[] temp_hashes = new FileHash[hashes.length];
			
			for (int i = 0; i < temp_hashes.length; i++) {
				temp_hashes[i] = hashes[i];
			}
			
			return temp_hashes;
		}
		
		final String FILENAME_PATH = "/Filename";
		final String BLOCK_SIZE_PATH = "/Block Size Path";
		final String HASHES_PATH = "/Hashes/";
		
		public com.myster.mml.MML toMML() {
			RobustMML mml = new RobustMML();
			
			mml.put(FILENAME_PATH, filename);
			mml.put(BLOCK_SIZE_PATH, ""+blockSize);
			
			addHashesToHeader(hashes, mml, HASHES_PATH);
			
			return mml;
		}
		
		public byte[] toBytes() {
			ByteArrayOutputStream b_out = new ByteArrayOutputStream();
			
			DataOutputStream out = new DataOutputStream(b_out);
			
			try {
				out.writeUTF(toMML().toString());
			} catch (IOException ex) {
				throw new com.general.util.UnexpectedError("This line should not throw and error.");
			}
			
			return b_out.toByteArray(); // lots of "to" methods here.
		}
		
		public int getOffset() {
			return toBytes().length;
		}
	}
	
	
	
	//Hash encoding/decoding
	static final String HASH_NAME_PATH 	= "Hash Name";
	static final String HASH_AS_STRING	= "Hash Value";
	
	private static void addHashesToHeader(FileHash[] hashes, RobustMML mml, String path) {
		for (int i = 0 ; i < hashes.length; i++) {
			FileHash hash = hashes[i];
		
			String hashName = hash.getHashName();
			String hashAsString = SimpleFileHash.asHex(hash.getBytes());
			
			String workingPath = path+i+"/";
			
			mml.put(workingPath+HASH_NAME_PATH, hashName);
			mml.put(workingPath+HASH_AS_STRING, hashAsString);
		}
	}
	
	private static FileHash[] getHashesFromHeader(RobustMML mml, String path) throws IOException {
		java.util.Vector itemsToDecode = mml.list(path);
		if (itemsToDecode == null) throwIOException("itemsToDecode is null");
		
		FileHash[] hashes = new FileHash[itemsToDecode.size()];
		
		for (int i = 0; i < itemsToDecode.size(); i++) {
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