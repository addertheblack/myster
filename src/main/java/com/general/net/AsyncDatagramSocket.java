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
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.general.thread.Invoker;

public final class AsyncDatagramSocket {
    private static final Logger LOGGER = Logger.getLogger(AsyncDatagramSocket.class.getName());
    
    // Specify the maximum size of a UDP packet. This is the maximum size of a UDP packet.
    private static final int BIG_BUFFER = 65536;
    
    private final Deque<ImmutableDatagramPacket> queue = new ConcurrentLinkedDeque<>();
    private final SocketRunner socketRunner;
    private final DatagramPacket bufferPacket;
    private final int port;
    private final Invoker invoker = Invoker.EDT;

    private volatile int usedPort = -1;

    /** This is accessed by the thread. All call backs to this listener happen on the invoker */
    private volatile AsyncDatagramListener portListener;
    
    /**
     * @param port to user or 0 for any free port
     */
    public AsyncDatagramSocket(int port) {
        this.port = port;

        bufferPacket = new DatagramPacket(new byte[BIG_BUFFER], BIG_BUFFER);

        socketRunner = new SocketRunner();
        Executors.newVirtualThreadPerTaskExecutor().execute(socketRunner);
    }

    public void setPortListener(AsyncDatagramListener p) {
        portListener = p;
    }

    /**
     * Package protected for unit tests
     * 
     * @return port used or -1 if not yet known or -2 if socket is closed
     */
    int getUsedPort() {
        return usedPort;
    }

    /**
     * Asynchronous send. Will never block but consumes memory.
     */
    public void sendPacket(ImmutableDatagramPacket p) {
        queue.addLast(p);
    }

    public void close() {
        // don't wait for the thread to close. It will be done asynchronously.
        socketRunner.flagToEnd();
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
        while (queue.peekFirst() != null && counter < 50) {
            ImmutableDatagramPacket p = queue.removeFirst();

            if (p != null) {
                dsocket.send(p.getDatagramPacket());
                counter++;
            } //grrr..
        }
    }

    private class SocketRunner implements Runnable {
        private volatile boolean endFlag = false;

        public void run() {
            int counter = 0;
            for (; counter < 3; counter++) {
                try (DatagramSocket dsocket = open(port)) {
                    usedPort = dsocket.getLocalPort();
                    LOGGER.info("Opened dsocket on UDP port " + usedPort + ".");

                    runMainLoop(dsocket);
                } catch (IOException ex) {
                    LOGGER.info("Communication error on UDP port " + port
                            + " closing dsocket. counter: " + counter + " error: " + ex.getMessage());
                    try {
                        /*
                         * Wait a bit before trying again. This is to prevent a
                         * condition where there is an error because the
                         * previous instance of this AsyncDatagramSocket is
                         * still closing because it is closed by this thread
                         * asynchronously.
                         */
                        Thread.sleep(1000);
                    } catch (InterruptedException exception) {
                        exception.printStackTrace();
                    }
                }
            }
            //-Djava.util.logging.config.file=src/main/resources/logging.properties

            usedPort = -2;

            if (counter >= 3) {
                LOGGER.info("UDP port " + port + " giving up due to too many errors...");
            }
        }

        private void runMainLoop(DatagramSocket dsocket) throws IOException {
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
        }

        
        private void closingHook() {
            LOGGER.info("UDP port " + port + " is doing a happy close");
        }

        private static DatagramSocket open(int port) throws IOException {
            DatagramSocket dsocket = new DatagramSocket(port);
            dsocket.setSoTimeout(5);
            
            return dsocket;
        }

        public void flagToEnd() {
            endFlag = true;
        }
    }
}