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

import com.myster.client.stream.StandardSuite;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;

public class TypeListerThread extends MysterThread {
    ClientWindow container;

    Sayable msg;

    String ip;

    MysterSocket socket;

    public TypeListerThread(ClientWindow w) {
        container = w;
        msg = w;
        this.ip = w.getCurrentIP();
    }

    public void run() {
        try {
            say("Requested Type List (UDP)...");

            if (endFlag)
                return;
            com.myster.type.MysterType[] types = com.myster.client.datagram.StandardDatagramSuite
                    .getTypes(new MysterAddress(ip));
            if (endFlag)
                return;

            for (int i = 0; i < types.length; i++) {
                container.addItemToTypeList("" + types[i]);
            }

            say("Idle...");
        } catch (IOException exp) {
            try {
                say("Connecting to server...");
                socket = MysterSocketFactory
                        .makeStreamConnection(new MysterAddress(ip));
                if (endFlag)
                    return;
            } catch (IOException ex) {
                say("Could not connect, server is unreachable...");
                return;
            }

            try {
                say("Requesting File Type List...");

                MysterType[] typeList = StandardSuite.getTypes(socket);

                say("Adding Items...");
                for (int i = 0; i < typeList.length; i++) {
                    container.addItemToTypeList(typeList[i].toString());
                }

                say("Idle...");
            } catch (IOException ex) {
                say("Could not get File Type List from specified server.");
            } finally {
                try {
                    socket.close();
                } catch (Exception ex) {
                }
            }
        }
    }
    
    private synchronized void addItemToTypeList(final MysterType type) {
        if (endFlag)
            return;
        container.addItemToTypeList(type.toString());
    }
    
    private synchronized void say(final String message) {
        if (endFlag)
            return;
        msg.say(message);
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