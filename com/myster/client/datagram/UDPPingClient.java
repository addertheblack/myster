
package com.myster.client.datagram;

import java.net.*;
import java.io.InterruptedIOException;
import java.io.IOException;
import com.general.util.Semaphore;


public class UDPPingClient extends Thread{
	Semaphore sem=new Semaphore(0);
	String address;
	boolean value=false;
	
	public UDPPingClient (String s) {
		address=s;
	}

	public void run() {
		value=ping(address);
		sem.signalx();
	}
	
	public boolean getValue() {
		sem.waitx();
		return value;
	}
	
	public static boolean ping(String s) {
		DatagramSocket dsocket=null;
		int port=6669;
		String ip=s;
		if (s.indexOf(":")!=-1) {
			String portstr=s.substring(s.indexOf(":")+1);
			port=Integer.parseInt(portstr);
			ip=s.substring(0, s.indexOf(":"));
		}
		
		
		
		try {
			dsocket=new DatagramSocket(); //random port
			
			DatagramPacket workingPacket;
			DatagramPacket outgoingPacket;
			InetAddress address=InetAddress.getByName(ip);
			for (;;) {
				byte[] outdata={(byte)'P',(byte)'I',(byte)'N', (byte)'G'};
				outgoingPacket=new DatagramPacket(outdata, outdata.length, address, port);
				//System.out.println("Sending to "+ip+":"+port);
				dsocket.send(outgoingPacket);
				
				workingPacket=new DatagramPacket(new byte[4], 4);
				dsocket.setSoTimeout(10000);
				for (int i=0; true; i++) {
					try {
						dsocket.receive(workingPacket);
						byte[] data=workingPacket.getData();
						if (data[0]=='P'&&data[1]=='O'&&data[2]=='N'&&data[3]=='G') {
							//System.out.println("SUCESS!");
							return true;
						}
						continue;
					} catch (InterruptedIOException ex) {
						dsocket.send(outgoingPacket);
						if (i<2) continue;
						else return false;
					} catch (IOException ex) {
						return false;
					}
				}
			}
		} catch (IOException ex) {
			//System.out.println("UDP CLIENT sub system died");
			//ex.printStackTrace();
			return false;
		} finally {
			try { dsocket.close(); } catch (Exception ex) {}
		}
	}

}