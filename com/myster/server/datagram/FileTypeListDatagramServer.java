package com.myster.server.datagram;



import java.io.IOException;

import java.io.ByteArrayOutputStream;

import java.io.DataOutputStream;

import java.io.ByteArrayInputStream;

import java.io.DataInputStream;



import com.myster.transaction.*;

import com.myster.filemanager.FileTypeListManager;

import com.myster.net.BadPacketException;

import com.myster.type.MysterType;





public class FileTypeListDatagramServer extends TransactionProtocol {

	public static final int NUMBER_OF_FILE_TYPE_TO_RETURN = 100;

	public static final int TOP_TEN_TRANSACTION_CODE = com.myster.client.datagram.FileTypeListDatagramClient.FILE_TYPE_LIST_TRANSACTION_CODE;	



	static boolean alreadyInit = false;



	public synchronized static void init() {

		if (alreadyInit) return ; //should not be init twice

		

		TransactionManager.addTransactionProtocol(new FileTypeListDatagramServer());

	}





	public int getTransactionCode() {

		return TOP_TEN_TRANSACTION_CODE;

	}

	

	public void transactionReceived(Transaction transaction) throws BadPacketException {

		try {

		System.out.println("CALLED");

         MysterType[] temp;

      

         ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();

         DataOutputStream out=new DataOutputStream(byteOutputStream);

      

         temp = FileTypeListManager.getInstance().getFileTypeListing();

      

         for (int i=0; i<temp.length; i++) {

            out.writeUTF(temp[i].toString()); //BAD protocol

         }  

      

         out.writeUTF("");

      

			sendTransaction(new Transaction(transaction,

         byteOutputStream.toByteArray(), Transaction.NO_ERROR));

					

			System.out.println("SIZE OF ARRAY IS -> "+ byteOutputStream.toByteArray().length);

					

		} catch (IOException ex) {

			throw new BadPacketException("Bad packet "+ex);

		}

	}



	/*

    * 

	 private int countServersReturned(MysterServer[] servers) {

		for (int i = 0; i < servers.length; i++) {

			if (servers[i] == null) return i;

		}

		

		return servers.length;

	}

	

	public byte[] getBytesFromStrings(String[] addressesAsStrings) throws IOException {

		ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();

		DataOutputStream out = new DataOutputStream(byteOutputStream);

		

		for (int i = 0; i < addressesAsStrings.length  ; i++) {

			out.writeUTF(addressesAsStrings[i]);

		}

		

		out.writeUTF("");

		

		return byteOutputStream.toByteArray();

	}

	

   

	private MysterType getTypeFromTransaction(Transaction transaction) throws IOException {

		byte[] bytes = transaction.getData();

		

		if (bytes.length != 4) throw new IOException("Packet is the wrong length");

		

		return new MysterType((new DataInputStream(new ByteArrayInputStream(bytes))).readInt());

	}*/

}