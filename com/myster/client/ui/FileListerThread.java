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

import java.io.DataInputStream;
import java.io.DataOutputStream;

import com.general.util.TextSpinner;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;

public class FileListerThread extends MysterThread {
    ClientWindow w;

    Sayable msg;

    String ip;

    MysterType type;

    MysterSocket socket;

    public FileListerThread(ClientWindow a) {
        w = a;
        msg = w;
        this.ip = w.getCurrentIP();
        this.type = w.getCurrentType();
    }

    public void run() {
        setPriority(Thread.MAX_PRIORITY);
        DataOutputStream out;
        DataInputStream in;

        if (endFlag)
            return;
        
        if (socket == null) {
            try {
                say("Connecting to server...");
                socket = MysterSocketFactory
                        .makeStreamConnection(new MysterAddress(ip));
            } catch (Exception ex) {
                say("Server at that IP/Domain name is not responding.");
                return;
            }
        }

        if (endFlag)
            return;
        
        main: {
            try {
                out = new DataOutputStream(socket.getOutputStream());
                in = new DataInputStream(socket.getInputStream());

                say("Requesting File List...");

                out.writeInt(78);

                if (in.read() != 1) {
                    msg
                            .say("Server says it does not know how to send a file listing.");
                    break main;
                }

                if (type == null) {
                    say("No type is selected.");
                    break main;
                }

                say("Requesting File List: " + type);

                out.write(type.getBytes());
                int numberoffiles = in.readInt();
                ;

                say("Receiving List of Size: " + numberoffiles);
                System.out.println("Receiving List of Size: " + numberoffiles);

                TextSpinner spinner = new TextSpinner();

                final int LIMIT = 500;
                String[] files = new String[numberoffiles > LIMIT ? LIMIT
                        : numberoffiles];
                for (int i = 0; i < numberoffiles; i++) {
                    files[i % LIMIT] = in.readUTF();
                    if (i % 10 == 0)
                        say("Downloading file list: " + type + " "
                                + ((i * 100) / numberoffiles) + "%");

                    if ((i % LIMIT) == (LIMIT - 1)) {
                        addItemsToFileList(files);
                        if ((numberoffiles - i) < LIMIT)
                            files = new String[numberoffiles - i - 1];
                    }
                }

                out.writeInt(2);

                addItemsToFileList(files);

                in.read();

                say("Requesting File List: " + type + " "
                        + spinner.getSpin() + " Complete.");
                say("Idle...");
            } catch (Exception ex) {
                say("An unexpected error occured during the transfer of the file list.");
                ex.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (Exception ex) {
                    say("There was a problem closing the socket..");
                    return;
                }
            }
        }
    }
    
    public synchronized void say(final String message) {
        if (endFlag)
            return;
        msg.say(message);
    }
    
    public synchronized void addItemsToFileList(String[] files) {
        if (endFlag)
            return;
        w.addItemsToFileList(files);
    }
    
    public void flagToEnd() {
        endFlag = true;
        try { socket.close(); } catch (Exception ex) {}
        interrupt();
    }
    
    public void end() {
        endFlag = true;
        try { join();} catch (InterruptedException ex) {}
    }
}