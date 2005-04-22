package com.myster.net;

/**
 * Transport manager
 */

import java.io.IOException;
import java.util.Hashtable;

import com.general.net.AsyncDatagramListener;
import com.general.net.AsyncDatagramSocket;
import com.general.net.ImmutableDatagramPacket;

/**
 * This class provides functionality for the Myster Datagram multiplexer
 * protocol. The point of this protocol is to allow for an arbitrary number of
 * methods of interpreting packets on a single Internet Protocol (IP) port. The
 * protocol works by adding the protocol's "number" (aka: transport code) to the
 * start of the datagram. The server/client which receives this packet can use
 * the number to send the packet to the appropriate DatagramTransport object
 * which is responsible for dealing with the packet after that.
 * <p>
 * It's nice and simple.
 * 
 * @see DatagramTransport
 */
public class DatagramProtocolManager {
    static boolean calledAlreadyFlag = false;

    static GenericTransportManager impl;

    /**
     * Call this to "load" the manager.
     */
    public static synchronized void load() throws IOException {
        if (calledAlreadyFlag)
            return; // sould throw some sort of exception
        getImpl(); //getImpl will load the thing..
    }

    /**
     * Adds transport to port 6669 on all addresses.
     * 
     * @param transport
     *            to add
     * @return true if protocol was added successfully. false otherwise (usually
     *         because it has already been added or there is already a
     *         DatagramTransport registered for that transport code.
     * @throws IOException
     *             if transport cannot be added because the UDP sub system could
     *             not be inited
     */
    /*
     * In the future this routine might have a second param: port
     */
    public static boolean addTransport(DatagramTransport transport) throws IOException { //might
        return getImpl().addTransport(transport);
    }

    /**
     * Removes transport from port 6669 on all addresses.
     * 
     * @param transport to remove
     * @return success (will fail if protocol was not registered)
     */
    public static DatagramTransport removeTranport(DatagramTransport transport) {
        try {
            return getImpl().removeTransport(transport);
        } catch (IOException ex) {
            return null; //should not happen... not sure what to to here.
        }
    }

    private static GenericTransportManager getImpl() throws IOException { //Transport
        // Factory...
        if (impl == null) {
            synchronized (DatagramProtocolManager.class) {
                if (impl == null) {
                    //Load Transport Manager...
                    socket = new AsyncDatagramSocket(com.myster.Myster.DEFAULT_PORT);
                    impl = new GenericTransportManager(socket); //magic number
                    // bad.
                }
            }
        }
        return impl;
    }

    static AsyncDatagramSocket socket;

    public static AsyncDatagramSocket getSocket() {
        return socket;
    }

    /**
     * This class is the implementation of the transport manager. Could be any
     * class.
     */
    private static class GenericTransportManager implements DatagramSender, AsyncDatagramListener {
        Hashtable transportProtocols = new Hashtable();

        AsyncDatagramSocket dsocket;

        public GenericTransportManager(AsyncDatagramSocket dsocket) {
            this.dsocket = dsocket;
            dsocket.setPortListener(this);
        }

        public boolean addTransport(DatagramTransport t) {
            if (transportProtocols.get(new Short(t.getTransportCode())) != null)
                return false; //could not add because it already exists.

            transportProtocols.put(new Integer(t.getTransportCode()), t);

            t.setSender(this); //So the Transport has something to send packets
            // to.

            return true;
        }

        public DatagramTransport removeTransport(DatagramTransport t) {
            return (DatagramTransport) (transportProtocols
                    .remove(new Integer(t.getTransportCode())));
        }

        public void packetReceived(ImmutableDatagramPacket p) {
            try {
                DatagramTransport t = (DatagramTransport) (transportProtocols.get(new Integer(
                        getCodeFromPacket(p))));

                if (t != null) {
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
            byte[] data = p.getDataRange(0, 2);

            if (p.getSize() < 2)
                throw new IOException();

            int code = 0;
            for (int i = 0; i < data.length; i++) {
                code <<= 8; //inititally it shifts zeros...
                code |= data[i] & 255; //oops sign extending bug was
                // here.
            }

            return code;
        }
    }

}

