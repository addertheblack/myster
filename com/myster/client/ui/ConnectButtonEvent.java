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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import com.myster.util.MysterThread;

public class ConnectButtonEvent implements ActionListener {
    ClientWindow w;

    MysterThread connectToThread;

    public ConnectButtonEvent(ClientWindow w) {
        this.w = w;
    }

    public synchronized void actionPerformed(ActionEvent a) {
        try {
            connectToThread.end();
        } catch (Exception ex) {
        }
        w.clearAll();
        w.refreshIP();
        connectToThread = (new TypeListerThread(w));
        connectToThread.start();
    }

}