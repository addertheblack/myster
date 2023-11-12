package com.myster.client.datagram;

import java.io.IOException;
import java.util.Hashtable;

import com.general.events.EventDispatcher;
import com.general.events.SyncEventDispatcher;
import com.general.net.ImmutableDatagramPacket;
import com.general.util.Semaphore;
import com.general.util.Timer;
import com.myster.net.BadPacketException;
import com.myster.net.DatagramTransport;
import com.myster.net.MysterAddress;
import com.myster.net.PingPacket;

public class PongTransport extends DatagramTransport {
    static final short transportNumber = 20559;

    static final int TIMEOUT = 60000;

    static final int FIRST_TIMEOUT = 10000;

    private Hashtable requests = new Hashtable();

    public short getTransportCode() {
        return transportNumber;
    }

    public void packetReceived(ImmutableDatagramPacket immutablePacket)
            throws BadPacketException {
        try {

            //PongPacket packet=new PongPacket(immutablePacket);

            PongItemStruct struct = null;
            MysterAddress param_address = new MysterAddress(immutablePacket
                    .getAddress(), immutablePacket.getPort());
            synchronized (requests) {
                struct = (PongItemStruct) (requests.get(param_address));
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

    /**
     * Private method for code reuse. should NOT be in any sychronized block.
     */
    private void dispatch(MysterAddress param_address,
            ImmutableDatagramPacket immutablePacket, PongItemStruct pongItem) {
        long pingTime = System.currentTimeMillis() - pongItem.timeStamp;

        pongItem.dispatcher.fireEvent(new PingEvent(PingEvent.PING,
                immutablePacket, (int) pingTime, param_address));

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
    public void ping(MysterAddress param_address, PingEventListener listener) { //DANGER DEADLOCKS!
        synchronized (requests) {
            PongItemStruct pongItemStruct = (PongItemStruct) requests
                    .get(param_address);
            if (pongItemStruct == null) {
                pongItemStruct = new PongItemStruct(param_address);
                requests.put(param_address, pongItemStruct);
                sendPacket((new PingPacket(param_address))
                        .toImmutableDatagramPacket());
            }

            pongItemStruct.dispatcher.addListener(listener);
        }
    }

    public boolean ping(MysterAddress param_address) throws IOException,
            InterruptedException { //should NOT be synchronized!!!
        DefaultPingListener p = new DefaultPingListener();
        ping(param_address, p);
        return p.getResult(); //because this blocks for a long time.
    }

    private static class DefaultPingListener extends PingEventListener {
        /*
         * THis is a utiltity class that allows calling the ping routine in a
         * synchrous way.
         */
        Semaphore sem = new Semaphore(0);

        boolean value;

        public boolean getResult() throws InterruptedException {
            sem.getLock();

            return value;
        }

        public void pingReply(PingEvent e) {
            value = (e.getPacket() != null ? true : false);
            sem.signal();
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

