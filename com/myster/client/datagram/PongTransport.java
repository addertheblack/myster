package com.myster.client.datagram;

import com.myster.net.DatagramTransport;
import com.myster.net.MysterAddress;
import java.util.Hashtable;
import java.util.Vector;
import com.general.net.ImmutableDatagramPacket;
import com.myster.net.BadPacketException;
import com.myster.net.PongPacket;
import com.myster.net.PingPacket;
import java.io.IOException;
import com.general.util.Semaphore;
import com.general.util.Timer;
import java.util.Enumeration;

public class PongTransport extends DatagramTransport {
	static final int transportNumber=1347374663;
	static final int TIMEOUT=60000;
	static final int FIRST_TIMEOUT=20000;
	
	private Hashtable requests=new Hashtable();
	
	MysterAddress a;
	
	
	public int getTransportCode() {
		return transportNumber;
	}
	
	public synchronized void packetReceived(ImmutableDatagramPacket immutablePacket) throws BadPacketException {
		try {
			
			//PongPacket packet=new PongPacket(immutablePacket);
			
			MysterAddress param_address=new MysterAddress(immutablePacket.getAddress(), immutablePacket.getPort());
			
			dispatch(param_address, immutablePacket);
		} catch (Exception ex){
			ex.printStackTrace();
		}
	}
	
	private void dispatch(MysterAddress param_address, ImmutableDatagramPacket immutablePacket) {
		PongItemStruct pongItem=((PongItemStruct)(requests.get(param_address)));
		
		if (pongItem==null) {
			System.out.println("Got pong from address I've never heard of..");
			return;
		}
		
		long pingTime=System.currentTimeMillis()-pongItem.timeStamp;
		Vector pingListeners=pongItem.vector;
		
		
		for (int i=0; i<pingListeners.size(); i++) {
			((PingEventListener)(pingListeners.elementAt(i))).fireEvent(new PingEvent(PingEvent.PING, immutablePacket, (int)pingTime, param_address));
		}
		
		requests.remove(param_address);
	}

	
	
	public synchronized void ping(MysterAddress param_address, PingEventListener listener) throws IOException { //DANGER DEADLOCKS!
		PongItemStruct pongItemStruct=(PongItemStruct)requests.get(param_address);
		if (pongItemStruct==null) {
			pongItemStruct=new PongItemStruct();
			requests.put(param_address,pongItemStruct);
			Timer t=new Timer(new TimeoutClass(param_address), FIRST_TIMEOUT+(1*1000), false);
			sendPacket((new PingPacket(param_address)).toImmutableDatagramPacket());
		}
		
		
		pongItemStruct.vector.addElement(listener);
		a=param_address;
	}
	
	public boolean ping(MysterAddress param_address) throws IOException, InterruptedException { //should NOT be synchronized!!!
		DefaultPingListener p=new DefaultPingListener();
		ping(param_address,p);
		return p.getResult(); //because this blocks for a long time.
	}
	
	
	private static class DefaultPingListener extends PingEventListener {
		
		Semaphore sem=new Semaphore(0);
		boolean value;
		
		
		public boolean getResult() throws InterruptedException {
			sem.getLock();
			
			return value;
		}
		
		public void pingReply(PingEvent e) {
			value=(e.getPacket()!=null?true:false);
			if (value) System.out.println("Got pong reply it took "+e.getPingTime()+"ms.");
			else System.out.println("Ping timeout.");
			sem.signal();
		}
	}
	
	private static class PongItemStruct {
		public final Vector vector;
		public final long timeStamp;
		public boolean secondPing=false;	//used when the connection has timeout on one packet to send a second.
		
		public PongItemStruct() {
			timeStamp=System.currentTimeMillis();
			vector=new Vector(10,10);
		}
	}
	
	private class TimeoutClass implements Runnable {
		MysterAddress address;
		
		public TimeoutClass(MysterAddress address) {
			this.address=address;
		}
		
		public void run() {
			long curTime=System.currentTimeMillis();
			Vector itemsToDelete=new Vector(2,10);
			synchronized (requests) {
				Enumeration enum=requests.keys();
				if (enum.hasMoreElements()) {
					for (Object key=enum.nextElement();; key=enum.nextElement()) {
						PongItemStruct pongItem=(PongItemStruct)requests.get(key);
						
						if ((pongItem.timeStamp<=(curTime-FIRST_TIMEOUT))&&(!pongItem.secondPing)) {
							sendPacket((new PingPacket((MysterAddress)key)).toImmutableDatagramPacket());
							pongItem.secondPing=true;
							Timer t=new Timer(new TimeoutClass((MysterAddress)key), TIMEOUT-(curTime-pongItem.timeStamp)+(1*1000), false);
							System.out.println("Trying "+key+" again. it only has "+(TIMEOUT-(curTime-pongItem.timeStamp))+"ms left.");
						}
						
						if (pongItem.timeStamp<=(curTime-TIMEOUT)) {
							itemsToDelete.addElement(key);
						}
						
						if (!enum.hasMoreElements()) break;
					}
				}
				
				for (int i=0; i<itemsToDelete.size(); i++) {
					dispatch((MysterAddress)(itemsToDelete.elementAt(i)), null);
					requests.remove(itemsToDelete.elementAt(i));
				}
			}
		}
	}
}

