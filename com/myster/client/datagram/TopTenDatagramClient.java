package com.myster.client.datagram;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.util.Vector;

import com.myster.net.MysterAddress;
import com.myster.net.StandardDatagramClientImpl;
import com.myster.type.MysterType;
import com.myster.transaction.*;

public class TopTenDatagramClient implements StandardDatagramClientImpl {
	public static final int TOP_TEN_TRANSACTION_CODE = 10;

	private final MysterType type;
	
	public TopTenDatagramClient(MysterType type) {this.type = type;}
	
	
	// returns a MysterAddress[]
	public Object getObjectFromTransaction(Transaction transaction) throws IOException {
		
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
	
	// returns a MysterAddress[]
	public Object getNullObject() { return new MysterAddress[]{}; }
	
	public int getCode() { return TOP_TEN_TRANSACTION_CODE; }
	
	public byte[] getDataForOutgoingPacket() { return type.getBytes(); }
}