package com.myster.server.datagram;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import com.myster.transaction.*;
import com.myster.net.BadPacketException;
import com.myster.tracker.MysterServer;
import com.myster.type.MysterType;


public class SearchDatagramServer extends TransactionProtocol {
	public static final int SEARCH_TRANSACTION_CODE = com.myster.client.datagram.SearchDatagramClient.SEARCH_TRANSACTION_CODE;	

	static boolean alreadyInit = false;

	public synchronized static void init() {
		if (alreadyInit) return ; //should not be init twice
		
		TransactionManager.addTransactionProtocol(new SearchDatagramServer());
	}


	public int getTransactionCode() {
		return SEARCH_TRANSACTION_CODE;
	}
	
	public void transactionReceived(Transaction transaction) throws BadPacketException {
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(transaction.getData()));
			
			ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(byteOutputStream);
			
			String searchstring;
			String tempstring;
			
			MysterType type = new MysterType(in.readInt());
			searchstring=in.readUTF();
			
			String[] stringarray;
			
	       	stringarray = com.myster.filemanager.FileTypeListManager.getInstance().getDirList(type, searchstring); //does the search matching
			
			if (stringarray!=null) {
				for (int j=0; j<stringarray.length; j++) {
					out.writeUTF(stringarray[j]);
				}
			}
			
			out.writeUTF("");
			
			sendTransaction(new Transaction(transaction,
					byteOutputStream.toByteArray(), Transaction.NO_ERROR));
		} catch (IOException ex) {
			throw new BadPacketException("Bad packet "+ex);
		}
	}
}