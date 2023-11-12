package com.myster.message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;

import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.BadPacketException;
import com.myster.net.DataPacket;
import com.myster.net.MysterAddress;
import com.myster.pref.Preferences;
import com.myster.pref.PreferencesMML;
import com.myster.tracker.IPListManager;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionEvent;
import com.myster.transaction.TransactionListener;
import com.myster.transaction.TransactionManager;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSocket;

public class MessageManager {
    private static Preferences preferences;

    private static IPListManager ipListManager;

    
    
    public static void init(IPListManager ipListManager, Preferences preferences) {
        MessageManager.ipListManager = ipListManager;
        MessageManager.preferences = preferences;
        TransactionManager.addTransactionProtocol(new InstantMessageTransport(preferences));
    }

    public static void sendInstantMessage(MysterAddress address, String msg) {
        sendInstantMessage(address, msg, null);
    }

    public static void sendInstantMessage(InstantMessage message) {
        sendInstantMessage(message.address, message.message, message.quote);
    }

    public static void sendInstantMessage(MysterAddress address, String msg, String reply) {
        TransactionSocket tsocket = new TransactionSocket(InstantMessageTransport.TRANSPORT_NUMBER);

        tsocket.sendTransaction(new MessagePacket(generateID(), address, msg, reply),
                new TransactionListener() {
                    public void transactionReply(TransactionEvent e) {
                        System.out.println("Message reply.");

                        Transaction transaction = e.getTransaction();

                        if (transaction.isError()) {
                            if (transaction.getErrorCode() == Transaction.TRANSACTION_TYPE_UNKNOWN) {
                                simpleAlert("Client doesn't know how to receive messages.");
                            } else {
                                simpleAlert("Some sort of unknown error occured.");
                            }
                        } else {
                            try {
                                MessagePacket msgPacket = new MessagePacket(transaction);
                                if (msgPacket.getErrorCode() != 0) {
                                    if (msgPacket.getErrorCode() == 1) {
                                        simpleAlert("Client is refusing messages."
                                                + "\n\nClient says -> "
                                                + msgPacket.getErrorString());
                                    } else {
                                        simpleAlert("Client got the message, with error code "
                                                + msgPacket.getErrorCode() + "\n\n"
                                                + msgPacket.getErrorString());
                                    }
                                } else {
                                    //simpleAlert("Message was sent successfully...");
                                }
                            } catch (BadPacketException ex) {
                                simpleAlert("Remote host returned a bad packet.");
                            }
                        }
                    }

                    public void transactionTimout(TransactionEvent e) {
                        simpleAlert("Message was not received. There does not appear to be anyone at that address.");
                    }
                });
    }

    //public static boolean sendInstantMessageBlocking(...) {
    //	//..
    //}

    //public static void setMessageListener(MessageListener l) {
    //	
    //}

    protected static boolean messageReceived(MessagePacket msg) {
        if (isRefusingMessages(preferences))
            return false;

        (new MessageWindow(new InstantMessage(msg.getAddress(), msg.getMessage(), msg.getReply()), ipListManager::getQuickServerStats))
                .show();

        java.awt.Toolkit.getDefaultToolkit().beep();

        return true;
    }

    private static void simpleAlert(final String message) {
        Util.invokeLater(() -> {
            AnswerDialog.simpleAlert(message);
        });
    }

    private static int counter = 1;

    private static synchronized int generateID() {
        return counter++;
    }

    ////////////// PREFs \\\\\\\\\\
    private static final String PREFS_MESSAGING_KEY = "Myster Instant Messaging";
    private static final String MML_REFUSE_FLAG = "/Refusing";
    private static final String MML_REFUSE_MESSAGE = "/Refusing Messages";
    private static final String REFUSAL_MESSAGE_DEFAULT = "Not accepting messages at this time.";
    private static final String TRUE_AS_STRING = "TRUE";
    private static final String FALSE_AS_STRING = "FALSE";
    
    public static boolean isRefusingMessages(Preferences p) {
        return getPreferencesMML(p).get(MML_REFUSE_FLAG, FALSE_AS_STRING).equals(TRUE_AS_STRING);
    }

    public static String getRefusingMessage(Preferences p) {
        return getPreferencesMML(p).get(MML_REFUSE_MESSAGE, REFUSAL_MESSAGE_DEFAULT);
    }

    public static void setPrefs(Preferences p, Optional<String> text, boolean refuseMessages) {
        RobustMML mml = new RobustMML();
        mml.put(MML_REFUSE_MESSAGE, text.orElse(REFUSAL_MESSAGE_DEFAULT));

        mml.put(MML_REFUSE_FLAG, (refuseMessages ? TRUE_AS_STRING : FALSE_AS_STRING));

        p.put(PREFS_MESSAGING_KEY, mml);
    }

    private static PreferencesMML getPreferencesMML(Preferences p) {
        return new PreferencesMML(p.getAsMML(PREFS_MESSAGING_KEY,
                new PreferencesMML()));
    }
}



class InstantMessageTransport extends TransactionProtocol {
    private static final long EXPIRE_TIME = 60 * 60 * 1000; //1 hour.. (wow!)

    // package protected on purpose
    static final int TRANSPORT_NUMBER = 1111;

    private final Queue<ReceivedMessage> recentlyReceivedMessages = new ArrayDeque<>();

    private final Preferences preferences;

    public InstantMessageTransport(Preferences preferences) {
        this.preferences = preferences;
    }
    
    public int getTransactionCode() {
        return TRANSPORT_NUMBER;
    }

