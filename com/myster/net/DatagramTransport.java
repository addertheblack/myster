

package com.myster.net;

import com.general.net.ImmutableDatagramPacket;

/**
*	All classes who wish to be registered as offical transport protocols
*	Need to use this "interface". Common usage is to register as a listener.
*	At that time the TransportManager will set the sender so Immutablepackets
*	can be sent through the correct manager. This is so brilliant. I'm glad I
*	thought of it :-)..
*
*	Transport Listener
*/


public abstract class DatagramTransport implements DatagramSender {
	DatagramSender sender;
	
	public void sendPacket(ImmutableDatagramPacket packet) {
		if (sender==null)
				; //get pissed off...
		
		sender.sendPacket(packet);
	}
	/**
	*	This routine is so the transport manager can set the machanism
	*	through wich you are supposed to send your outgoing packets.
	*/
	protected final void setSender(DatagramSender sender) { //don't over-ride (or even access)
		this.sender=sender; //weeeee...
	}
	
	public abstract int getTransportCode() ;
	
	public abstract void packetReceived(ImmutableDatagramPacket p) throws BadPacketException ;

}

