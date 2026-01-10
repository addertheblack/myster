/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2025
 */

package com.myster.net.server;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Identity;
import com.myster.net.MysterAddress;
import com.myster.net.TLSSocket;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.server.transferqueue.TransferQueue;
import com.myster.server.event.ConnectionManagerEvent;
import com.myster.server.event.OperatorEvent;
import com.myster.server.event.ServerEventDispatcher;

/**
 * Handles incoming stream connections by applying the Myster protocol.
 * <p>
 * This class processes incoming socket connections as a Runnable, reading protocol
 * codes and delegating to appropriate ConnectionSection handlers. It supports
 * connection pooling by allowing multiple instances to process connections
 * concurrently.
 * <p>
 * The Myster protocol consists of connection sections identified by integer codes.
 * When a protocol code is received, this class either handles it directly (for
 * built-in protocols like TLS) or delegates to registered ConnectionSection
 * implementations.
 * 
 * @author Andrew Trumper
 */

public class ConnectionRunnable implements Runnable {
    private static final Logger log = Logger.getLogger(ConnectionRunnable.class.getName());
    
    private final ServerEventDispatcher eventSender;
    private final TransferQueue transferQueue;
    private final Map<Integer, ConnectionSection> connectionSections;
    private final Socket socket;
    private final FileTypeListManager fileManager;
    
    /** Counter for generating unique thread names */
    private static final AtomicInteger threadCounter = new AtomicInteger(0);

    private final Identity identity;

    /**
     * Creates a new connection handler for the specified socket.
     * 
     * @param socket the client socket to handle
     * @param eventSender the event dispatcher for connection events
     * @param transferQueue the queue for managing file transfers
     * @param fileTypeListManager the manager for file type operations
     * @param connectionSections map of protocol codes to their handlers
     */
    protected ConnectionRunnable(Socket socket,
                                 Identity identity,
                                 ServerEventDispatcher eventSender,
                                 TransferQueue transferQueue,
                                 FileTypeListManager fileTypeListManager,    
                                 Map<Integer, ConnectionSection> connectionSections) {
        this.identity = identity;
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
     * 
     * @param value the integer value to convert
     * @return ASCII representation of the integer bytes
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
            // Only include printable ASCII characters (32-126)
            if (b >= 32 && b <= 126) {
                sb.append((char) b);
            } else {
                sb.append('?'); // Placeholder for non-printable characters
            }
        }
        return sb.toString();
    }

    /**
     * Processes the client connection by reading protocol codes and dispatching
     * to appropriate handlers. This method implements the main Myster protocol
     * processing loop.
     */
    public void run() {
        eventSender.getOperationDispatcher().fire()
                .connectEvent(new OperatorEvent(new MysterAddress(socket.getInetAddress())));

        int sectionCounter = 0;
        try (var tempTcpSocket = new com.myster.net.stream.client.TCPSocket(socket)) {
            ConnectionContext context = new ConnectionContext(tempTcpSocket, new MysterAddress(socket.getInetAddress()), null, transferQueue, fileManager);

            MysterDataInputStream inputStream = context.socket().in;

            int protocolCode;
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            do {
                try {
                    protocolCode = inputStream.readInt();
                    
                    // Log the protocol code with multiple representations for debugging
                    String asciiRepresentation = intToAsciiString(protocolCode);
                    log.fine("Protocol code received: " + protocolCode + 
                                     " (0x" + Integer.toHexString(protocolCode).toUpperCase() + 
                                     ") ASCII: \"" + asciiRepresentation + "\"");
                } catch (Exception _) {
                    return;
                }

                sectionCounter++; // Track sections to detect ping connections

                MysterAddress remoteAddress = new MysterAddress(socket.getInetAddress());

                switch (protocolCode) {
                case TLSSocket.STLS_CONNECTION_SECTION:
                    log.fine("Client requested STLS (Start TLS) connection section");
                    try {
                        // Send acceptance response (1 = success in Myster protocol)
                        context.socket().out.write(1);
                        context.socket().out.flush();
                        
                        TLSSocket tlsSocket = TLSSocket.upgradeServerSocket(socket, identity);
                        
                        // Update context to use encrypted connection
                        context = new ConnectionContext(tlsSocket, context.serverAddress(), context.sectionObject(), transferQueue, fileManager);
                        inputStream = context.socket().in;
                        
                        log.fine("STLS upgrade successful - connection is now encrypted");
                        break;
                        
                    } catch (Exception e) {
                        log.warning("Failed to upgrade to TLS: " + e.getMessage());
                        
                        // Send rejection (0 = failure in Myster protocol)
                        context.socket().out.write(0);
                        context.socket().out.flush();
                        return;
                    }
                case 1:
                    // Basic acknowledgment protocol
                    context.socket().out.write(1);
                    context.socket().out.flush();
                    break;
                case 2:
                    // Acknowledgment and disconnect protocol
                    context.socket().out.write(1);
                    context.socket().out.flush();
                    return;
                default:
                    ConnectionSection section = connectionSections.get(protocolCode);
                    if (section == null) {
                        String asciiRepresentation = intToAsciiString(protocolCode);
                        log.warning("System detects unknown protocol number: " + protocolCode + 
                                     " (0x" + Integer.toHexString(protocolCode).toUpperCase() + 
                                     ") ASCII: \"" + asciiRepresentation + "\"");
                        context.socket().out.write(0); // Send rejection for unknown protocol
                    } else {
                        doSection(section, remoteAddress, context);
                    }
                }
            } while (true);
        } catch (IOException ex) {
            // Connection terminated normally or by error
        } finally {
            if (sectionCounter == 0) {
                eventSender.getOperationDispatcher().fire().pingEvent(new OperatorEvent(new MysterAddress(
                        socket.getInetAddress())));
            }

            eventSender.getOperationDispatcher().fire().disconnectEvent(new OperatorEvent(new MysterAddress(
                    socket.getInetAddress())));
        }
    }

    /**
     * Fires a connection event for the specified section.
     * 
     * @param section the connection section
     * @param remoteAddress the remote client address
     * @param sectionObject the section-specific data object
     */
    private void fireConnectEvent(ConnectionSection section, MysterAddress remoteAddress, Object sectionObject) {
        eventSender.getConnectionDispatcher().fire().sectionEventConnect(new ConnectionManagerEvent(remoteAddress, section.getSectionNumber(), sectionObject));
    }

    /**
     * Fires a disconnection event for the specified section.
     * 
     * @param section the connection section
     * @param remoteAddress the remote client address  
     * @param sectionObject the section-specific data object
     */
    private void fireDisconnectEvent(ConnectionSection section, MysterAddress remoteAddress, Object sectionObject) {
        eventSender.getConnectionDispatcher().fire()
                .sectionEventDisconnect(new ConnectionManagerEvent(remoteAddress,
                                                                   section.getSectionNumber(),
                                                                   sectionObject));
    }

    /**
     * Executes a connection section with proper event handling.
     * 
     * @param section the connection section to execute
     * @param remoteAddress the remote client address
     * @param context the connection context
     * @throws IOException if an I/O error occurs during section execution
     */
    private void doSection(ConnectionSection section, MysterAddress remoteAddress, ConnectionContext context)
            throws IOException {
        Object sectionObject = section.getSectionObject();
        fireConnectEvent(section, remoteAddress, sectionObject);
        try {
            section.doSection(context.withSectionObject(sectionObject));
        } finally {
            fireDisconnectEvent(section, remoteAddress, sectionObject);
        }
    }
}