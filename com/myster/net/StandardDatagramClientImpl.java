package com.myster.net;

import java.io.IOException;

import com.myster.transaction.*;

public interface StandardDatagramClientImpl {
	public Object getObjectFromTransaction(Transaction transaction) 
				throws IOException ;
	
	public Object getNullObject() ;
	
	public byte[] getDataForOutgoingPacket();
	
	public int getCode() ;
}