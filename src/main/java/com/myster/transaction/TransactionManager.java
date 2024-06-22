package com.myster.transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import com.general.net.ImmutableDatagramPacket;
import com.general.util.Timer;
import com.general.util.Util;
import com.myster.net.BadPacketException;
import com.myster.net.DataPacket;
import com.myster.net.DatagramProtocolManager;
import com.myster.net.DatagramProtocolManager.TransportManager;
import com.myster.net.DatagramSender;
import com.myster.net.DatagramTransport;
import com.myster.net.MysterAddress;
import com.myster.server.event.ConnectionManagerEvent;
import com.myster.server.event.ServerEventDispatcher;

/**
 * The TransactionManager is responsible for dealing with lower level details involved with
 * implementing the Myster simple datagram "transaction" protocol.
 * 
 * TODO put in transaction protocol docs here.
 */
public class TransactionManager {
    private static final Logger LOGGER = Logger.getLogger(TransactionManager.class.getName());
    
    private final ServerEventDispatcher dispatcher;
    private final DatagramProtocolManager datagramManager;

    /**
     * Creates a TransactionManager which also has a TransactionManager implementation (which is
     * supposed to actually do the work).
     * 
     * @param dispatcher
     * @param datagramManager 
     */
    public TransactionManager(ServerEventDispatcher dispatcher, DatagramProtocolManager datagramManager) {
        this.dispatcher = dispatcher;
        this.datagramManager = datagramManager;
    }


    /**
     * Responsible for sending a transaction and notifying the listener if there is any reply or
     * timeout etc..
     * <p>
     * This function is for use by TransactionSocket only.
     * 
     * @see TransactionSocket
     * 
     * 
     * @param data
     *            DataPacket to send (protocol information is added to the information in this
     *            packet)
     * @param transactionCode
     *            of the remote datagram connection section to activate
     * @param listener
     *            to be notified upon events to do with this transaction
     * @return integer based ID to make references to this outstanding transaction. The id is no
     *         longer valid after the transaction has responded
     */
    public int sendTransaction(DataPacket data, int transactionCode, TransactionListener listener_in) {
        int port = data.getAddress().getPort();
        
        return datagramManager.accessPort(port, (transportManager) -> {
            TransactionTransportImplementation transactionTransport =
                    extractTransactionTransport(transportManager);
            
            TransactionListener listener = new TransactionListener() {
                
                @Override
                public void transactionTimout(TransactionEvent event) {
                    datagramManager
                            .accessPort(port,
                                        (transportManager) -> transportManager
                                                .removeTransportIfEmpty(transactionTransport));

                    listener_in.transactionTimout(event);
                }

                @Override
                public void transactionReply(TransactionEvent event) {
                    datagramManager
                            .accessPort(port,
                                        (transportManager) -> transportManager
                                                .removeTransportIfEmpty(transactionTransport));

                    listener_in.transactionReply(event);
                }

                @Override
                public void transactionCancelled(TransactionEvent event) {
                    datagramManager
                            .accessPort(port,
                                        (transportManager) -> transportManager
                                                .removeTransportIfEmpty(transactionTransport));

                    listener_in.transactionCancelled(event);
                }
            };
            
            return transactionTransport.sendTransaction(data, transactionCode, listener);
        });
    }

    private TransactionTransportImplementation extractTransactionTransport(TransportManager transportManager) {
        TransactionTransportImplementation transactionTransport =
                (TransactionTransportImplementation) transportManager
                        .getTransport(Transaction.TRANSACTION_PROTOCOL_NUMBER);
        
        if (transactionTransport == null) {
            transactionTransport = new TransactionTransportImplementation(transportManager::sendPacket, dispatcher);
            transportManager.addTransport(transactionTransport);
        }
        return transactionTransport;
    }
    

