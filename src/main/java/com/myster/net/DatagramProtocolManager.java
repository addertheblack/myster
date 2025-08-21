package com.myster.net;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import com.general.net.AsyncDatagramListener;
import com.general.net.AsyncDatagramSocket;
import com.general.net.ImmutableDatagramPacket;
import com.myster.application.MysterGlobals;

/**
 * This manager mediates access to the Datagram socket depending on the port.
 * 
 * It includes methods to access the {@link TransportManager} in a thread safe way for the correct port
 */
public class DatagramProtocolManager {
    // Map<port, TransportManager>
    private final Map<Integer, TransportManager> lookup;
    
    public DatagramProtocolManager() {
        lookup = new HashMap<>();
    }

    /**
     * Used to access the {@link TransportManager} for a port in a thread safe way.
     * 
     * Do not let the transport manager escape.
     * 
     * @return the result of AccessTransportManager<R> function
     */
    public synchronized <R> R mutateTransportManager(int port, AccessTransportManager<R> function) {
        TransportManager t = lookup.get(port);

        if (t == null) {
            t = newTransportManager(port);
            lookup.put(port, t);
        }

        R result = null;
        try {
            result = function.apply(t);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (t.isEmpty()) {
            t.close();
            lookup.remove(port);
        }

        return result;
    }

    private TransportManager newTransportManager(int port) {
        var sigh = new GenericTransportManager(port);
        
        sigh.initSocket();
        
        return sigh;
    }


    /**
     * This class provides functionality for the Myster Datagram multiplexer protocol. The point of this
     * protocol is to allow for an arbitrary number of methods of interpreting packets on a single
     * Internet Protocol (IP) port. The protocol works by adding the protocol's "number" (aka: transport
     * code) to the start of the datagram. The server/client which receives this packet can use the
     * number to send the packet to the appropriate DatagramTransport object which is responsible for
     * dealing with the packet after that.
     * <p>
     * It's nice and simple.
     * <p>
     * 
     * @see DatagramProtocolManager
     */
    public interface TransportManager extends DatagramSender {
        /**
         * Adds transport to port on all addresses.
         * 
         * @param transport
         *            to add
         * @return true if protocol was added successfully. false otherwise (usually because it has
         *         already been added or there is already a DatagramTransport registered for that
         *         transport code.
         */
        boolean addTransport(DatagramTransport t);

        /**
         * @param t to remove but only if empty
         * @return transport that was removed or null if none
         */
        DatagramTransport removeTransportIfEmpty(DatagramTransport t);

        /**
         * @return True if there's not active transports
         */
        boolean isEmpty();

        /**
         * Close this port - must have no transports ie: isEmpty() must be true
         */
        void close();

        DatagramTransport getTransport(short transactionProtocolNumber);
    }

    /**
     * Used to gain access to the {@link TransportManager} in a thread safe way
     * using
     * {@link DatagramProtocolManager#mutateTransportManager(int, AccessTransportManager)}
     */
    public interface AccessTransportManager<R> extends Function<TransportManager, R> {
        R apply(TransportManager transportManager);
    }
    
    /**
     * This class is the implementation of the transport manager. Could be any class.
     */
    private static class GenericTransportManager implements TransportManager, AsyncDatagramListener {
        private static final Logger LOGGER = Logger.getLogger(GenericTransportManager.class.getName());
        
        private final Map<Short, DatagramTransport> transportProtocols = new HashMap<>();
        private final int port;
        
        private AsyncDatagramSocket dsocket;

        public GenericTransportManager(int port) {
            this.port = port;
        }

        @Override
        public boolean addTransport(DatagramTransport t) {
            if (transportProtocols.get(t.getTransportCode()) != null)
                return false; //could not add because it already exists.

            transportProtocols.put(t.getTransportCode(), t);

            return true;
        }

        @Override
        public DatagramTransport removeTransportIfEmpty(DatagramTransport t) {
            if (t.isEmpty()) {
                return transportProtocols.remove(t.getTransportCode());
            }
            return null;
        }

        @Override
        public void packetReceived(ImmutableDatagramPacket p) {
            try {
                DatagramTransport t =
                        (transportProtocols.get(getCodeFromPacket(p)));

                if (t != null) {
                    t.packetReceived(this::sendPacket, p);
                } else {
                    if (MysterGlobals.DEFAULT_SERVER_PORT == p.getPort()) {
                        LOGGER.info("Transport Code not found -> " + getCodeFromPacket(p) + " "
                                    + p.getAddress().toString() + ":" + p.getPort());
                    }
                }
            } catch (IOException ex) {
                LOGGER.info("Packet too short Exception.");
                ex.printStackTrace();
            }
        }
        
        private synchronized void initSocket() {
            if (dsocket == null) {
                    dsocket = new AsyncDatagramSocket(port);
                    dsocket.setPortListener(this);
            }
        }

        @Override
        public void close() {
            if (!isEmpty()) {
                throw new IllegalStateException("Could not close socket, there are still listeners");
            }

            synchronized (this) {
                if (dsocket != null) {
                    dsocket.close();
                }
            }
        }
        
        @Override
        public boolean isEmpty() {
            return transportProtocols.isEmpty();
        }
        
        public synchronized void sendPacket(ImmutableDatagramPacket p) {
            if (dsocket != null) {
                dsocket.sendPacket(p);
            }
        }

        private static short getCodeFromPacket(ImmutableDatagramPacket p) throws IOException {
            byte[] data = p.getDataRange(0, 2);

            if (p.getSize() < 2)
                throw new IOException();

            short code = 0;
            for (int i = 0; i < data.length; i++) {
                code <<= 8; //Initially it shifts zeros...
                code |= data[i] & 255; //sign extension grrr
            }

            return code;
        }

        @Override
        public DatagramTransport getTransport(short transactionProtocolNumber) {
            return transportProtocols.get(transactionProtocolNumber);
        }
    }

}

