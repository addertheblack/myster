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

public class TopTenDatagramClient {
	public static final int TOP_TEN_TRANSACTION_CODE = 10;
	
	public static void getTopTen(MysterAddress address, MysterType type, final StandardDatagramListener listener) {
		TransactionSocket tsocket=new TransactionSocket(TOP_TEN_TRANSACTION_CODE);
		
		
		// We need to convert between a generic transaciton ,listener and a Stanard Myster
		//transaction listener (to return the data pre-formated, like we want)
		tsocket.sendTransaction(new TopTenClientPacket(address, type), new TransactionListener() {
			public void transactionReply(TransactionEvent e) {
				if (DatagramUtilities.dealWithError(e.getTransaction(), listener)) return;
			
				try {
					listener.response(new StandardDatagramEvent(e.getAddress(), TOP_TEN_TRANSACTION_CODE, getMysterAddressFromServerPacket(e.getTransaction())));
				} catch (IOException ex) {
					//Packet was badly formatted
					
					listener.response(new StandardDatagramEvent(e.getAddress(), TOP_TEN_TRANSACTION_CODE, new MysterAddress[]{}));
				}
			}
			
			public void transactionTimout(TransactionEvent e) {
				listener.timeout(new StandardDatagramEvent(e.getAddress(), TOP_TEN_TRANSACTION_CODE, new MysterAddress[]{})); // a Null object is better than a null
			}
		});
		
		// no need to close socket.. all sockets are one-shot.
	}
	
	private static MysterAddress[] getMysterAddressFromServerPacket(Transaction transaction) throws IOException {
		
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(transaction.getData()));
		
		Vector strings = new Vector(100,100);
		for (;;) {
			String nextString = in.readUTF();
			
			if (nextString.equals("")) break;
			
			strings.addElement(nextString);
		}
		
		MysterAddress[] addresses = new MysterAddress[strings.size()];
		for (int i = 0; i < strings.size(); i++) {
			addresses[i] = new MysterAddress((String)strings.elementAt(i));
		}
		
		return addresses;
	}
	
	private static class TopTenClientPacket implements DataPacket {
		private final MysterType type;
		private final MysterAddress address;

		public TopTenClientPacket(MysterAddress address, MysterType type) {
			this.type 		= type;
			this.address	= address;
		}

		public MysterAddress getAddress() {
			return address;
		}
		
		public MysterType getType() {
			return type;
		}
		
		public byte[] getBytes() {
			return getData();
		}
		
		public byte[] getData() {
			return type.getBytes();
		}
		
		public byte[] getHeader() {
			return new byte[0];
		}
	}
}