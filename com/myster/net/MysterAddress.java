/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2002-2003
*/

package com.myster.net;

//import Myster;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class MysterAddress {
	InetAddress fullAddress;
	int port;

	public MysterAddress(String s) throws UnknownHostException { //should throw a runtime error.
		String ip=s;
		int port = com.myster.Myster.DEFAULT_PORT;
		
		if (s.indexOf(":") != -1) {
			String portstr = s.substring(s.indexOf(":")+1);
			try {
				port = Integer.parseInt(portstr); //need some checks here.
			} catch (NumberFormatException ex) {
				throw new UnknownHostException("Port value is not a number");
			}
			ip = s.substring(0, s.indexOf(":"));
		}

		init(InetAddress.getByName(ip), port);
	}
	
	public MysterAddress(InetAddress i) {
		init(i, com.myster.Myster.DEFAULT_PORT);
	}
	
	public MysterAddress(InetAddress i, int port) { //should throw a runtime error.
		init(i, port);
	}
	
	private void init(InetAddress i, int port) {
		fullAddress=i;
		this.port=port; //need some checks here
		
		if (port > 0xFFFF) throw new IllegalArgumentException("Port is out of range -> "+port);
	}

	public String getIP() {
		return fullAddress.getHostAddress();
	}
	
	public InetAddress getInetAddress() {//throws UnknownHostException {
		return fullAddress;
	}
	
	public int getPort() {
		return port;
	}
	
	public String toString() {
		return (port!=com.myster.Myster.DEFAULT_PORT?getIP()+":"+port:getIP());
	}
	
	public boolean equals(Object aa) {
		MysterAddress a=(MysterAddress)aa;
		if (fullAddress.equals(a.fullAddress)&&(port==a.port)) return true;
		return false;
	}
	
	public int hashCode() {
		return fullAddress.hashCode() ^ port;
	}
}
