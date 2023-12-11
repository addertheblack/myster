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

import com.general.util.AnswerDialog;
import com.general.util.LinkedList;
import com.general.util.Util;

public final class AsyncDatagramSocket {
    private AsyncDatagramListener portListener;

    private DatagramSocket dsocket;

    private ManagerThread managerThread;

    private LinkedList queue = new LinkedList();

    private int port;

    private DatagramPacket bufferPacket;

    private static final int BIG_BUFFER = 65536;

    public AsyncDatagramSocket(int port) throws IOException {
        this.port = port;

        open(port);

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

    private void open(int port) throws IOException {
        dsocket = new DatagramSocket(port);
        dsocket.setSoTimeout(5);
    }

    public void close() {
        dsocket.close();

        //managerThread.end();
    }

    private void doGetNewPackets() throws IOException {
        int counter = 0;
        long startTime = System.currentTimeMillis();
        while (true) { //System.currentTimeMillis() - startTime < 50) {
            try {
                bufferPacket.setLength(BIG_BUFFER);

                dsocket.receive(bufferPacket);

                if (portListener != null) {
                    Util.invokeLater(new PrivateRunnable(new ImmutableDatagramPacket(bufferPacket)));
                }
            } catch (InterruptedIOException ex) {
                return;
            }
            counter++;

        }
    }

    private void doSendNewPackets() throws IOException {
        int counter = 0;
        while (queue.getSize() > 0 && counter < 5) {
            ImmutableDatagramPacket p = (ImmutableDatagramPacket) (queue.removeFromHead());

            if (p != null) {
                dsocket.send(p.getDatagramPacket());
                counter++;
            } //grrr..
        }
    }

    private class ManagerThread extends Thread {
        boolean endFlag = false;

        public void run() {
            for (;;) {
                if (endFlag)
                    return;

                try {
                    doGetNewPackets();
                } catch (IOException ex) {
                    //close();
                    ex.printStackTrace();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                if (endFlag)
                    return;

                try {
                    doSendNewPackets();
                } catch (IOException exp) {
                    close();
                    try {
                        open(port);
                        //com.general.util.AnswerDialog.simpleAlert("The UDP
                        // sub-system was acting up. It has been restarted
                        // successfully. If this message appears frequently, ");
                    } catch (Exception ex) {
                        AnswerDialog
                                .simpleAlert("The UDP sub-system crashed and could not be re-started. Consider restarting the computer. Myster can not work properly without a UDP sub-system.");
                        end();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        public void end() {
            endFlag = true;
        }
    }

    private class PrivateRunnable implements Runnable {
        ImmutableDatagramPacket packet;

        PrivateRunnable(ImmutableDatagramPacket packet) {
            this.packet = packet;
        }

        public void run() {
            portListener.packetReceived(packet);
        }
    }
}