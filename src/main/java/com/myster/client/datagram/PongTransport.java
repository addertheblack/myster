package com.myster.client.datagram;

import java.util.HashMap;
import java.util.Map;

import com.general.events.EventDispatcher;
import com.general.events.SyncEventDispatcher;
import com.general.net.ImmutableDatagramPacket;
import com.general.util.Timer;
import com.myster.net.BadPacketException;
import com.myster.net.DatagramTransport;
import com.myster.net.MysterAddress;
import com.myster.net.PingPacket;

public class PongTransport extends DatagramTransport {
    public static final short TRANSPORT_NUMBER = 20559;
    private static final int TIMEOUT = 60000;
    private static final int FIRST_TIMEOUT = 10000;

    private final Map<MysterAddress, PongItemStruct> requests = new HashMap<>();

    public short getTransportCode() {
        return TRANSPORT_NUMBER;
    }

    public void packetReceived(ImmutableDatagramPacket immutablePacket)
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
     * Private method for code reuse. should NOT be in any sychronized block.
     */
    private void dispatch(MysterAddress param_address,
                          ImmutableDatagramPacket immutablePacket,
                          PongItemStruct pongItem) {
        long pingTime = System.currentTimeMillis() - pongItem.timeStamp;

        pongItem.dispatcher.fireEvent(new PingEvent(PingEvent.PING,
                                                    immutablePacket,
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
                
                // TODO: NO! Need to connect on the fly
                sendPacket((new PingPacket(param_address))
                        .toImmutableDatagramPacket());
            }

            pongItemStruct.dispatcher.addListener(listener);
        }
    }

    private class PongItemStruct {
        public final EventDispatcher dispatcher;

        public final long timeStamp;

        public boolean secondPing = false; //used when the connection has
                                           // timeout on one packet to send a
                                           // second.

        public Timer timer;

        public PongItemStruct(MysterAddress param_address) {
            timeStamp = System.currentTimeMillis();
            dispatcher = new SyncEventDispatcher();
            timer = new Timer(new TimeoutClass(param_address), FIRST_TIMEOUT
                    + (1 * 1000), false);
        }
    }

    private class TimeoutClass implements Runnable {
        MysterAddress address;

        public TimeoutClass(MysterAddress address) {
            this.address = address;
        }

        public void run() {
            long curTime = System.currentTimeMillis();

            PongItemStruct struct = null;
            synchronized (requests) {
                struct = (PongItemStruct) (requests.get(address));
                if (struct != null) {
                    if (!struct.secondPing) {
                        sendPacket((new PingPacket(address))
                                .toImmutableDatagramPacket()); //send two
                                                               // packet the
                                                               // second time
                        sendPacket((new PingPacket(address))
                                .toImmutableDatagramPacket()); //send two
                                                               // packet the
                                                               // second time
                        struct.secondPing = !struct.secondPing;
                        //System.out.println("Trying "+address+" again. it only
                        // has "+(TIMEOUT-(curTime-struct.timeStamp))+"ms
                        // left.");
                        struct.timer = new Timer(new TimeoutClass(address),
                                TIMEOUT - (curTime - struct.timeStamp)
                                        + (1 * 1000), false);
                        return;
                    } else {
                        justBeforeDispatch(address, struct);
                        if (struct.timeStamp > (curTime - TIMEOUT)) {
                            //System.out.println("Assumption bad in Pong
                            // transport");
                            //System.exit(1);
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

