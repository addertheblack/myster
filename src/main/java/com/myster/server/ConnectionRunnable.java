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
import com.myster.identity.Identity;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.myster.net.MysterAddress;
import com.myster.net.TLSSocket;
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
     * Converts a 32-bit integer to its ASCII string representation in network byte order.
     * Non-printable characters are replaced with '?'.
     */
    private static String intToAsciiString(int value) {
        // Extract bytes in big-endian (network) order
        byte[] bytes = new byte[4];
        bytes[0] = (byte) ((value >>> 24) & 0xFF);  // Most significant byte
        bytes[1] = (byte) ((value >>> 16) & 0xFF);
        bytes[2] = (byte) ((value >>> 8) & 0xFF);
        bytes[3] = (byte) (value & 0xFF);           // Least significant byte
        
        // Convert to ASCII string
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            // Only add printable ASCII characters (32-126)
            if (b >= 32 && b <= 126) {
                sb.append((char) b);
            } else {
                sb.append('?'); // Non-printable character placeholder
            }
        }
        return sb.toString();
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

            int protocolCode;
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            do {
                try {
                    protocolCode = i.readInt(); //reads the type of connection
                    // requested
                    
                    // Log the protocol code as both integer and ASCII
                    String asciiRepresentation = intToAsciiString(protocolCode);
                    System.out.println("Protocol code received: " + protocolCode + 
                                     " (0x" + Integer.toHexString(protocolCode).toUpperCase() + 
                                     ") ASCII: \"" + asciiRepresentation + "\"");
                } catch (Exception ex) {
                    return;
                }

                sectioncounter++; //to detect if it was a ping.

                //Figures out which object to invoke for the connection type:
                //NOTE: THEY SAY RUN() NOT START()!!!!!!!!!!!!!!!!!!!!!!!!!
                MysterAddress remoteip = new MysterAddress(socket.getInetAddress());

                switch (protocolCode) {
                case TLSSocket.STLS_CONNECTION_SECTION: // "STLS" - Handle TLS connection section
                    System.out.println("Client requested STLS (Start TLS) connection section");
                    try {
                        // Send acceptance response using Myster protocol (1 = good)
                        context.socket().out.write(1);
                        context.socket().out.flush();
                        
                        TLSSocket tlsSocket = TLSSocket.upgradeServerSocket(socket, Identity.getIdentity());
                        
                        // Update the context with the new encrypted socket
                        context = new ConnectionContext(tlsSocket, context.serverAddress(), context.sectionObject(), transferQueue, fileManager);
                        i = context.socket().in; // Update input stream to use encrypted connection
                        
                        System.out.println("STLS upgrade successful - connection is now encrypted");
                        
                        // Continue processing more protocol codes on encrypted connection
                        break;
                        
                    } catch (Exception e) {
                        System.out.println("Failed to upgrade to TLS: " + e.getMessage());
                        
                        // Send rejection using Myster protocol (0 = bad)
                        context.socket().out.write(0);
                        context.socket().out.flush();
                        return;
                    }
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
                            .get(protocolCode);
                    if (section == null) {
                        System.out
                                .println("!!!System detects unknown protocol number : "
                                + protocolCode);
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