package com.myster.server.stream;
/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

import java.net.*;
import java.io.*;

import com.myster.server.*;
import com.myster.mml.RobustMML;
import com.myster.hash.SimpleFileHash;
import com.myster.hash.FileHash;

import com.myster.type.MysterType;

public class FileByHash extends ServerThread {
	public static final int NUMBER=150;
	
	public static final String HASH_TYPE 	= "/Hash Type";
	public static final String HASH			= "/Hash";
	
	public int getSectionNumber() {
		return NUMBER;
	}
	
	public void section(ConnectionContext context) throws IOException {
		try {
			MysterType type = new MysterType(context.socket.in.readInt()); //type
		
			FileHash md5Hash = null;  
		
			for (;;) { //get hash name, get hash length, get hash data until hashname is ""
				String hashType = context.socket.in.readUTF();
				if (hashType.equals("")) {
					break;
				} 
				int lengthOfHash = 0xffff & (int)(context.socket.in.readShort());
				
				byte[] hashBytes = new byte[lengthOfHash];
				context.socket.in.readFully(hashBytes,0,hashBytes.length);
				
				if (hashType.equals("md5")) {
					md5Hash = SimpleFileHash.buildFileHash(hashType, hashBytes);
				}
			}
			
			
			com.myster.filemanager.FileItem file = null;
			
			if (md5Hash != null) {
				file = com.myster.filemanager.FileTypeListManager.getInstance().getFileFromHash(type, md5Hash);
			}
		
			if (file == null) {
				context.socket.out.writeUTF("");
			} else {
				context.socket.out.writeUTF(file.getName());
			}
		} catch(RuntimeException ex) {
			ex.printStackTrace();
			throw ex;
		}
	}
	
	private static FileHash buildFileHash(RobustMML mml) {
		String hashType = mml.get(HASH_TYPE);
		String hash 	= mml.get(HASH);
		
		if (hashType == null || hash == null) return null;
		
		byte[] hash_bytes;
		try {
			hash_bytes = com.general.util.Util.fromHexString(hash);
		} catch (NumberFormatException ex) {
			return null;
		}
		
		if (hash_bytes == null) return null;
		
		return com.myster.hash.SimpleFileHash.buildFileHash(hashType, hash_bytes);
	}
}