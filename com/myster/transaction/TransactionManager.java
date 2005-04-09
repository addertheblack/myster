package com.myster.transaction;

import java.util.Hashtable;

import com.general.net.ImmutableDatagramPacket;
import com.general.util.Timer;
import com.general.util.Util;
import com.myster.net.BadPacketException;
import com.myster.net.DataPacket;
import com.myster.net.DatagramProtocolManager;
import com.myster.net.DatagramTransport;
import com.myster.net.MysterAddress;

/**
 * The TransactionManager is responsible for dealing with lower level details
 * involved with implementing the Myster simple datagram "transaction" protocol.
 * 
 * TODO put in transaction protocol docs here.
 */
public class TransactionManager implements TransactionSender {
    TransactionTransportImplementation impl;

    static TransactionManager singleton;

    /**
     * Creates a TransactionManager which also has a TransactionManager
     * implementation (which is supposed to actually do the work).
     */
    private TransactionManager() {
        impl = new TransactionTransportImplementation();
        try {
            DatagramProtocolManager.addTransport(impl);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Singleton lazy init loader. Don't call this from the outside.
     * 
     * @return the TransactionManager implementation.
     */
    private static synchronized TransactionManager load() {
        if (singleton == null) {
            singleton = new TransactionManager();
            //do some registering...
        }
        return singleton;
    }

    /**
     * Sends a transaction back. Ment for use by TransactionProtol (serve side).
     * 
     * @param packet
     *            to send
     * @see TransactionProtocol
     */
    public void sendTransaction(Transaction packet) {
        impl.sendTransaction(packet);
    }

    /**
     * Responsible for sending a transaction and notifying the listener if there
     * is any reply or timeout etc..
     * <p>
     * This function is for use by TransactionSocket only.
     * 
     * @see TransactionSocket
     * 
     * 
     * @param data
     *            DataPacket to send (protocol information is added to the
     *            information in this packet)
     * @param transactionCode
     *            of the remote datagram connection section to activate
     * @param listener
     *            to be notified upon events to do with this transaction
     * @return integer based ID to make references to this outstanding
     *         transaction. The id is no longer valid after the transaciton has
     *         responded
     */
    static int sendTransaction(DataPacket data, int transactionCode, TransactionListener listener) {
        return load().impl.sendTransaction(data, transactionCode, listener);
    }

    /**
     * Cancels the outstanding transaction referenced by this id.
     * <p>
     * There's actually not much point in cancelling a transaction since the
     * same result can be obtained by ignoring the response of the server. The
     * only difference is cancelling the transaction means Myster can free up
     * the resources occupied by the transaction as well as not bother sending
     * additional follow-up packets, which are usually sent if there is no
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
    static boolean cancelTransaction(int id) {
        return load().impl.cancelTransaction(id);
    }

    /**
     * Adds a TransactionProtocol (server side). A TransactionProtocol is a
     * server side event listener for a specific transaction connection section.
     * <p>
     * 
     * @see TransactionProtocol
     * @param protocol
     *            listener to add.
     * @return the same TransactionProtocol but with a valid "sender"
     */
    public static TransactionProtocol addTransactionProtocol(TransactionProtocol protocol) { //For
        // server
        protocol.setSender(load());
        return load().impl.addTransactionProtocol(protocol);
    }

    /**
     * Removes this protocol from active duty. Does not invalidate the sender.
     * 
     * @param protocol
     *            object to remove.
     * @return the same TransactionProtocol object..
     */
    public static TransactionProtocol removeTransactionProtocol(TransactionProtocol protocol) { //For
        // server
        return load().impl.removeTransactionProtocol(protocol);
    }

    /**
     * This class implements the DatagramTransport which means it implements a
     * listener for all datagram packets with the its
     * TRANSACTION_PROTOCOL_NUMBER. This also means all outgoing packets need to
     * go through this object as this is the interface provided by
     * DatagramProtocolManager.
     * 
     * @see DatagramProtocolManager
     */
    private static class TransactionTransportImplementation extends DatagramTransport {
        private final Hashtable serverProtocols = new Hashtable();

        private final Hashtable outstandingTransactions = new Hashtable();

        public short getTransportCode() {
            return Transaction.TRANSACTION_PROTOCOL_NUMBER;
        }

        public void packetReceived(ImmutableDatagramPacket immutablePacket)
                throws BadPacketException {
            Transaction transaction = new Transaction(immutablePacket);

            if (transaction.isForClient()) {
                fireEvents(transaction.getConnectionNumber(), transaction);
            } else {
                TransactionProtocol protocol = (TransactionProtocol) (serverProtocols
                        .get(new Integer(transaction.getTransactionCode())));

                if (protocol == null) {
                    System.out.println("No Transaction protocol registered under type: "
                            + transaction.getTransactionCode());

                    sendTransaction(new Transaction(transaction, new byte[0],
                            Transaction.TRANSACTION_TYPE_UNKNOWN));//returning
                    // error
                    // here!

                    return;
                }

                protocol.transactionReceived(transaction); //fun...
            }
        }

        public int sendTransaction(DataPacket packet, int transactionCode,
                TransactionListener listener) {
            int uniqueid = getNextID();

            Transaction transaction = new Transaction(packet.getAddress(), transactionCode,
                    uniqueid, packet.getData());

            outstandingTransactions.put(new Integer(uniqueid), new ListenerRecord(packet
                    .getAddress(), uniqueid, listener, new TimeoutTimer(uniqueid, transaction)));

            sendPacket(transaction.toImmutableDatagramPacket());

            return uniqueid;
        }

        /**
         * Cancels the transaction pointed to by this internal id. This routine
         * blocks until the event is cancelled but sends the cancelled event
         * asynchronously to the transaction listeners.
         * 
         * 
         * @param uniqueId
         * @return true if cancelled.
         */
        public boolean cancelTransaction(int uniqueId) {
            final ListenerRecord record = (ListenerRecord) outstandingTransactions
                    .remove(new Integer(uniqueId));

            if (record == null)
                return false;

            record.timer.cancleTimer();

            /*
             * Events should be on the event thread. We can't use the standard
             * fireEvents routine in this object because the routine assumes
             * that it's called on the event thread. We also couldn't just
             * bundle up the code here in a runnable and send it to the event
             * thread because those runnable are done asynchronously and we need
             * to return if the transaction was cancelled,
             * 
             * We could do it in a synchronous call to the event thread.. but
             * that could cause confusing deadlocks (and harder to write code).
             * There's also no reason for it when we can cancel the event THEN
             * asynchronously update all listeners. It's a feature.
             */
            Util.invokeLater(new Runnable() {
                public void run() {
                    record.listener.fireEvent(new TransactionEvent(TransactionEvent.CANCELLED,
                            System.currentTimeMillis() - record.timeStamp, record.address, null));
                }
            });

            return true;
        }

        public void sendTransaction(Transaction transaction) {
            sendPacket(transaction.getImmutableDatagramPacket());
        }

        public TransactionProtocol addTransactionProtocol(TransactionProtocol protocol) { //For
            // server
            return (TransactionProtocol) (serverProtocols.put(new Integer(protocol
                    .getTransactionCode()), protocol));
        }

        public TransactionProtocol removeTransactionProtocol(TransactionProtocol protocol) { //For
            // server
            return (TransactionProtocol) (serverProtocols.remove(new Integer(protocol
                    .getTransactionCode())));
        }

        /*
         * Not all events are sent through here. Cancelled events aren't sent
         * here.
         */
        private void fireEvents(int uniqueid, Transaction transaction) {
            ListenerRecord record = ((ListenerRecord) (outstandingTransactions.remove(new Integer(
                    uniqueid))));

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
                    System.currentTimeMillis() - record.timeStamp, record.address,
                    transaction));
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

        private static class ListenerRecord { //NOT immutable
            public final long timeStamp;

            public final TimeoutTimer timer;

            public final TransactionListener listener;

            public final int uniqueid;

            public MysterAddress address; //if reply is from different it won't

            // work.

            public ListenerRecord(MysterAddress address, int uniqueid,
                    TransactionListener listener, TimeoutTimer timer) {
                this.address = address;
                this.uniqueid = uniqueid;
                this.listener = listener;
                this.timeStamp = System.currentTimeMillis();
                this.timer = timer;
            }

        }

        private class TimeoutTimer implements Runnable {
            private final int[] TIMEOUTS = { 2500, 5000, 10000, 20000 };

            int timeoutCycle = 0;

            Transaction transaction;

            int uniqueid;

            boolean endFlag = false;

            public TimeoutTimer(int uniqueid, Transaction transaction) {
                this.uniqueid = uniqueid;
                this.transaction = transaction;

                setTheTimer();
            }

            private void setTheTimer() {
                Timer timer = new Timer(this, TIMEOUTS[timeoutCycle++]); // doens't
                // have
                // to
                // be
                // read
            }

            private void sendTheTransaction() {
                sendPacket(transaction.toImmutableDatagramPacket());
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