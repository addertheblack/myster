package com.myster.net;

/**
 * Transport manager
 */

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.general.net.AsyncDatagramListener;
import com.general.net.AsyncDatagramSocket;
import com.general.net.ImmutableDatagramPacket;

/**
 * This class provides functionality for the Myster Datagram multiplexer protocol. The point of this
 * protocol is to allow for an arbitrary number of methods of interpreting packets on a single
 * Internet Protocol (IP) port. The protocol works by adding the protocol's "number" (aka: transport
 * code) to the start of the datagram. The server/client which receives this packet can use the
 * number to send the packet to the appropriate DatagramTransport object which is responsible for
 * dealing with the packet after that.
 * <p>
 * It's nice and simple.
 * 
 * @see DatagramTransport
 */
public class DatagramProtocolManager {
    private static GenericTransportManager impl;

//    /**
//     * Call this to "load" the manager.
//     */
//    public static synchronized void load() throws IOException {
//        if (calledAlreadyFlag)
//            return; // sould throw some sort of exception
//        getImpl(); //getImpl will load the thing..
//    }

    /**
     * Adds transport to port 6669 on all addresses.
     * 
     * @param transport
     *            to add
     * @return true if protocol was added successfully. false otherwise (usually because it has
     *         already been added or there is already a DatagramTransport registered for that
     *         transport code.
     */
    /*
     * In the future this routine might have a second param: port
     */
    public static boolean addTransport(DatagramTransport transport) { //might
        return getImpl().addTransport(transport);
    }

//    /**
//     * Removes transport from port 6669 on all addresses.
//     * 
//     * @param transport
//     *            to remove
//     * @return success (will fail if protocol was not registered)
//     */
//    public static DatagramTransport removeTranport(DatagramTransport transport) {
//        return getImpl().removeTransport(transport);
//    }

    private synchronized static GenericTransportManager getImpl() { //Transport
        if (impl == null) {
            //Load Transport Manager...
            impl = new GenericTransportManager(); //magic number
            // bad.
        }
        return impl;
    }

    /**
     * This class is the implementation of the transport manager. Could be any class.
     */
    private static class GenericTransportManager implements DatagramSender, AsyncDatagramListener {
        private final Map<Short, DatagramTransport> transportProtocols = new HashMap<>();

        AsyncDatagramSocket dsocket;

        public boolean addTransport(DatagramTransport t) {
            if (transportProtocols.get(t.getTransportCode()) != null)
                return false; //could not add because it already exists.

            transportProtocols.put(t.getTransportCode(), t);

            t.setSender(this); //So the Transport has something to send packets
            // to.

            return true;
        }

//        public DatagramTransport removeTransport(DatagramTransport t) {
//            return (transportProtocols
//                    .remove(t.getTransportCode()));
//        }

        public void packetReceived(ImmutableDatagramPacket p) {
            try {
                DatagramTransport t =
                        (transportProtocols.get(getCodeFromPacket(p)));

                if (t != null) {
                    t.packetReceived(p);
                }
            } catch (IOException ex) {
                System.out.println("Packet too short Exception.");
                ex.printStackTrace();
            }
        }

        private static long lastErrorTime = 0;

        public synchronized void sendPacket(ImmutableDatagramPacket p) {
            if (dsocket == null) {
                if (System.currentTimeMillis() - lastErrorTime > 5 * 60 * 100) {
                    try {
                        dsocket = new AsyncDatagramSocket(com.myster.application.MysterGlobals.DEFAULT_PORT);
                        dsocket.setPortListener(this);
                    } catch (IOException ex) {
                        System.out.println("The datagram socket could not be created ->> ");
                        ex.printStackTrace();
                    }
                }
            }
            if (dsocket != null)
                dsocket.sendPacket(p);
        }

        private static short getCodeFromPacket(ImmutableDatagramPacket p) throws IOException {
            byte[] data = p.getDataRange(0, 2);

            if (p.getSize() < 2)
                throw new IOException();

            short code = 0;
            for (int i = 0; i < data.length; i++) {
                code <<= 8; //inititally it shifts zeros...
                code |= data[i] & 255; //oops sign extending bug was
                // here.
            }

            return code;
        }
    }

}

