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
            msg.say("Requested Type List (UDP)...");

            com.myster.type.MysterType[] types = com.myster.client.datagram.StandardDatagramSuite
                    .getTypes(new MysterAddress(ip));

            for (int i = 0; i < types.length; i++) {
                container.addItemToTypeList("" + types[i]);
            }

            msg.say("Idle...");
        } catch (IOException exp) {
            try {
                msg.say("Connecting to server...");
                socket = MysterSocketFactory
                        .makeStreamConnection(new MysterAddress(ip));
            } catch (IOException ex) {
                msg.say("Could not connect, server is unreachable...");
                return;
            }

            try {
                msg.say("Requesting File Type List...");

                MysterType[] typeList = StandardSuite.getTypes(socket);

                msg.say("Adding Items...");
                for (int i = 0; i < typeList.length; i++) {
                    container.addItemToTypeList(typeList[i].toString());
                }

                msg.say("Idle...");
            } catch (IOException ex) {
                msg.say("Could not get File Type List from specified server.");
            } finally {
                try {
                    socket.close();
                } catch (Exception ex) {
                }
            }
        }
    }
}