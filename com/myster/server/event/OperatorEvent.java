/**
	a somewhat useless class for now.
*/


package com.myster.server.event;

import com.myster.net.MysterAddress;

public class OperatorEvent extends ServerEvent { //wow
	public static final int PING		= 0;
	public static final int DISCONNECT	= 1;
	public static final int CONNECT		= 2;
	
	public OperatorEvent(int id, MysterAddress address) {
		super(id, address, -1);
	}

}