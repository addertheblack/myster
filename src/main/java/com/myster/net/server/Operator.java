/* 

 Title:         Myster Open Source
 Author:        Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.net.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.general.util.Timer;

/**
 * This class is responsible for "picking up the phone" or making a TCP
 * connection with clients. After the connection has been made it's up to the
 * connection manager to figure out what kind o of service to employ and to
 * manage the different connection sections.
 */
public class Operator implements Runnable {
    private static final Logger log = Logger.getLogger(Operator.class.getName());
    
    private final Consumer<Socket> socketConsumer; //Communication CHANNEL.
    private final int port;

    private ServerSocket serverSocket;
    private volatile Timer timer;
    private volatile boolean endFlag = false;

    private final Optional<InetAddress> bindAddress;

    protected Operator(Consumer<Socket> socketConsumer, int port, Optional<InetAddress> bindAddress) {
        Thread.currentThread().setName("Operator Thread port: " + port);
        
        this.bindAddress = bindAddress;
        this.socketConsumer = socketConsumer;
        this.port = port;
    }

    /**
     * Signals this operator to stop accepting new connections.
     * The server socket is closed which causes the accept() call to throw an
     * IOException, breaking out of the accept loop. Existing connections that
     * were already accepted continue to be processed - they are not affected.
     * <p>
     * This method is idempotent - calling it multiple times has no additional effect.
     */
    public void flagToEnd() {
        endFlag = true;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.fine("Exception closing server socket during flagToEnd: " + e.getMessage());
        }
        if (timer != null) {
            timer.cancelTimer();
        }
    }

    public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY); //to minimize the time it takes to make a
        // connection.
        Thread.currentThread().setName("Operator Thread port: " + port);

        refreshServerSocket(); //creates the sever socket.

        resetSocketTimer();

        Socket socket = null;
        while (!endFlag) {
            try {
                socket = serverSocket.accept();
                socket.setSoTimeout(120000);
                socketConsumer.accept(socket);

                resetSocketTimer();
            } catch (IOException ex) {
                if (endFlag) {
                    // Normal shutdown - we were flagged to end
                    log.info("Operator on port " + port + " shutting down");
                    break;
                }
                try {
                    socket.close();
                } catch (Exception exp) {
                }
                synchronized (this) { //synchronized in case the socket is
                    // being re-set.
                    try {
                        Thread.sleep(100); //sometimes the OS will crash in such a way
                        // that it supplies an
                        //infinite number of brokens sockets.
                        //This is here so that the system remains responsive
                        // during that time.
                    } catch (InterruptedException exp) {
                    }
                }
            }
        }
        log.info("Operator on port " + port + " has stopped");
    }
    //Creates or recreates a new server socket.
    private synchronized void refreshServerSocket() {
        for (;;) {
            try {
                if (serverSocket != null)
                    try {
                        serverSocket.close();
                    } catch (IOException _) {
                    }

                serverSocket = new ServerSocket(getPort(), 512, bindAddress.orElse(null)); //bigger buffer
                break;
            } catch (IOException _) {
                try {
                    Thread.sleep(10 * 1000);
                } catch (InterruptedException exp) {
                } //wait 10 seconds then try to make the socket again.
            }
        }
    }

    public void resetSocketTimer() {
        if (timer != null) {
            timer.cancelTimer();
        }

        timer = new Timer(new Runnable() {
            public void run() {
                log.info("RESETING THE CONNECTION");
                refreshServerSocket();
            }
        }, 10 * 60 * 1000); //ms -> seconds -> minutes 10 minutes.
    }

    public int getPort() {
        return port;
    }
}