
package com.myster.server.datagram;

import com.general.net.AsyncDatagramListener;
import com.general.net.AsyncDatagramSocket;
import com.general.net.ImmutableDatagramPacket;

/**
 * deprecated. now useless.. To be removed.
 */
public class UDPOperator implements AsyncDatagramListener {
    AsyncDatagramSocket dsocket = null;

    public UDPOperator(AsyncDatagramSocket dsocket) {
        this.dsocket = dsocket;
        //dsocket.setPortListener(this);
    }

    public void start() { //hahaha

    }

    public void packetReceived(ImmutableDatagramPacket workingPacket) {
        /*
         * 
         * byte[] data=workingPacket.getData(); byte[] comp=(new
         * String("PING")).getBytes(); if
         * (data[0]==comp[0]&&data[1]==comp[1]&&data[2]==comp[2]&&data[3]==comp[3]) {
         * byte[] outdata=(new String("PONG")).getBytes(); outgoingPacket=new
         * ImmutableDatagramPacket(workingPacket.getAddress(),
         * workingPacket.getPort(), outdata);
         * dsocket.sendPacket(outgoingPacket); } System.out.println("Replied to
         * a ping!");
         *  
         */
    }
}