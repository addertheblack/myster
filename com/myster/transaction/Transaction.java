package com.myster.transaction;

import com.myster.net.DataPacket;
import com.myster.net.MysterAddress;
import com.general.net.ImmutableDatagramPacket;
import com.general.util.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.io.IOException;

public final class Transaction implements DataPacket { 		//Immutable (Java needs some way to enforce immutable on sub classes)
	final MysterAddress address;	//immutable
	final int transactionCode;
	final int connectionNumber;
	final boolean isForClient;
	final byte errorByte; //To server transactions should have simply 0000 0000 here or "NO_ERROR" (padding ick!)
	
	final byte[] data;
	
	private final static int HEADER_SIZE=11;
	
	public final static short TRANSACTION_PROTOCOL_NUMBER=1234;
	
	public final static byte NO_ERROR=0;
	public final static byte TRANSACTION_TYPE_UNKNOWN=1;
	
	///note: put in checks for length etc...
	protected Transaction(ImmutableDatagramPacket packet) throws NotATransactionException {	//makes a Transaction representation.. Inverse of toImmutableDatagramPacket
		this.address=new MysterAddress(packet.getAddress(), packet.getPort());
		
		byte[] bytes=packet.getData();
		
		ByteArrayInputStream bin=new ByteArrayInputStream(bytes);
		DataInputStream in=new DataInputStream(bin);
		
		int fullyQualifiedConnectionNumber;
		try {
			int int_temp=in.readShort();
			if (int_temp!=TRANSACTION_PROTOCOL_NUMBER) throw new NotATransactionException("Tried to make a transaction from a packet of type "+int_temp+" instead of type "+TRANSACTION_PROTOCOL_NUMBER+".");
			transactionCode=in.readInt();
			fullyQualifiedConnectionNumber=in.readInt();
			int errorTemp=in.read();
			if (errorTemp==-1) throw new NotATransactionException("Transaction shorter than header.");
			errorByte=(byte)errorTemp;
		} catch (IOException ex) {
			throw new NotATransactionException("Formating error occured: "+ex);
		}
		//isForClient flag is the least significant bit in the connection number field (so all odd are for client)
		connectionNumber=getConnectionNumber(fullyQualifiedConnectionNumber);
		isForClient=getPacketDirection(fullyQualifiedConnectionNumber);
		
		data=new byte[bytes.length-HEADER_SIZE];
		
		System.arraycopy(bytes, HEADER_SIZE, data, 0, data.length);
	}
	
	/**
	 *	Creates a transaction that is a !!!reply!!! to the passed transaction. Resulting packet will be intended for
	 * 	the inverse recipient from itself (ie: if the passed transaction is for a client then this packet will
	 *	be for a server.
	 */
	public Transaction(Transaction transaction, byte[] bytes, byte errorByte) {
		this(transaction.getAddress(), transaction.getTransactionCode(), transaction.getConnectionNumber(), bytes, ! transaction.isForClient(), errorByte);
	}
	
	/**
	 *	Default to assuming it is NOT for client (ie it's an outgoing packet)
	 */
	protected Transaction(MysterAddress transaction, int transactionCode, int connectionNumber, byte[] bytes) {
		this(transaction,transactionCode,connectionNumber,bytes, false, NO_ERROR);
	}
	
	protected Transaction(MysterAddress address, int transactionCode, int connectionNumber, byte[] bytes, boolean isForClient, byte errorByte) {
		this.address=address;
		this.transactionCode=transactionCode;
		this.connectionNumber=connectionNumber;
		this.isForClient=isForClient;
		this.data=bytes;
		this.errorByte=errorByte;
	}
	
	public MysterAddress getAddress() {
		return address;	
	}
	
	public boolean isForClient() {
		return isForClient;
	}
	
	public int getTransactionCode() {
		return transactionCode;
	}
	
	public int getConnectionNumber() {
		return connectionNumber;
	}
	
	public byte getErrorCode() {
		return errorByte;
	}
	
	public boolean isError() {
		return (errorByte!=NO_ERROR);
	}
	
	public ImmutableDatagramPacket getImmutableDatagramPacket() {
		return toImmutableDatagramPacket();
	}
	
	public ImmutableDatagramPacket toImmutableDatagramPacket() {
		return new ImmutableDatagramPacket(address.getInetAddress(), address.getPort(), getBytes());
	}
	
	public byte[] getBytes() { 	// (for immutable packet) is slow
		return Util.concatenateBytes(getHeader(), data); //byteOut.. funny.
	}
	
	public byte[] getHeader() {
		ByteArrayOutputStream byteOut=new ByteArrayOutputStream();
		DataOutputStream out=new DataOutputStream(byteOut);
		
		try {
			out.writeShort(TRANSACTION_PROTOCOL_NUMBER);
			out.writeInt(transactionCode);
			out.writeInt(getFullyQualifiedConnectionNumber());
			out.write(errorByte);
		} catch (IOException ex) {
			ex.printStackTrace(); //!!!!!!
		}
		
		return byteOut.toByteArray();
	}
	
	
	private int getHeaderSize() {	//sizeToSkip for header! (should be equal to getBytes().length)
		return HEADER_SIZE;
	}
	
	public byte[] getData() { //for those who wish to parse the juice (You know what is the juice?).
		return (byte[])(data.clone());
	}
	
	private int getFullyQualifiedConnectionNumber() { //long function name.
		int temp=connectionNumber << 1;
		
		if (isForClient) {
			temp++;
		}
		
		return temp;
	}
	
	private boolean getPacketDirection(int fullyQualifiedConnectionNumber) {
		return ((fullyQualifiedConnectionNumber & 1) == 1);
	}
	
	private int getConnectionNumber(int fullyQualifiedConnectionNumber) {
		return fullyQualifiedConnectionNumber >>> 1;
	}
	
}

