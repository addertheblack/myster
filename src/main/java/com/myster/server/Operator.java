/* 

 Title:         Myster Open Source
 Author:        Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

import com.general.util.Timer;

/**
 * This class is responsible for "picking up the phone" or making a TCP
 * connection with clients. After the connection has been made it's up to the
 * connection manager to figure out what kind o of service to employ and to
 * manage the different connection sections.
 */
public class Operator implements Runnable {
    private final Consumer<Socket> socketConsumer; //Communication CHANNEL.
    private final int port;

    private ServerSocket serverSocket;
    private volatile Timer timer;

    protected Operator(Consumer<Socket> socketConsumer, int port) {
        Thread.currentThread().setName("Operator Thread port: " + port);
        this.socketConsumer = socketConsumer; //Communications channel
        // between operator and
        // section threads.
        this.port = port;
    }
        
    public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY); //to minimize the time it takes to make a
        // connection.

        refreshServerSocket(); //creates the sever socket.

        resetSocketTimer();

        Socket socket = null;
        do {
            try {
                socket = serverSocket.accept();
                socket.setSoTimeout(120000);
                socketConsumer.accept(socket);

                resetSocketTimer();
            } catch (IOException ex) {
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
        } while (true);
    }
    //Creates or recreates a new server socket.
    private synchronized void refreshServerSocket() {
        for (;;) {
            try {
                if (serverSocket != null)
                    try {
                        serverSocket.close();
                    } catch (IOException ex) {
                    }

                serverSocket = new ServerSocket(getPort(),
                        5); //bigger buffer
                break;
            } catch (IOException ex) {
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
                System.out.println("RESETING THE CONNECTION");
                refreshServerSocket();
            }
        }, 10 * 60 * 1000); //ms -> seconds -> minutes 10 minutes.
    }

    public int getPort() {
        return port;
    }
}