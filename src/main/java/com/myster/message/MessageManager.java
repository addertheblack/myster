package com.myster.message;

import java.util.Optional;

import com.general.thread.CallListener;
import com.general.thread.PromiseFuture;
import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.client.net.MysterDatagram;
import com.myster.client.net.ParamBuilder;
import com.myster.client.stream.UnknownProtocolException;
import com.myster.mml.RobustMML;
import com.myster.net.BadPacketException;
import com.myster.net.MysterAddress;
import com.myster.net.TimeoutException;
import com.myster.pref.MysterPreferences;
import com.myster.pref.PreferencesMML;

public class MessageManager {
    public static PromiseFuture<MessagePacket> sendInstantMessage(MysterDatagram protocol,
                                                                  InstantMessage message) {
        return sendInstantMessage(protocol, message.address, message.message, message.quote);
    }

    public static PromiseFuture<MessagePacket> sendInstantMessage(MysterDatagram protocol,
                                                                  MysterAddress address,
                                                                  String msg,
                                                                  String quote) {
        return protocol.sendInstantMessage(new ParamBuilder(address), msg, quote)
                .addCallListener(new CallListener<MessagePacket>() {
                    @Override
                    public void handleResult(MessagePacket msgPacket) {
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
            }
            
            @Override
            public void handleException(Throwable exception) {
                if (exception instanceof UnknownProtocolException ex) {
                    simpleAlert("Client doesn't know how to receive messages.");
                    return;
                } else if (exception instanceof TimeoutException ex) {
                    simpleAlert("Message was not received. There does not appear to be anyone at that address.");
                } else if ( exception instanceof BadPacketException ex ) {
                    simpleAlert("Remote host returned a bad packet.");
                }
                else {
                    simpleAlert("Some sort of unknown error occured.");
                    exception.printStackTrace();
                    return;
                }
            }
            
            @Override
            public void handleFinally() {
                
            }
            
            @Override
            public void handleCancel() {
                
            }
        });
    }

    private static void simpleAlert(final String message) {
        Util.invokeLater(() -> {
            AnswerDialog.simpleAlert(message);
        });
    }

    ////////////// PREFs \\\\\\\\\\
    private static final String PREFS_MESSAGING_KEY = "Myster Instant Messaging";
    private static final String MML_REFUSE_FLAG = "/Refusing";
    private static final String MML_REFUSE_MESSAGE = "/Refusing Messages";
    private static final String REFUSAL_MESSAGE_DEFAULT = "Not accepting messages at this time.";
    private static final String TRUE_AS_STRING = "TRUE";
    private static final String FALSE_AS_STRING = "FALSE";
    
    public static boolean isRefusingMessages(MysterPreferences p) {
        return getPreferencesMML(p).get(MML_REFUSE_FLAG, FALSE_AS_STRING).equals(TRUE_AS_STRING);
    }

    public static String getRefusingMessage(MysterPreferences p) {
        return getPreferencesMML(p).get(MML_REFUSE_MESSAGE, REFUSAL_MESSAGE_DEFAULT);
    }

    public static void setPrefs(MysterPreferences p, Optional<String> text, boolean refuseMessages) {
        RobustMML mml = new RobustMML();
        mml.put(MML_REFUSE_MESSAGE, text.orElse(REFUSAL_MESSAGE_DEFAULT));

        mml.put(MML_REFUSE_FLAG, (refuseMessages ? TRUE_AS_STRING : FALSE_AS_STRING));

        p.put(PREFS_MESSAGING_KEY, mml);
    }

    private static PreferencesMML getPreferencesMML(MysterPreferences p) {
        return new PreferencesMML(p.getAsMML(PREFS_MESSAGING_KEY,
                new PreferencesMML()));
    }
}