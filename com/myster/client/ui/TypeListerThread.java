/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.client.ui;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import com.general.thread.CallAdapter;
import com.general.util.Util;
import com.myster.client.net.MysterProtocol;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;

public class TypeListerThread extends MysterThread {
    public interface TypeListener {
        public void addItemToTypeList(MysterType s);   
        public void refreshIP(MysterAddress address);
    }
    

    private final MysterProtocol protocol;
    private final TypeListener listener;
    private final Sayable msg;
    private final String ip;
    
    private MysterSocket socket;

    public TypeListerThread(MysterProtocol protocol,
                            TypeListener listener,
                            Sayable msg,
                            String ip) {
        this.protocol = protocol;
        this.listener = new TypeListener() {
            public void addItemToTypeList(MysterType s) {
                Util.invokeLater(() -> {
                    if (endFlag) {
                        return;
                    }
                    listener.addItemToTypeList(s);
                });
            }

            public void refreshIP(MysterAddress address) {
                Util.invokeLater(() -> {
                    if (endFlag) {
                        return;
                    }
                    listener.refreshIP(address);
                });
            }
        };
        this.msg = (String s) -> Util.invokeLater(() -> msg.say(s));
        this.ip = ip;
    }

    public void run() {
        try {
            msg.say("Requested Type List (UDP)...");

            if (endFlag)
                return;
            MysterAddress mysterAddress = new MysterAddress(ip);
            listener.refreshIP(mysterAddress);
            com.myster.type.MysterType[] types = protocol.getDatagram().getTypes(mysterAddress, new CallAdapter<>()).get();
            if (endFlag)
                return;

            for (int i = 0; i < types.length; i++) {
                listener.addItemToTypeList(types[i]);
            }

            msg.say("Idle...");
        } catch (ExecutionException | IOException exp) {
            if ((exp instanceof ExecutionException) && !(exp.getCause() instanceof IOException)) {
                throw new IllegalStateException("Unexpected Exception", exp);
            }
            
            msg.say("Connecting to server...");
            try (MysterSocket socket =
                    MysterSocketFactory.makeStreamConnection(new MysterAddress(ip))) {
                if (endFlag)
                    return;
            } catch (IOException ex) {
                msg.say("Could not connect, server is unreachable...");
                return;
            }

            try {
                msg.say("Requesting File Type List...");

                MysterType[] typeList = protocol.getStream().getTypes(socket);

                msg.say("Adding Items...");
                for (int i = 0; i < typeList.length; i++) {
                    listener.addItemToTypeList(typeList[i]);
                }

                msg.say("Idle...");
            } catch (IOException ex) {
                msg.say("Could not get File Type List from specified server.");
            }
        } catch (InterruptedException exception) {
            return;
        }
    }
    
    public void flagToEnd() {
        endFlag = true;
        try { socket.close(); } catch (Exception ex) {}
        interrupt();
    }
    
    public void end() {
        flagToEnd();
        try {
            join();
        } catch (InterruptedException ex) {
            //nothing..
        }
    }
}