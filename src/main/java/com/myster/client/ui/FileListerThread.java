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

import com.general.util.Util;
import com.myster.client.stream.MysterDataInputStream;
import com.myster.client.stream.MysterDataOutputStream;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;

public class FileListerThread extends MysterThread {
//    private static final Logger LOGGER = Logger.getLogger(FileListerThread.class.getName());
    
    public interface ItemListListener {
        public void addItemsToFileList(String[] files);
    }
    
    private final ItemListListener listener;
    private final Sayable msg;
    private final String ip;
    private final MysterType type;

    private MysterSocket socket;
    
    private final LookupTypeDescription lookup;
    
    interface LookupTypeDescription {
        String lookup(MysterType type);
    }

    public FileListerThread(ItemListListener listener, Sayable msg, String ip, MysterType type, LookupTypeDescription lookup) {
        this.lookup = lookup;
        this.listener = (String[] files) -> Util.invokeLater(() -> {
            if (endFlag) {
                return;
            }
            listener.addItemsToFileList(files);
        });
        this.msg = (String s) -> Util.invokeLater(() -> {
            if (endFlag) {
                return;
            }
            
            msg.say(s);
        });

        this.ip = ip;
        this.type = type;
    }

    public void run() {
        setPriority(Thread.MAX_PRIORITY);

        if (endFlag)
            return;
        if (type == null)
            return;

        try (MysterSocket socket =
                MysterSocketFactory.makeStreamConnection(MysterAddress.createMysterAddress(ip))) {

            MysterDataOutputStream out = socket.getOutputStream();
            MysterDataInputStream in = socket.getInputStream();

            msg.say("Requesting File List...");

            out.writeInt(78);

            if (in.read() != 1) {
                msg.say("Server says it does not know how to send a file listing.");
                return;
            }

            if (type == null) {
                msg.say("No type is selected.");
                return;
            }

            msg.say("Requesting File List: " + lookup.lookup(type));

            out.writeType(type);
            int numberoffiles = in.readInt();

            msg.say("Receiving List of Size: " + numberoffiles);

            final int LIMIT = 500;
            String[] files = new String[numberoffiles > LIMIT ? LIMIT : numberoffiles];
            for (int i = 0; i < numberoffiles; i++) {
                files[i % LIMIT] = in.readUTF();
                if (i % 100 == 0)
                    msg.say("Downloading file list: " + lookup.lookup(type) + " " + ((i * 100) / numberoffiles) + "%");

                if ((i % LIMIT) == (LIMIT - 1)) {
                    listener.addItemsToFileList(files);
                    if ((numberoffiles - i) < LIMIT)
                        files = new String[numberoffiles - i - 1];
                }
            }

            out.writeInt(2);

            listener.addItemsToFileList(files);

            in.read();

            msg.say("Requesting File List: " + lookup.lookup(type) + " Complete.");
            msg.say("Idle...");
        } catch (IOException ex) {
            msg.say("An unexpected error occurred during the transfer of the file list.");
            ex.printStackTrace();
        }
    }
    
    public void flagToEnd() {
        endFlag = true;
        try { socket.close(); } catch (Exception ex) {}
        interrupt();
    }
    
    public void end() {
        flagToEnd();
        try { join();} catch (InterruptedException ex) {}
    }
}