package com.myster.client.datagram;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.general.events.NewGenericDispatcher;
import com.general.net.AsyncDatagramSocket;
import com.general.net.ImmutableDatagramPacket;
import com.general.thread.Invoker;
import com.general.util.Timer;
import com.myster.net.BadPacketException;
import com.myster.net.DatagramSender;
import com.myster.net.DatagramTransport;
import com.myster.net.MysterAddress;
import com.myster.net.PingPacket;

public class PongTransport extends DatagramTransport {
    public static final short TRANSPORT_NUMBER = 20559;
    
    private static final int TIMEOUT = 60000;
    private static final int FIRST_TIMEOUT = 1000;
    
    private static final Logger LOGGER = Logger.getLogger(AsyncDatagramSocket.class.getName());

    private final Map<MysterAddress, PongItemStruct> requests = new HashMap<>();
    private final DatagramSender sender;
    
    public PongTransport(DatagramSender sender) {
        this.sender = sender;
    }

    @Override
    public short getTransportCode() {
        return TRANSPORT_NUMBER;
    }

    @Override
    public void packetReceived(DatagramSender ignore, ImmutableDatagramPacket immutablePacket)
            throws BadPacketException {
        try {
            PongItemStruct struct = null;
            MysterAddress param_address =
                    new MysterAddress(immutablePacket.getAddress(), immutablePacket.getPort());
            synchronized (requests) {
                struct = requests.get(param_address);
                if (struct != null) {
                    justBeforeDispatch(param_address, struct);
                } else {
                    LOGGER.fine("Got PONG response but can't find a request that matches it: " + param_address + ":" + immutablePacket.getPort());
                    return;
                }
            }
            dispatch(param_address, immutablePacket, struct);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    @Override
    public boolean isEmpty() {
        synchronized (requests) {
        return requests.isEmpty();
        }
    }

    /**
     * Private method for code reuse. should NOT be in any synchronized block.
     */
    private void dispatch(MysterAddress param_address,
                          ImmutableDatagramPacket immutablePacket,
                          PongItemStruct pongItem) {
        long pingTime = System.currentTimeMillis() - pongItem.timeStamp;

        pongItem.dispatcher.fire()
                .pingReply(new PingEvent(immutablePacket,
                                         (int) pingTime,
                                         param_address));
    }

    /**
     * For code re-use purposes ONLY!.. should be inlined! *Should* be in
     * sychronized block.
     */
    private void justBeforeDispatch(MysterAddress param_address,
            PongItemStruct pongItem) {
        pongItem.timer.cancelTimer();
        requests.remove(param_address);
    }

    /**
     * Pings this address. Reply is on the event thread.
     * 
     * @param param_address
     * @param listener
     */
    public void ping(MysterAddress param_address, PingEventListener listener) {
        synchronized (requests) {
            PongItemStruct pongItemStruct = requests.get(param_address);
            if (pongItemStruct == null) {
                pongItemStruct = new PongItemStruct(param_address);
                requests.put(param_address, pongItemStruct);

                sender.sendPacket((new PingPacket(param_address)).toImmutableDatagramPacket());
            }

            pongItemStruct.dispatcher.addListener(listener);
        }
    }

    private class PongItemStruct {
        public final NewGenericDispatcher<PingEventListener> dispatcher;
        public final long timeStamp;

        /**
         * used when the connection has timeout on one packet to send a second.
         */
        public int pingAttempt = 0;

        public Timer timer;

        public PongItemStruct(MysterAddress param_address) {
            timeStamp = System.currentTimeMillis();
            dispatcher = new NewGenericDispatcher<PingEventListener>(PingEventListener.class, Invoker.SYNCHRONOUS);
            timer = new Timer(new TimeoutClass(param_address), FIRST_TIMEOUT, false);
        }
    }

    private class TimeoutClass implements Runnable {
        private static final int MAX_PING_ATTEMPTS = 5;
        MysterAddress address;

        public TimeoutClass(MysterAddress address) {
            this.address = address;
        }

        public void run() {
            long curTime = System.currentTimeMillis();

            PongItemStruct struct = null;
            synchronized (requests) {
                struct = requests.get(address);
                if (struct != null) {
                    long timeSoFar = (curTime - struct.timeStamp);
                    
                    struct.pingAttempt ++;
                    if (struct.pingAttempt < MAX_PING_ATTEMPTS && timeSoFar < TIMEOUT) {
                        sender.sendPacket((new PingPacket(address))
                                .toImmutableDatagramPacket());
                        long maxTimeoutSize = TIMEOUT - timeSoFar;
                        int timeoutSizeCandidate = (int)Math.pow(2, struct.pingAttempt) * FIRST_TIMEOUT;
                        struct.timer = new Timer(new TimeoutClass(address),
                                                 Math.min(maxTimeoutSize, timeoutSizeCandidate));

                        return;
                    } else {
                        justBeforeDispatch(address, struct);
                        if (struct.timeStamp > (curTime - TIMEOUT)) {
                            // nothing ?
                        }
                    }
                } else {
                    return;
                }
            }

            dispatch(address, null, struct);
        }

    }
}

