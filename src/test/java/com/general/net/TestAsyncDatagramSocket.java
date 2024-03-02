package com.general.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import com.general.thread.AsyncContext;
import com.general.thread.PromiseFuture;

/**
 * Test of {@link AsyncDatagramSocket}.
 */
public class TestAsyncDatagramSocket {

    /**
     * We're testing the following scenario:
     * 
     * 1. Open a socket 
     * 2. Send a packet to the socket 
     * 3. Make sure the packet is received on the listener 
     * 4. Close the socket
     */
    @Test
    void testRecievePacketsOnListenerUsingInvoker()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        AsyncDatagramSocket asyncDatagramSocket = new AsyncDatagramSocket(0);
        int usedPort;

        usedPort = asyncDatagramSocket.getUsedPort();

        long waitForBindStartTime = System.currentTimeMillis();
        // wait for the port to be assigned
        while (usedPort < 0) {
            if (System.currentTimeMillis() - waitForBindStartTime > 5000) {
                throw new RuntimeException("socket didn't bind");
            }
            usedPort = asyncDatagramSocket.getUsedPort();
        }

        /*
         * now we need to allocate another dsocket to send packets to the socket
         * we just allocated. We need to make sure it's allocated on the local
         * host
         */
        try (DatagramSocket dsocket = new DatagramSocket(null)) {
            dsocket.bind(new InetSocketAddress("localhost", 0));

            /*
             * attach a mock to the listener so we can tell if it has been
             * called
             */
            PromiseFuture<ImmutableDatagramPacket> packetFuture = PromiseFuture
                    .newPromiseFuture((AsyncContext<ImmutableDatagramPacket> context) -> {
                        asyncDatagramSocket.setPortListener((p) -> context.setResult(p));
                    }).useEdt();


            // the dsocket
            // We want there to be a payload so we can test that the payload is
            // correct
            byte[] payload = new byte[] { 1, 2, 3, 4, 5 };
            DatagramPacket datagramPacket =
                    new ImmutableDatagramPacket(InetAddress.getByName("localhost"),
                                                usedPort,
                                                payload).getDatagramPacket();
            dsocket.send(datagramPacket);

            /* get() the result of the PromiseFuture with some timeout */
            ImmutableDatagramPacket result = packetFuture.get(30, TimeUnit.SECONDS);
            
            /* Assert the payload is what we expect. Namely the byte array above */
            byte[] expected = payload;
            byte[] actual = result.getData();
            assertArrayEquals(expected, actual);
        }

        asyncDatagramSocket.close();
        
        /* wait for the socket to close */
        long startTime = System.currentTimeMillis();
        while (asyncDatagramSocket.getUsedPort() != -2) {
            if (System.currentTimeMillis() - startTime > 5000) {
                throw new RuntimeException("socket didn't close");
            }
        }
    }

    /**
     * We're testing the following scenario:
     * 
     * 1. Open a socket
     * 2. Close the socket
     * 3. Open a socket on the same port
     * 4. Make sure the socket is opened on the same port
     * 5. Make sure the first socket is closed
     * 
     * The reason is there's a race condition here where the first socket might not be closed when the second socket is opened.
     * As a result the second socket must be resilient to the first socket still being open. It does this by retrying the bind
     * 3 times with s delay to make sure the first socket is closed.
     * @throws InterruptedException
     */
   @Test
    void testSocketOpensWhenPrevSocketCloses() throws InterruptedException {
        AsyncDatagramSocket asyncDatagramSocket = new AsyncDatagramSocket(0);
        int usedPort;

        usedPort = asyncDatagramSocket.getUsedPort();

        long waitForBindStartTime = System.currentTimeMillis();
        // wait for the port to be assigned
        while (usedPort < 0) {
            if (System.currentTimeMillis() - waitForBindStartTime > 5000) {
                throw new RuntimeException("socket didn't bind");
            }
            Thread.sleep(1);
            usedPort = asyncDatagramSocket.getUsedPort();
        }

        asyncDatagramSocket.close();

        AsyncDatagramSocket asyncDatagramSocket2 = new AsyncDatagramSocket(usedPort);
                long waitForBindStartTime2 = System.currentTimeMillis();
                
        // wait for the port to be assigned
        while (asyncDatagramSocket2.getUsedPort() < 0) {
            if (System.currentTimeMillis() - waitForBindStartTime2 > 5000) {
                throw new RuntimeException("socket didn't bind");
            }
        }

        assertEquals(usedPort, asyncDatagramSocket2.getUsedPort());
        assertEquals(-2, asyncDatagramSocket.getUsedPort());

        asyncDatagramSocket2.close();

        /* wait for the socket to close */
        long startTime = System.currentTimeMillis();
        while (asyncDatagramSocket2.getUsedPort() != -2) {
            if (System.currentTimeMillis() - startTime > 5000) {
                throw new RuntimeException("socket didn't close");
            }
        }
    }

    @Test
    void testAsyncDatagramSocketSendPacket()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        AsyncDatagramSocket asyncDatagramSocket = new AsyncDatagramSocket(0);
        int usedPort;

        usedPort = asyncDatagramSocket.getUsedPort();

        long waitForBindStartTime = System.currentTimeMillis();
        // wait for the port to be assigned
        while (usedPort < 0) {
            if (System.currentTimeMillis() - waitForBindStartTime > 5000) {
                throw new RuntimeException("socket didn't bind");
            }
            usedPort = asyncDatagramSocket.getUsedPort();
        }

        /*
         * now we need to allocate another dsocket to send packets to the socket
         * we just allocated. We need to make sure it's allocated on the local
         * host
         */
        byte[] payload = new byte[] { 1, 2, 3, 4, 5 };
        DatagramPacket receivedPacket = new DatagramPacket(new byte[5], 5);
        try (DatagramSocket dsocket = new DatagramSocket(null)) {
            dsocket.bind(new InetSocketAddress("localhost", 0));

            dsocket.setSoTimeout(1000);

            // now send some data that we can get on the dsocket
            ImmutableDatagramPacket datagramPacket =
                    new ImmutableDatagramPacket(InetAddress.getByName("localhost"),
                                                dsocket.getLocalPort(),
                                                payload);

            asyncDatagramSocket.sendPacket(datagramPacket);

            dsocket.receive(receivedPacket);
        }
        asyncDatagramSocket.close();

        assertArrayEquals(payload, receivedPacket.getData());
    }
}