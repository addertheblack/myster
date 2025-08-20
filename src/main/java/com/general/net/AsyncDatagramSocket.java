package com.general.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.general.thread.Invoker;

public final class AsyncDatagramSocket {
    private static final Logger LOGGER = Logger.getLogger(AsyncDatagramSocket.class.getName());
    
    private static final int BIG_BUFFER = 65536;
    
    private final Deque<ImmutableDatagramPacket> queue = new ConcurrentLinkedDeque<>();
    private final SocketRunner socketRunner;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(BIG_BUFFER);
    private final int port;
    private final Invoker invoker = Invoker.newVThreadInvoker();

    private volatile int usedPort = -1;
    
    /** This is accessed by the thread. All call backs to this listener happen on the invoker */
    private volatile AsyncDatagramListener portListener;

    private volatile Selector selector;
    
    public AsyncDatagramSocket(int port) {
        this.port = port;
        socketRunner = new SocketRunner();
        Executors.newVirtualThreadPerTaskExecutor().execute(socketRunner);
    }

    public void setPortListener(AsyncDatagramListener listener) {
        this.portListener = listener;
        
        if (selector!=null) {
            selector.wakeup();
        }
    }

    public int getUsedPort() {
        return usedPort;
    }

    public void sendPacket(ImmutableDatagramPacket packet) {
        queue.addLast(packet);
        
        if (selector!=null) {
            selector.wakeup();
        }
    }

    public void close() {
        socketRunner.flagToEnd();
    }

    private class SocketRunner implements Runnable {
        private volatile boolean endFlag = false;

        @Override
        public void run() {
            try {
                int counter = 0;
                
                counter = loop(counter);
                
                final int p = usedPort;
                usedPort = -2;

                if (counter >= 3) {
                    LOGGER.fine("Closing AsyncDatagramSocket on " + p
                            + " giving up due to too many errors...");
                }

                LOGGER.fine("Closing AsyncDatagramSocket on " + p + "...");
            } finally {
                invoker.shutdown();
            }
        }

        private int loop(int counter) {
            for (; counter < 3; counter++) {
                try (DatagramChannel channel = DatagramChannel.open();
                        Selector s = Selector.open()) {
                    selector = s;
                    
                    // It looks like I might be flooding the output buffer..
                    // Need to double check to see if that's really the case
                    channel.setOption(StandardSocketOptions.SO_SNDBUF, 128 * 1024);
//                    channel.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024);
                    
                    channel.bind(new InetSocketAddress(port));
                    channel.configureBlocking(false);
                    channel.register(selector, SelectionKey.OP_READ);
                    usedPort = ((InetSocketAddress) channel.getLocalAddress()).getPort();
                    LOGGER.fine("Opened DatagramChannel on UDP port " + usedPort
                            + (port != usedPort ? " (a random port) " : ""));

                    mainLoop(channel);

                    // OK, so we've completed without error.. Skip the rest
                    // of the loop and end.
                    break;
                } catch (IOException ex) {
                    LOGGER.warning("Failed to open DatagramChannel on port " + port + ": "
                            + ex.getMessage());
                    usedPort = -2;

                    try {
                        /*
                         * Wait a bit before trying again. This is to
                         * prevent a condition where there is an error
                         * because the previous instance of this
                         * AsyncDatagramSocket is still closing because it
                         * is closed by this thread asynchronously.
                         */
                        Thread.sleep(10 * (long) Math.pow(10, counter));
                    } catch (InterruptedException exception) {
                        exception.printStackTrace();
                    }
                }
            }
            return counter;
        }

        private void mainLoop(DatagramChannel channel) throws IOException {
            // this is to avoid a race condition where the selector might not be
            // inited by the time the first packet arrives to be sent.
            // If that happens we might receive a packet but not have a selector to
            // notify on.
            while (!endFlag) {
                // this is to avoid a race condition where the selector might not be
                // inited by the time the first packet arrives to be sent.
                // If that happens we might receive a packet but not have a selector to
                // notify on. If there's nothing on the queue it's harmless
                sendPackets(channel);
                readPackets(channel);
                
                selector.select();
                selector.selectedKeys().clear();
            }
        }

        private void readPackets(DatagramChannel channel) throws IOException {
            if (portListener == null) {
                return;
            }
            
            for (;;) {
                readBuffer.clear();
                InetSocketAddress sourceAddress = (InetSocketAddress) channel.receive(readBuffer);
                if (sourceAddress == null) {
                    return;
                }
                readBuffer.flip();
                byte[] data = new byte[readBuffer.remaining()];
                readBuffer.get(data);
                ImmutableDatagramPacket packet =
                        new ImmutableDatagramPacket(sourceAddress.getAddress(), port, data);
                
                // switch threads - it's important we don't do the callback on this thread
                invoker.invoke(() -> portListener.packetReceived(packet));
            }
        }

        private void sendPackets(DatagramChannel channel) throws IOException {
            while (!queue.isEmpty() && !endFlag) {
                ImmutableDatagramPacket packet = queue.poll();
                if (packet != null) {
                    ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
                    channel.send(buffer, packet.getSocketAddress());
                }
            }
        }

        public void flagToEnd() {
            LOGGER.fine("Requesting AsyncDatagramSocket on " + port + " to close...");
            endFlag = true;

            if (selector != null) {
                selector.wakeup();
            }
        }
    }
}
