
package com.myster.message;

import java.io.IOException;

import com.myster.net.MysterAddress;
import com.myster.net.StandardDatagramClientImpl;
import com.myster.transaction.Transaction;

public class ImClient implements StandardDatagramClientImpl<MessagePacket> {
    private final MessagePacket messageToSend;

    public ImClient(MysterAddress address, String msg, String reply) {
        messageToSend = new MessagePacket(address, msg, reply); 
    }
    
    @Override
    public MessagePacket getObjectFromTransaction(Transaction transaction) throws IOException {
        return new MessagePacket(transaction);
    }

    @Override
    public Object getNullObject() {
        return null;
    }

    @Override
    public byte[] getDataForOutgoingPacket() {
        return messageToSend.getData();
    }

    @Override
    public int getCode() {
        return ImTransactionServer.TRANSACTION_CODE;
    }
    
}