package com.myster.transaction;

import java.util.Hashtable;

import com.general.net.ImmutableDatagramPacket;
import com.general.util.Timer;
import com.myster.net.BadPacketException;
import com.myster.net.DataPacket;
import com.myster.net.DatagramProtocolManager;
import com.myster.net.DatagramTransport;
import com.myster.net.MysterAddress;

public class TransactionManager implements TransactionSender {
    //public static final short TRANSACTION_PROTOCOL_NUMBER=6000;

    TransactionTransportImplementation impl;

    static TransactionManager singleton;

    public TransactionManager() {
        impl = new TransactionTransportImplementation();
        try {
            DatagramProtocolManager.addTransport(impl);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static synchronized TransactionManager load() {
        if (singleton == null) {
            singleton = new TransactionManager();
            //do some registering...
        }
        return singleton;
    }

    public void sendTransaction(Transaction packet) {
        impl.sendTransaction(packet);
    }

    protected static void sendTransaction(DataPacket data, int transactionCode,
            TransactionListener listener) {
        load().impl.sendTransaction(data, transactionCode, listener);
    }

    public static TransactionProtocol addTransactionProtocol(
            TransactionProtocol protocol) { //For server
        protocol.setSender(load());
        return load().impl.addTransactionProtocol(protocol);
    }

    public static TransactionProtocol removeTransactionProtocol(
            TransactionProtocol protocol) { //For server
        return load().impl.removeTransactionProtocol(protocol);
    }

    private static class TransactionTransportImplementation extends
            DatagramTransport {
        private Hashtable serverProtocols = new Hashtable();

        private Hashtable outstandingTransactions = new Hashtable();

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
                    System.out
                            .println("No Transaction protocol registered under type: "
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

        public void sendTransaction(DataPacket packet, int transactionCode,
                TransactionListener listener) {
            int uniqueid = getNextID();

            Transaction transaction = new Transaction(packet.getAddress(),
                    transactionCode, uniqueid, packet.getData());

            outstandingTransactions.put(new Integer(uniqueid),
                    new ListenerRecord(packet.getAddress(), uniqueid, listener,
                            new TimeoutTimer(uniqueid, transaction)));

            sendPacket(transaction.toImmutableDatagramPacket());
        }

        public void sendTransaction(Transaction transaction) {
            sendPacket(transaction.getImmutableDatagramPacket());
        }

        public TransactionProtocol addTransactionProtocol(
                TransactionProtocol protocol) { //For server
            return (TransactionProtocol) (serverProtocols.put(new Integer(
                    protocol.getTransactionCode()), protocol));
        }

        public TransactionProtocol removeTransactionProtocol(
                TransactionProtocol protocol) { //For server
            return (TransactionProtocol) (serverProtocols.remove(new Integer(
                    protocol.getTransactionCode())));
        }

        private void fireEvents(int uniqueid, Transaction transaction) {
            ListenerRecord record = ((ListenerRecord) (outstandingTransactions
                    .remove(new Integer(uniqueid))));

            if (record == null)
                return; //minor err

            record.timer.cancleTimer();

            //if it's not from the right address ignore.. Anti-spoofing
            if (transaction != null) {
                if (!transaction.getAddress().equals(record.address))
                    return;
            }

            record.listener.fireEvent(new TransactionEvent(
                    (transaction == null ? TransactionEvent.TIMEOUT
                            : TransactionEvent.REPLY), System
                            .currentTimeMillis()
                            - record.timeStamp, (transaction == null ? null
                            : transaction.getAddress()), transaction));
        }

        int idCounter = 0;

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