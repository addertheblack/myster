package com.myster.transaction;

import com.myster.net.MysterAddress;
import com.myster.net.DataPacket;

public class TransactionSocket {
	MysterAddress address;
	int protocolNumber;

	public TransactionSocket(MysterAddress address, int protocolNumber) {
		this.address=address;
		this.protocolNumber=protocolNumber;
	}

	public void sendTransaction(DataPacket t, TransactionListener l) {
		TransactionManager.sendTransaction(t, protocolNumber, l);
	}
	
	//public Transaction sendTransactionBlocking(Transaction r) {
	//	return reply;	//blocking version
	//}
	
	public void close() {
		//nothing
	}
}