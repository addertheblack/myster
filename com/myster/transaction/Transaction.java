/**
*	The transaction class is reponsible for encapsulation transaction data / header formating
*	This class does double duty filling in for both to -> transactions and thier replies <-
*	. This leads to code to be more nasty than usual but does very nicely on the code reuse front.
*	To client (replies) and to server (to) packets are formated the same except that to client
*	packets have an extra byte to signal basic protocol level errors. The only error defined at
*	the moment is the TRANSACTION_TYPE_UNKNOWN which is sent by servers who have been asked
*	for data of an unknown type. This itself is here so that unknown transaction type errors
*	can sperated from timeout and other errors that might confuse debugging or lead to 
*	ambiguous error messages.
*	<p>
*	This object is intended to be immutable. This object should not be created by outside packages
*	as their are several feilds that need to be filled out that outside code cannot fill out.
*	Since the object is supposed to be immutable it can be sent outside core protocol code
*	after it has been created without fear of corruption. This class should not be subclassed
*	but wrapper instead using the DataPacket interface.
*	
*/


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
	
	private final static int HEADER_SIZE=10; //reply packets are 1 byte longer
	
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
			
			connectionNumber=getConnectionNumber(fullyQualifiedConnectionNumber);
			isForClient=getPacketDirection(fullyQualifiedConnectionNumber);
			
			if (isForClient()) { //reply packets have a 1 byte longer header. THis byte is the error byte. !=0 is err
				int errorTemp=in.read(); 
				if (errorTemp==-1) throw new NotATransactionException("Transaction shorter than header.");
				errorByte=(byte)errorTemp;
			} else {
				errorByte=0;
			}
		} catch (IOException ex) {
			throw new NotATransactionException("Formating error occured: "+ex);
		}

		
		data=new byte[bytes.length-getHeaderSize()];
		System.arraycopy(bytes, getHeaderSize(), data, 0, data.length);
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
	
	public byte[] getHeader() { //slow
		ByteArrayOutputStream byteOut=new ByteArrayOutputStream();
		DataOutputStream out=new DataOutputStream(byteOut);
		
		try {
			out.writeShort(TRANSACTION_PROTOCOL_NUMBER);
			out.writeInt(transactionCode);
			out.writeInt(getFullyQualifiedConnectionNumber());
			if (isForClient()) out.write(errorByte); //...
		} catch (IOException ex) {
			ex.printStackTrace(); //!!!!!! should never happen since all calls are to byte[]
		}
		
		return byteOut.toByteArray();
	}
	
	
	private int getHeaderSize() {	//sizeToSkip for header! (should be equal to getBytes().length)
		return HEADER_SIZE + (isForClient()?1:0); //reply packets are 1 byte longer (the err packet)
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

