/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.server;

import com.myster.client.stream.MysterDataInputStream;
import com.myster.filemanager.FileTypeListManager;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.myster.net.MysterAddress;
import com.myster.server.event.ConnectionManagerEvent;
import com.myster.server.event.OperatorEvent;
import com.myster.server.event.ServerEventDispatcher;
import com.myster.server.transferqueue.TransferQueue;

/**
 * This class takes incoming stream connections and applies the Myster protocol
 * to them. The incomming streams are gotten by waiting on a BlockingQueue
 * object. The ConnectionManager object is made to run as a thread and accessing
 * sockets through a BlockingQueue allows the ConnectionManager to block until a
 * new stream is available. It also allows MULTIPLE ConnectionSection objects to
 * wait on a single BlockingQueue and form a connection pool.
 * <p>
 * The Myster protocol for stream is made up up connection sections. This class
 * gets the connection and waits for a connection number. When the connection
 * number has been received it then returns 1 or 0 depending on whether or not
 * it has a connection section handle installed that can understand that
 * protocol. Connection sections are passed to this object in its constructor.
 * 
 * 
 * @author Andrew Trumper
 *  
 */

public class ConnectionRunnable implements Runnable {
    private final ServerEventDispatcher eventSender;
    private final TransferQueue transferQueue;
    private final Map<Integer, ConnectionSection> connectionSections;
    private final Socket socket;
    private final FileTypeListManager fileManager;
    
    private static final AtomicInteger threadCounter = new AtomicInteger(0);

    /**
     * Builds a connection section object.
     * 
     * @param socketQueue
     *            a DoubleBlockingQueue containing sockets that should be
     *            handled.
     * @param eventSender
     *            the event dispatcher object
     * @param transferQueue
     *            the TransferQueue object to use for queuing downloads
     * @param connectionSections
     *            a Hashtable of connection section integers to
     *            ConnectionSection objects
     */
    protected ConnectionRunnable(Socket socket,
                                 ServerEventDispatcher eventSender,
                                 TransferQueue transferQueue,
                                 FileTypeListManager fileTypeListManager,    
                                 Map<Integer, ConnectionSection> connectionSections) {
        Thread.currentThread().setName("Server Thread " + (threadCounter.incrementAndGet()));

        this.socket = socket;
        this.transferQueue = transferQueue;
        this.eventSender = eventSender;
        this.connectionSections = connectionSections;
        this.fileManager = fileTypeListManager;
    }

    /**
     * Does the actual work in this object.
     *  
     */
    public void run() {
        eventSender.getOperationDispatcher().fire()
                .connectEvent(new OperatorEvent(new MysterAddress(socket.getInetAddress())));

        int sectioncounter = 0;
        try (var tempTcpSocket = new com.myster.client.stream.TCPSocket(socket)) {
            ConnectionContext context = new ConnectionContext(tempTcpSocket, new MysterAddress(socket.getInetAddress()), null, transferQueue, fileManager);
                    

            MysterDataInputStream i = context.socket().in; //opens the connection

            int protocalcode;
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            do {
                try {
                    Thread.yield();
                    protocalcode = i.readInt(); //reads the type of connection
                    // requested
                } catch (Exception ex) {
                    Thread.yield();
                    return;
                }

                sectioncounter++; //to detect if it was a ping.

                //Figures out which object to invoke for the connection type:
                //NOTE: THEY SAY RUN() NOT START()!!!!!!!!!!!!!!!!!!!!!!!!!
                MysterAddress remoteip = new MysterAddress(socket.getInetAddress());

                switch (protocalcode) {
                case 1:
                    context.socket().out.write(1); //Tells the other end that the
                    // command is good  !
                    break;
                case 2:
                    context.socket().out.write(1); //Tells the other end that the
                    // command is good  !
                    return;
                default:
                    ConnectionSection section = connectionSections
                            .get(protocalcode);
                    if (section == null) {
                        System.out
                                .println("!!!System detects unknown protocol number : "
                                + protocalcode);
                        context.socket().out.write(0); //Tells the other end that
                        // the command is bad!
                    } else {
                        doSection(section, remoteip, context);
                    }
                }
            } while (true);
        } catch (IOException ex) {
            // nothing
        } finally {
            if (sectioncounter == 0) {
                eventSender.getOperationDispatcher().fire().pingEvent(new OperatorEvent(new MysterAddress(
                        socket.getInetAddress())));
            }

            eventSender.getOperationDispatcher().fire().disconnectEvent(new OperatorEvent(new MysterAddress(
                    socket.getInetAddress())));
            // socket already closed here
        }

    }

    /**
     * Used to turn a REALLY long line of code into a smaller long line of code.
     */
    private void fireConnectEvent(ConnectionSection d, MysterAddress remoteAddress, Object o) {
        eventSender.getConnectionDispatcher().fire().sectionEventConnect(new ConnectionManagerEvent(remoteAddress, d.getSectionNumber(), o));
    }

    /**
     * Used to turn a REALLY long line of code into a smaller long line of code.
     */
    private void fireDisconnectEvent(ConnectionSection d, MysterAddress remoteAddress, Object o) {
        eventSender.getConnectionDispatcher().fire()
                .sectionEventDisconnect(new ConnectionManagerEvent(remoteAddress,
                                                                   d.getSectionNumber(),
                                                                   o));
    }

    private void doSection(ConnectionSection d, MysterAddress remoteIP, ConnectionContext context)
            throws IOException {
        Object sectionObject = d.getSectionObject();
        fireConnectEvent(d, remoteIP, sectionObject);
        try {
            d.doSection(context.withSectionObject(sectionObject));
        } finally {
            fireDisconnectEvent(d, remoteIP, sectionObject);
        }
    }
}