    public synchronized void transactionReceived(Transaction transaction, Object transactionObject)
            throws BadPacketException {
        MessagePacket msg = new MessagePacket(transaction);

        ReceivedMessage receivedMessage = new ReceivedMessage(msg);
        if (isOld(receivedMessage)) {
            sendTransaction(new Transaction(transaction, (new MessagePacket(transaction
                    .getAddress(), 0, "")).getData(), Transaction.NO_ERROR));
            return; //if it's one we've seen before ignore it.
        }

        recentlyReceivedMessages.add(receivedMessage);

        //below is where the event system would go.
        if (MessageManager.messageReceived(msg)) {
            sendTransaction(new Transaction(transaction, (new MessagePacket(transaction
                    .getAddress(), 0, "")).getData(), Transaction.NO_ERROR));
        } else {
            sendTransaction(new Transaction(transaction, (new MessagePacket(transaction
                    .getAddress(), 1, MessageManager.getRefusingMessage(preferences))).getData(),
                    Transaction.NO_ERROR));
        }

        //reply with err or not
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

        public ReceivedMessage(MessagePacket msgPacket) {
            timeStamp = System.currentTimeMillis();
            msg = msgPacket.getMessage();
            address = msgPacket.getAddress();
            messageID = msgPacket.getID();
            //System.out.println("message id:"+messageID);
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

class MessagePacket implements DataPacket { //Is Immutable
    private static final String REPLY = "/Reply";

    private static final String MSG = "/Msg";

    private static final String ID_KEY = "/ID";

    private static final String FROM = "/From/";

    private static final String REPLY_ERR_KEY = "/Error Code";

    private static final String REPLY_ERR_STRING = "/Error String";

    public static final int PROTOCOL = 1111; //Transaction Protocol

    //Message
    private final String msg;

    private final String reply;

    private final String from;

    private final int id;

    //Msg Reply
    private final boolean replyFlag;

    private final String replyErrMsg;

    private final int replyErrCode;

    private final MysterAddress address;

    private byte[] data;

    public MessagePacket(int id, MysterAddress address, String msg, String reply) {
        this.id = id;

        this.address = address;

        this.msg = msg;
        this.from = null;
        this.reply = reply;

        this.replyErrMsg = null;

        this.replyFlag = false;
        this.replyErrCode = 0;

        //Do null pointer checks
        //...
        //End null pointer checks...

        data = makeBytes();
    }

    public MessagePacket(int id, MysterAddress address, String msg) {
        this(id, address, msg, null);
    }

    public MessagePacket(MysterAddress address, int errCode, String errMessage) {
        this.replyErrCode = errCode;
        this.replyErrMsg = errMessage;

        this.address = address;

        this.replyFlag = true;

        this.msg = null;
        this.from = null;
        this.reply = null;
        this.id = 0;

        data = makeBytes();
    }

    public MessagePacket(Transaction transaction) throws BadPacketException {
        data = transaction.getData();

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        RobustMML mml;
        try {
            mml = new RobustMML(in.readUTF());
        } catch (MMLException ex) {
            throw new BadPacketException("Not an MML.");
        } catch (IOException ex) {
            throw new BadPacketException("Death.");
        }

        String errCodeAsString = mml.get(REPLY_ERR_KEY);

        if (errCodeAsString == null) { //no error code = not reply

            msg = mml.get(MSG);
            reply = mml.get(REPLY);
            from = (mml.get(FROM) == null ? transaction.getAddress().toString() : mml.get(FROM));
            String idAsString = mml.get(ID_KEY);

            //errors
            if (msg == null)
                throw new BadPacketException("No message in this message packet.");

            try {
                id = Integer.parseInt(idAsString);
            } catch (NumberFormatException ex) {
                throw new BadPacketException(idAsString + " is not a number!");
            }
            //end errors

            this.replyErrMsg = null;
            this.replyErrCode = 0;

            replyFlag = false;

        } else {
            try {
                replyErrCode = Integer.parseInt(errCodeAsString);
            } catch (NumberFormatException ex) {
                throw new BadPacketException("Error code is NaN.");
            }

            replyErrMsg = (mml.get(REPLY_ERR_STRING) == null ? "" : mml.get(REPLY_ERR_STRING));

            this.msg = null;
            this.from = null;
            this.reply = null;
            this.id = 0;

            replyFlag = true;
        }

        address = transaction.getAddress();
    }

    public String getMessage() {
        return msg;
    }

    public String getReply() {
        return reply;
    }

    public String getFromName() {
        return from;
    }

    public MysterAddress getAddress() {
        return address;
    }

    public boolean isReply() {
        return replyFlag;
    }

    public String getErrorString() {
        return replyErrMsg;
    }

    public int getErrorCode() {
        return replyErrCode;
    }

    public int getID() {
        return id;
    }

    private byte[] makeBytes() {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteStream);
        RobustMML mml = new RobustMML();

        //msg
        if (reply != null)
            mml.put(REPLY, reply);
        if (msg != null)
            mml.put(MSG, msg);
        if (from != null)
            mml.put(FROM, from);
        mml.put(ID_KEY, "" + id);

        //reply
        if (isReply())
            mml.put(REPLY_ERR_KEY, "" + replyErrCode);
        if (isReply() && replyErrMsg != null)
            mml.put(REPLY_ERR_STRING, replyErrMsg);

        try {
            out.writeUTF(mml.toString());
        } catch (IOException ex) {
            // nothing
        }

        return byteStream.toByteArray();
    }

    public byte[] getData() { //is slow.
        return (data.clone());
    }

    public byte[] getHeader() {
        return new byte[] {};
    }

    public byte[] getBytes() {
        return getData(); //warning.. does not access getHeader!!!!!! (is not
        // necessary at this writing)
    }
}

