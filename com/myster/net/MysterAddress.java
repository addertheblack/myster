package com.myster.net;

import java.net.*;

public class MysterAddress {
	int port=6669;
	InetAddress fullAddress;
	
	public static final boolean DNS_ON=true;

	public MysterAddress(String s) throws UnknownHostException { //somethign similar here too.
		String ip=s;
		if (s.indexOf(":")!=-1) {
			String portstr=s.substring(s.indexOf(":")+1);
			port=Integer.parseInt(portstr);
			ip=s.substring(0, s.indexOf(":"));
		}

		fullAddress=InetAddress.getByName(ip);

	}
	
	public MysterAddress(InetAddress i) {
		fullAddress=i;
	}
	
	public MysterAddress(InetAddress i, int port) { //should throw a runtime error.
		fullAddress=i;
		this.port=port; //need some checks here.
	}
	
	public static String getResolved(String s) throws UnknownHostException { //resolves an domain anme to ip.
		String ip=s;
		int port=6669;
		
		if (s.indexOf(":")!=-1) {
			String portstr=s.substring(s.indexOf(":")+1);
			port=Integer.parseInt(portstr);
			ip=s.substring(0, s.indexOf(":"));
		}
		
		if (DNS_ON) ip=InetAddress.getByName(ip).getHostAddress();
		return (port==6669?ip:ip+":"+port);
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
		return (port!=6669?getIP()+":"+port:getIP());
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