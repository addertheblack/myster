/**
	a somewhat useless class for now.
*/


package com.myster.server.event;

public class OperatorEvent extends ServerEvent { //wow
	public static final int PING=0;
	public static final int DISCONNECT=1;
	
	public OperatorEvent(int id, String ip) {
		super(id, ip, -1);
	}

}