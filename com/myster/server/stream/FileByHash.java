package com.myster.server.stream;
/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

import java.util.Vector;
import java.util.StringTokenizer;
import java.net.*;
import java.io.*;

import com.myster.server.*;
import com.myster.mml.RobustMML;
import com.myster.mml.MMLException;
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
	
		String hashType = context.socket.in.readUTF();
		int lengthOfHash = 0xffff & (int)(context.socket.in.readShort());
		
		byte[] hashBytes = new byte[lengthOfHash];
		
		context.socket.in.readFully(hashBytes,0,hashBytes.length);
		
		com.myster.filemanager.FileItem file = com.myster.filemanager.FileTypeListManager.getInstance().getFileFromHash(type, SimpleFileHash.buildFileHash(hashType, hashBytes));
	
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
		
		byte[] hash_bytes = getBytes(hash);
		
		if (hash_bytes == null) return null;
		
		return com.myster.hash.SimpleFileHash.buildFileHash(hashType, hash_bytes);
	}
	
	private static byte[] getBytes(String hash) {
		Vector bytesVector = new Vector(20,20);
		
		if (hash.length() %2 != 0) return null;
		
		for (int i = 0; i < hash.length(); i+=2) {
			String pair = hash.substring(i,i+2);
			
			try {
				bytesVector.addElement(Byte.valueOf(pair, 16));
			} catch (NumberFormatException ex) {
				return null;
			}
		}
		
		byte[] bytes = new byte[bytesVector.size()];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = ((Byte)(bytesVector.elementAt(i))).byteValue();
		}
		
		return bytes;
	}
}