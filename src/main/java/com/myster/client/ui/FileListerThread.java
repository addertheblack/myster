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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

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
                final long[] startTime = new long[] { System.currentTimeMillis() };
                final List<FileRecord> records = new ArrayList<>();
                StandardSuiteStream
                        .getFileStatsBatch(socket,
                                           Util.map(Arrays.asList(files),
                                                    fileName -> new MysterFileStub(mysterAddress,
                                                                                   type,
                                                                                   fileName))
                                                   .toArray(new MysterFileStub[] {}))
                        .setInvoker(Invoker.EDT)
                        .addResultListener(_ -> {
                            listener.addItemsToFileList(records.toArray(new FileRecord[] {}));
                        })
                        .addPartialResultListener(r -> {
                            records.add(new FileRecord(files[counter[0]], r));
                            
                            // if it has been more than a second .... then..
                            long currentTime = System.currentTimeMillis();
                            if ((currentTime - startTime[0]) > 1000) {
                                listener.addItemsToFileList(records.toArray(new FileRecord[] {}));
                                records.clear();
                                startTime[0] = currentTime;
                            }
                            
                            
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

            msg.say("Requesting File List: " + lookup.lookup(type) + " Complete.");
            msg.say("Idle...");
            
            out.writeInt(2); // 2 is disconnect
            in.read();
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