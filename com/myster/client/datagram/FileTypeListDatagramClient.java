package com.myster.client.datagram;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.util.Vector;

import com.myster.net.StandardDatagramListener;
import com.myster.net.StandardDatagramEvent;
import com.myster.net.MysterAddress;
import com.myster.net.DataPacket;
import com.myster.type.MysterType;
import com.myster.transaction.*;

public class FileTypeListDatagramClient {
	public static final int FILE_TYPE_LIST_TRANSACTION_CODE = 79;
	
	public static void getFileTypeList(MysterAddress address, final StandardDatagramListener listener) {
		TransactionSocket tsocket=new TransactionSocket(FILE_TYPE_LIST_TRANSACTION_CODE);
		
		
		// We need to convert between a generic transaciton ,listener and a Stanard Myster
		//transaction listener (to return the data pre-formated, like we want)
		tsocket.sendTransaction(new FileTypeListClientPacket(address), new TransactionListener() {
			public void transactionReply(TransactionEvent e) {
				
				//if (e.getTransaction().isError()) System.out.println("#$%^&**&&^!!!&%&*%%^&*$%^&$%^$%^$%^$%^$%^$%^$%^");
				
				try {
					listener.response(new StandardDatagramEvent(e.getAddress(), FILE_TYPE_LIST_TRANSACTION_CODE, getFileTypeListFromServerPacket(e.getTransaction())));
				} catch (IOException ex) {
					//Packet was badly formatted
					
					listener.response(new StandardDatagramEvent(e.getAddress(), FILE_TYPE_LIST_TRANSACTION_CODE, new MysterType[]{}));
				}
			}
			
			public void transactionTimout(TransactionEvent e) {
				listener.timeout(new StandardDatagramEvent(e.getAddress(), FILE_TYPE_LIST_TRANSACTION_CODE, new MysterType[]{})); // a Null object is better than a null
			}
		});
		
		// no need to close socket.. all sockets are one-shot.
	}
	
	private static MysterType[] getFileTypeListFromServerPacket(Transaction transaction) throws IOException {
		
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(transaction.getData()));
		
		Vector strings = new Vector(100,100);
		for (;;) {
			String nextString = in.readUTF();
			
			if (nextString.equals("")) break;
			
			strings.addElement(nextString);
		}
		
      MysterType[] mysterTypes = new MysterType[strings.size()];
		for (int i = 0; i < strings.size(); i++) {
        //mysterTypes[i] = new MysterType((byte[])strings.elementAt(i));
        mysterTypes[i] = new MysterType(((String)strings.elementAt(i)).getBytes());
		}
		
		return mysterTypes;
	}
	
	private static class FileTypeListClientPacket implements DataPacket {
		private final MysterAddress address;

		public FileTypeListClientPacket(MysterAddress address) {
			this.address	= address;
		}

		public MysterAddress getAddress() {
			return address;
		}
		
		
		public byte[] getBytes() {
			return getData();
		}
		
		public byte[] getData() {
			return new byte[0];
		}
		
		public byte[] getHeader() {
			return new byte[0];
		}
	}
}