
package com.myster.message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.client.stream.MysterDataInputStream;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.BadPacketException;
import com.myster.net.MysterAddress;
import com.myster.transaction.Transaction;

public class MessagePacket { //Is Immutable
    private static final String REPLY = "/Reply";
    private static final String MSG = "/Msg";
    private static final String FROM = "/From/";
    private static final String REPLY_ERR_KEY = "/Error Code";
    private static final String REPLY_ERR_STRING = "/Error String";

    //Message
    private final String msg;
    private final String reply;
    private final String from;

    //Msg Reply
    private final boolean replyFlag;
    private final String replyErrMsg;
    private final int replyErrCode;
    private final MysterAddress address;

    private byte[] data;

    public MessagePacket(MysterAddress address, String msg, String reply) {
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
        this(address, msg, null);
    }

    public MessagePacket(MysterAddress address, int errCode, String errMessage) {
        this.replyErrCode = errCode;
        this.replyErrMsg = errMessage;

        this.address = address;

        this.replyFlag = true;

        this.msg = null;
        this.from = null;
        this.reply = null;

        data = makeBytes();
    }

    public MessagePacket(Transaction transaction) throws BadPacketException {
        data = transaction.getData();

        MysterDataInputStream in = new MysterDataInputStream(new ByteArrayInputStream(data));

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

            //errors
            if (msg == null)
                throw new BadPacketException("No message in this message packet.");
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
}