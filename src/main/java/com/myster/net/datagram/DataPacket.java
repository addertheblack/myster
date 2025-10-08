package com.myster.net.datagram;

import com.myster.net.MysterAddress;

/**
 * DataPacket represents a packet of data with header to send over the network.
 * Typically in network protocols, the protocol is built up in multiple layers.
 * These layers usually differ in that a packet of information differs between
 * the layers because usually some sort of header data is added. DataPacket is
 * here so one layer in a network protocol can access either the data or the
 * bytes of the packet to transmit without knowlege of the sepcifics of the
 * protocol. The only limitation with the interface is it assumes the is only a
 * header, no "footer" or other modification to the internals of the the packet.
 * That is it assumes that getBytes() returns getHeader() + getData(). This
 * interface will probably be refactored to no longer have a getHeader()
 *  
 */

public interface DataPacket { //ImmutablePacket should have this too? no
    public MysterAddress getAddress();

    public byte[] getData(); //returns ONLY& the data part

    public byte[] getBytes(); //returns data + header
}