    /**
     * Cancels the outstanding transaction referenced by this id.
     * <p>
     * There's actually not much point in cancelling a transaction since the same result can be
     * obtained by ignoring the response of the server. The only difference is cancelling the
     * transaction means Myster can free up the resources occupied by the transaction as well as not
     * bother sending additional follow-up packets, which are usually sent if there is no
     * acknowlegement packet received.
     * <p>
     * This function is to be used only by TransactionSocket
     * 
     * @see TransactionSocket
     * 
     * @param id
     *            (internal) of the outstanding transaction to cancel.
     * @return true if transaction was cancelled, false otherwise.
     */
    public boolean cancelTransaction(int port, int id) {
        return datagramManager.accessPort(port, (TransportManager transportManager) -> {
            TransactionTransportImplementation transport =
                    (TransactionTransportImplementation) transportManager.getTransport(Transaction.TRANSACTION_PROTOCOL_NUMBER);
            
            return transport.cancelTransaction(id);
        });
    }

    /**
     * Adds a TransactionProtocol (server side). A TransactionProtocol is a server side event
     * listener for a specific transaction connection section.
     * <p>
     * 
     * @see TransactionProtocol
     * @param protocol
     *            listener to add.
     * @return the same TransactionProtocol but with a valid "sender"
     */
    public TransactionProtocol addTransactionProtocol(int port, TransactionProtocol protocol) {
        return datagramManager.accessPort(port, (TransportManager transportManager) -> {
            TransactionTransportImplementation transactionTransport =
                    extractTransactionTransport(transportManager);

            return transactionTransport.addTransactionProtocol(protocol);
        });
    }

    /**
     * This class implements the DatagramTransport which means it implements a listener for all
     * datagram packets with a TRANSACTION_PROTOCOL_NUMBER. This also means all outgoing
     * packets need to go through this object as this is the interface provided by
     * DatagramProtocolManager.
     * 
     * @see DatagramProtocolManager
     */
    private static class TransactionTransportImplementation extends DatagramTransport {
        private final Map<Integer, TransactionProtocol> serverProtocols = new HashMap<>();

        private final Map<Integer, ListenerRecord> outstandingTransactions = new HashMap<>();

        private final ServerEventDispatcher dispatcher;

        private final DatagramSender sender;

        /**
         * @param dispatcher
         */
        public TransactionTransportImplementation(DatagramSender sender, ServerEventDispatcher dispatcher) {
            this.sender = sender;
            this.dispatcher = dispatcher;
        }

        public boolean isEmpty() {
            return serverProtocols.isEmpty() && outstandingTransactions.isEmpty();
        }

        public short getTransportCode() {
            return Transaction.TRANSACTION_PROTOCOL_NUMBER;
        }

        @Override
        public void packetReceived(DatagramSender sender, ImmutableDatagramPacket immutablePacket)
                throws BadPacketException {
            Transaction transaction = new Transaction(immutablePacket);

            if (transaction.isForClient()) {
                fireEvents(transaction.getConnectionNumber(), transaction);
            } else {
                TransactionProtocol protocol = serverProtocols
                        .get(transaction.getTransactionCode());

                if (protocol == null) {
                    LOGGER.info("No Transaction protocol registered under type: "
                            + transaction.getTransactionCode());

                    sendTransaction(sender, new Transaction(transaction, new byte[0],
                            Transaction.TRANSACTION_TYPE_UNKNOWN));

                    return;
                }
                Object transactionObject = protocol.getTransactionObject();
                dispatcher.getConnectionDispatcher().fire()
                        .sectionEventConnect(new ConnectionManagerEvent(transaction.getAddress(),
                                                                        transaction
                                                                                .getTransactionCode(),
                                                                        transactionObject,
                                                                        true));
                protocol.transactionReceived((t) -> sendTransaction(sender, t), transaction, transactionObject); //fun...
            }
        }

        public int sendTransaction(DataPacket packet, int transactionCode,
                TransactionListener listener) {
            int uniqueid = getNextID();

            Transaction transaction = new Transaction(packet.getAddress(), transactionCode,
                    uniqueid, packet.getData());

            outstandingTransactions.put(uniqueid, new ListenerRecord(packet
                    .getAddress(), uniqueid, listener, new TimeoutTimer(uniqueid, transaction)));

            sender.sendPacket(transaction.toImmutableDatagramPacket());

            return uniqueid;
        }

