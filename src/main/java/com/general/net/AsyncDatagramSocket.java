package com.general.net;

/**
 * This Socket can be used if you want to receive packets via an event and not
 * have your main thread blocked on the socket.
 * 
 * This socket needs to be closed or it will leak threads and other resources...
 */

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import com.general.thread.Invoker;
import com.general.util.LinkedList;
import com.general.util.Util;
import com.myster.util.MysterThread;

public final class AsyncDatagramSocket {
    private static final int BIG_BUFFER = 65536;
    
    private final LinkedList<ImmutableDatagramPacket> queue = new LinkedList<>();
    private final ManagerThread managerThread;
    private final DatagramPacket bufferPacket;
    private final int port;
    private final Invoker invoker = Invoker.EDT;

    /** This is accessed by the thread. All call backs to this listener happen on the invoker */
    private AsyncDatagramListener portListener;
    
    public AsyncDatagramSocket(int port) throws IOException {
        this.port = port;

        bufferPacket = new DatagramPacket(new byte[BIG_BUFFER], BIG_BUFFER);

        managerThread = new ManagerThread();
        managerThread.start();
    }

    public void setPortListener(AsyncDatagramListener p) {
        portListener = p;
    }

    /**
     * Asynchronous send. Will never block but consumes memory.
     */
    public void sendPacket(ImmutableDatagramPacket p) {
        queue.addToTail(p);
    }

    public void close() {
        managerThread.end();
    }

    private void doGetNewPackets(DatagramSocket dsocket) throws IOException {
        while (true) { 
            try {
                bufferPacket.setLength(BIG_BUFFER);

                dsocket.receive(bufferPacket);

                if (portListener != null) {
                    ImmutableDatagramPacket immutablePacket =
                            new ImmutableDatagramPacket(bufferPacket);
                    invoker.invoke(() -> {
                        portListener.packetReceived(immutablePacket);
                    });
                }
            } catch (InterruptedIOException ex) {
                return;
            }
        }
    }

    private void doSendNewPackets(DatagramSocket dsocket) throws IOException {
        int counter = 0;
        
        /* 
         * Only send a tiny number of packets then check for new incoming packets.. 
         * 
         * It's ok if we overload the buffer and cause packet loss. But we also want to check for
         * incoming packets once in a while.
         */
        while (queue.getSize() > 0 && counter < 50) {
            ImmutableDatagramPacket p = queue.removeFromHead();

            if (p != null) {
                dsocket.send(p.getDatagramPacket());
                counter++;
            } //grrr..
        }
    }

    private class ManagerThread extends MysterThread {
        public void run() {
            for (int counter = 0; counter < 3; counter++) {
                System.out.println("Opening dsocket on UDP port " + port + ".");
                try (DatagramSocket dsocket = open(port)) {
                    for (;;) {
                        if (endFlag) {
                            closingHook();
                            return;
                        }

                        doGetNewPackets(dsocket);

                        if (endFlag) {
                            closingHook();
                            return;
                        }

                        doSendNewPackets(dsocket);
                    }
                } catch (IOException ex) {
                    System.out.println("Communication error on UDP port " + port + " closing dsocket...");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException exception) {
                        exception.printStackTrace();
                    }
                }
            }
            
            System.out.println("UDP port " + port + " giving up due to too many errors...");
        }
        
        private void closingHook() {
            System.out.println("UDP port " + port + " is doing a happy close");
        }

        private static DatagramSocket open(int port) throws IOException {
            DatagramSocket dsocket = new DatagramSocket(port);
            dsocket.setSoTimeout(5);
            
            return dsocket;
        }

        @Override
        public void end() {
            flagToEnd();
        }
    }
}