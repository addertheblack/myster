

package com.myster.net;

/**
	Transport manager
*/

import java.io.IOException;
import com.general.net.AsyncDatagramSocket;
import com.general.net.ImmutableDatagramPacket;
import java.util.Hashtable;
import com.general.net.AsyncDatagramListener;

public class DatagramProtocolManager {
	static boolean calledAlreadyFlag=false;
	static GenericTransportManager impl;
	/**
	*	Call this to "load" the manager.
	*/
	public static synchronized void load() throws IOException {
		if (calledAlreadyFlag) return; // sould throw some sort of exception
		getImpl(); //getImpl will load the thing..
	}

	/**
	*	Adds transport to port 6669 on all addresses.
	*/
	public static boolean addTransport(DatagramTransport transport) throws IOException { //might have second parameter of port soon.
		return getImpl().addTransport(transport);
	}
	
	/**
	*	Removes transport from port 6669 on all addresses.
	*/
	public static DatagramTransport removeTranport(DatagramTransport transport) {
		try {
			return getImpl().removeTransport(transport);
		} catch (IOException ex) {
			return null; //should not happen... not sure what to to here.
		}
	}

	private static GenericTransportManager getImpl() throws IOException { //Transport Factory...
		if (impl==null) {
			synchronized (DatagramProtocolManager.class) {
				if (impl==null) {
					//Load Transport Manager...
					socket=new AsyncDatagramSocket(6669);
					impl=new GenericTransportManager(socket); //magic number bad.
				}
			}
		}
		return  impl;
	}
	
	static AsyncDatagramSocket socket;
	public static AsyncDatagramSocket getSocket() {
		return socket;
	}
	
	/**
	* This class is the implementation of the transport manager. Could be any class.
	*/
	private static class GenericTransportManager implements DatagramSender, AsyncDatagramListener{
		Hashtable transportProtocols=new Hashtable();
		AsyncDatagramSocket dsocket;
		
		public GenericTransportManager(AsyncDatagramSocket dsocket) {
			this.dsocket=dsocket;
			dsocket.setPortListener(this);
		}
		
		public boolean addTransport(DatagramTransport t) {
			if (transportProtocols.get(new Short(t.getTransportCode()))!=null) return false;	//could not add because it already exists.
			
			transportProtocols.put(new Integer(t.getTransportCode()), t);
			
			t.setSender(this); //So the Transport has something to send packets to.
			
			return true;
		}
		
		public DatagramTransport removeTransport(DatagramTransport t) {
			return (DatagramTransport)(transportProtocols.remove(new Integer(t.getTransportCode())));
		}
		
		public void packetReceived(ImmutableDatagramPacket p) {
			try {
				DatagramTransport t=(DatagramTransport)(transportProtocols.get(new Integer(getCodeFromPacket(p))));
				
				if (t!=null) {
					t.packetReceived(p);
				}
			} catch (IOException ex) {
				System.out.println("Packet too short Exception.");
				ex.printStackTrace();
			}
		}
		
		public void sendPacket(ImmutableDatagramPacket p) {
			dsocket.sendPacket(p);
		}
	
		private static int getCodeFromPacket(ImmutableDatagramPacket p) throws IOException {
			byte[] data=p.getDataRange(0, 2);
	
			if (p.getSize()<2) throw new IOException();
					
			int code=0;
			for (int i=0; i<data.length; i++) {
				code<<=8; //inititally it shifts zeros...
				code|=((int)data[i]) & 255; //oops sign extending bug was here.
			}
			
			return code;
		}
	}

}