        /**
         * Cancels the transaction pointed to by this internal id. This routine blocks until the
         * event is cancelled but sends the cancelled event asynchronously to the transaction
         * listeners.
         * 
         * 
         * @param uniqueId
         * @return true if cancelled.
         */
        public boolean cancelTransaction(int uniqueId) {
            final ListenerRecord record = outstandingTransactions
                    .remove(uniqueId);

            if (record == null)
                return false;

            record.timer.cancleTimer();

            /*
             * Events should be on the event thread. We can't use the standard fireEvents routine in
             * this object because the routine assumes that it's called on the event thread. We also
             * couldn't just bundle up the code here in a runnable and send it to the event thread
             * because those runnable are done asynchronously and we need to return if the
             * transaction was cancelled,
             * 
             * We could do it in a synchronous call to the event thread.. but that could cause
             * confusing deadlocks (and harder to write code). There's also no reason for it when we
             * can cancel the event THEN asynchronously update all listeners. It's a feature.
             */
            Util.invokeLater(new Runnable() {
                public void run() {
                    record.listener.fireEvent(new TransactionEvent(TransactionEvent.CANCELLED,
                            System.currentTimeMillis() - record.timeStamp, record.address, null));
                }
            });

            return true;
        }

        private void sendTransaction(DatagramSender sender, Transaction transaction) {
            if (!transaction.isForClient()) {
                throw new IllegalStateException("transaction is for client");
            }
            sender.sendPacket(transaction.toImmutableDatagramPacket());
        }

        /**
         * If you want to handle transactions as a server.
         */
        public TransactionProtocol addTransactionProtocol(TransactionProtocol protocol) {
            return serverProtocols.put(protocol.getTransactionCode(), protocol);
        }

        /*
         * Not all events are sent through here. Cancelled events aren't sent here.
         */
        private void fireEvents(int uniqueid, Transaction transaction) {
            ListenerRecord record = outstandingTransactions.remove(
                    uniqueid);

            if (record == null)
                return; //minor err

            record.timer.cancleTimer();

            //if it's not from the right address ignore.. Anti-spoofing
            if (transaction != null) {
                if (!transaction.getAddress().equals(record.address))
                    return;
            }

            record.listener.fireEvent(new TransactionEvent(
                    (transaction == null ? TransactionEvent.TIMEOUT : TransactionEvent.REPLY),
                    System.currentTimeMillis() - record.timeStamp, record.address, transaction));
        }

        int idCounter = 0; //used to generate unique ids.

        private synchronized int getNextID() {
            int temp = idCounter;

            if (idCounter == 0x7FFFFFFF) { //biggest positive 31 bit number..
                idCounter = 0;
            } else {
                idCounter++;
            }

            return temp;
        }

        private void timeout(int uniqueid) {
            fireEvents(uniqueid, null);
        }

        private static record ListenerRecord(MysterAddress address, int uniqueid,
                                             TransactionListener listener, TimeoutTimer timer, long timeStamp) {
            public ListenerRecord(MysterAddress address, int uniqueid,
                                  TransactionListener listener, TimeoutTimer timer) {
                this(address, uniqueid, listener, timer, System.currentTimeMillis());
                
                Objects.requireNonNull(address);
                Objects.requireNonNull(listener);
                Objects.requireNonNull(timer);
            }
        }

        private class TimeoutTimer implements Runnable {
            private final int[] TIMEOUTS = { 2500, 5000, 10000, 20000 };

            private final Transaction transaction;
            private final int uniqueid;
            
            private int timeoutCycle = 0;
            private boolean endFlag = false;

            public TimeoutTimer(int uniqueid, Transaction transaction) {
                this.uniqueid = uniqueid;
                this.transaction = transaction;

                setTheTimer();
            }

            private void setTheTimer() {
                // doens't need to be read
                Timer timer = new Timer(this, TIMEOUTS[timeoutCycle++]);
            }

            private void sendTheTransaction() {
                sender.sendPacket(transaction.toImmutableDatagramPacket());
            }

            public synchronized void run() {
                if (endFlag)
                    return;

                if (timeoutCycle < TIMEOUTS.length) {
                    sendTheTransaction();
                    setTheTimer();
                } else {
                    timeout(uniqueid);
                }
            }

            public synchronized void cancleTimer() {
                endFlag = true;
                //timer.cancleTimer(); <- It will work but might cause complex
                // threading problesm in cancleTimer if it's synchronized later.
            }
        }
    }
}