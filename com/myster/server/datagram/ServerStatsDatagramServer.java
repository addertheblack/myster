package com.myster.server.datagram;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import com.myster.transaction.*;
import com.myster.net.BadPacketException;
import com.myster.tracker.MysterServer;
import com.myster.tracker.IPListManagerSingleton;
import com.myster.type.MysterType;


public class ServerStatsDatagramServer extends TransactionProtocol {
	public static final int SERVER_STATS_TRANSACTION_CODE = com.myster.client.datagram.ServerStatsDatagramClient.SERVER_STATS_TRANSACTION_CODE;	

	static boolean alreadyInit = false;

	public synchronized static void init() {
		if (alreadyInit) return ; //should not be init twice
		
		TransactionManager.addTransactionProtocol(new ServerStatsDatagramServer());
	}

	public int getTransactionCode() {
		return SERVER_STATS_TRANSACTION_CODE;
	}
	
	public void transactionReceived(Transaction transaction) throws BadPacketException {
		try {
			ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(byteOutputStream);
			
			out.writeUTF(""+com.myster.server.stream.HandshakeThread.getMMLToSend());
			
			sendTransaction(new Transaction(transaction,
					byteOutputStream.toByteArray(), Transaction.NO_ERROR));
	
		} catch (IOException ex) {
			throw new BadPacketException("Bad packet "+ex);
		}
	}
}