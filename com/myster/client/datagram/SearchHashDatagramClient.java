package com.myster.client.datagram;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;

import com.myster.net.StandardDatagramClientImpl;
import com.myster.net.MysterAddress;
import com.myster.type.MysterType;
import com.myster.transaction.*;
import com.myster.hash.FileHash;

public class SearchHashDatagramClient implements StandardDatagramClientImpl{
	public static final int SEARCH_HASH_TRANSACTION_CODE = 150; //There is no UDP version of the first version of get file type list.
	
	MysterType type ;
	FileHash[] hashes ;
	
	public SearchHashDatagramClient(MysterType type, FileHash hash) {
		this(type, new FileHash[]{hash});
	}
	
	public SearchHashDatagramClient(MysterType type, FileHash[] hashes) {
		this.type = type;
		this.hashes = hashes;
	}

	//returns String
	public Object getObjectFromTransaction(Transaction transaction) throws IOException {
		return (new DataInputStream(new ByteArrayInputStream(transaction.getData()))).readUTF();
	}
	
	//returns String
	public Object getNullObject ()  { return new String(""); }

	public byte[] getDataForOutgoingPacket() {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		
		try {
			DataOutputStream out = new DataOutputStream(byteArrayOutputStream);
			
			out.writeInt(type.getAsInt());
			
			for (int i = 0; i < hashes.length; i++) {
				out.writeUTF(hashes[i].getHashName());
				
				out.writeShort(hashes[i].getHashLength());
				
				byte[] byteArray = hashes[i].getBytes();

				out.write(byteArray,0,byteArray.length);
			}
			
			out.writeUTF("");
		} catch (IOException ex) {
			throw new com.general.util.UnexpectedException(ex);
		}
		
		return byteArrayOutputStream.toByteArray();
	}
	
	public int getCode() { return SEARCH_HASH_TRANSACTION_CODE; }
}