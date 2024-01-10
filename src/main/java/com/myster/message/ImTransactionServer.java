package com.myster.message;

import java.util.ArrayDeque;
import java.util.Queue;

import com.myster.net.BadPacketException;
import com.myster.net.MysterAddress;
import com.myster.pref.MysterPreferences;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;

public class ImTransactionServer implements TransactionProtocol {
    private static final long EXPIRE_TIME = 60 * 60 * 1000; //1 hour.. (wow!)

    // package protected on purpose
    static final int TRANSACTION_CODE = 1111;

    private final Queue<ReceivedMessage> recentlyReceivedMessages = new ArrayDeque<>();
    private final MysterPreferences preferences;

    public final InstantMessageListener listener;

    public interface InstantMessageListener {
        public void messageReceived(InstantMessage msg);
    }

    public ImTransactionServer(MysterPreferences preferences, InstantMessageListener listener) {
        this.preferences = preferences;
        this.listener = listener;
    }
    
    @Override
    public int getTransactionCode() {
        return TRANSACTION_CODE;
    }

    private boolean messageReceived(MessagePacket msg) {
        if (MessageManager.isRefusingMessages(preferences))
            return false;

        listener.messageReceived(new InstantMessage(msg.getAddress(),
                                                    msg.getMessage(),
                                                    msg.getReply()));

        java.awt.Toolkit.getDefaultToolkit().beep();

        return true;
    }

    @Override
    public synchronized void transactionReceived(TransactionSender sender, Transaction transaction, Object transactionObject)
            throws BadPacketException {
        MessagePacket msg = new MessagePacket(transaction);

        ReceivedMessage receivedMessage = new ReceivedMessage(transaction, msg);
        if (isOld(receivedMessage)) {
            sender.sendTransaction(new Transaction(transaction,
                                                   (new MessagePacket(transaction.getAddress(),
                                                                      0,
                                                                      "")).getData(),
                                                   Transaction.NO_ERROR));
            return; // if it's one we've seen before ignore it.
        }

        recentlyReceivedMessages.add(receivedMessage);

        // below is where the event system would go.
        if (messageReceived(msg)) {
            sender.sendTransaction(new Transaction(transaction,
                                                   (new MessagePacket(transaction.getAddress(),
                                                                      0,
                                                                      "")).getData(),
                                                   Transaction.NO_ERROR));
        } else {
            sender.sendTransaction(new Transaction(transaction,
                                                   (new MessagePacket(transaction.getAddress(),
                                                                      1,
                                                                      MessageManager
                                                                              .getRefusingMessage(preferences)))
                                                                                      .getData(),
                                                   Transaction.NO_ERROR));
        }

        // reply with err or not
    }

    private void trashOld() { //gets rid of old messages.
        while (recentlyReceivedMessages.peek() != null) {
            var recentMessage = (recentlyReceivedMessages.peek());
            if (recentMessage.getTimeStamp() > (System.currentTimeMillis() - EXPIRE_TIME))
                return;

            recentlyReceivedMessages.poll();
        }
    }

    private boolean isOld(ReceivedMessage receivedMessage) {
        trashOld();

        return recentlyReceivedMessages.contains(receivedMessage);
    }

    private static class ReceivedMessage {
        private final long timeStamp;

        private final String msg;

        private final MysterAddress address;

        private final int messageID;

        public ReceivedMessage(Transaction transaction, MessagePacket msgPacket) {
            timeStamp = System.currentTimeMillis();
            msg = msgPacket.getMessage();
            address = transaction.getAddress();
            messageID = transaction.getConnectionNumber();
        }

        public boolean equals(Object o) {
            if (!(o instanceof ReceivedMessage))
                return false; //err

            ReceivedMessage message = (ReceivedMessage) o;

            return (message.msg.equals(msg) && message.address.equals(address) && message.messageID == messageID);
        }

        public long getTimeStamp() {
            return timeStamp;
        }
    }
}