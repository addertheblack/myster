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
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import com.general.thread.Invoker;
import com.general.util.UnexpectedException;
import com.general.util.Util;
import com.myster.mml.MessagePack;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.net.stream.client.MysterSocketFactory;
import com.myster.net.stream.client.StandardSuiteStream;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;

public class FileListerThread extends MysterThread {
//    private static final Logger LOGGER = Logger.getLogger(FileListerThread.class.getName());
    
    public record FileRecord(String file, MessagePack metaData) {}
    
    public interface ItemListListener {
        public void addItemsToFileList(FileRecord[] files);
    }

    // must be called on EDT!
    private final ItemListListener listener;

    // must be called on EDT!
    private final Sayable msg;

    private final String ip;
    private final MysterType type;

    private MysterSocket socket;
    
    private final LookupTypeDescription lookup;
    
    interface LookupTypeDescription {
        String lookup(MysterType type);
    }

    public FileListerThread(ItemListListener listener,
                            Sayable msg,
                            String ip,
                            MysterType type,
                            LookupTypeDescription lookup) {
        this.lookup = lookup;
        this.listener = listener;
        this.msg = msg;

        this.ip = ip;
        this.type = type;
    }

    public void run() {
        setPriority(Thread.MAX_PRIORITY);

        if (endFlag)
            return;
        if (type == null)
            return;

        MysterAddress mysterAddress;
        try {
            mysterAddress = MysterAddress.createMysterAddress(ip);
        } catch (UnknownHostException ex) {
            throw new UnexpectedException(ex);
        }
        
        try (MysterSocket socket = MysterSocketFactory.makeStreamConnection(mysterAddress)) {

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

            final int listFileProportion = 5;
            String[] files = new String[numberoffiles];
            for (int i = 0; i < numberoffiles; i++) {
                files[i] = in.readUTF();
                if (i % 100 == 0) {
                    msg.say("Downloading file names: " + lookup.lookup(type) + " " + ((i * listFileProportion) / numberoffiles) + "%");
                }

            }

            try {
                final int[] counter = new int[] { 0 };
                StandardSuiteStream
                        .getFileStatsBatch(socket,
                                           Util.map(Arrays.asList(files),
                                                    fileName -> new MysterFileStub(mysterAddress,
                                                                                   type,
                                                                                   fileName))
                                                   .toArray(new MysterFileStub[] {}))
                        .setInvoker(Invoker.EDT)
                        .addResultListener(messagePacks -> {
                            FileRecord[] records = new FileRecord[files.length];

                            for (int i = 0; i < files.length; i++) {
                                records[i] = new FileRecord(files[i], messagePacks.get(i));
                            }

                            listener.addItemsToFileList(records);
                        })
                        .addPartialResultListener(r -> {
                            var c = counter[0]++;
                            if ( c %10 != 0) {
                                return;
                            }
                            msg.say("Downloading file metadata: " + lookup.lookup(type) + " "
                                    + ((listFileProportion+( c* (100-listFileProportion)) / numberoffiles)) + "%");})
                        .get();
            } catch (InterruptedException ex) {
                return;
            } catch (ExecutionException ex) {
                var cause = ex.getCause();
                
                if (cause instanceof IOException ioException) {
                    throw ioException;
                } else {
                    throw new UnexpectedException(ex);
                }
            }

            
            out.writeInt(2); // 2 is disconnect
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