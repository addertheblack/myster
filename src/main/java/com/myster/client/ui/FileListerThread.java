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
import java.util.List;
import java.util.concurrent.CancellationException;

import com.general.thread.Invoker;
import com.general.util.UnexpectedException;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.net.stream.client.MysterSocketFactory;
import com.myster.net.stream.client.StandardSuiteStream;
import com.myster.net.stream.client.StandardSuiteStream.FileCallback;
import com.myster.net.stream.client.StandardSuiteStream.NamedMetaData;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;

public class FileListerThread extends MysterThread {
//    private static final Logger log = Logger.getLogger(FileListerThread.class.getName());
    
    public record FileRecord(String file, MessagePak metaData) {}
    
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

            final int[] counter = new int[] { 0 };
            final int[] max = new int[] { 0 };
            final long[] startTime = new long[] { System.currentTimeMillis() };
            final List<FileRecord> records = new ArrayList<>();
            StandardSuiteStream.getAllFilesAndMetadata(socket, type, new FileCallback() {
                @Override
                public void numberOfFiles(int numberOfFiles) {
                    Invoker.EDT.invoke(() -> {
                        max[0] = numberOfFiles;
                    });
                }

                @Override
                public void file(NamedMetaData f) {
                    records.add(new FileRecord(f.name(), f.pak()));

                    // if it has been more than a second .... then..
                    long currentTime = System.currentTimeMillis();
                    if ((currentTime - startTime[0]) > 1000 || max[0] == (counter[0] + 1)) {
                        var rr = records.toArray(new FileRecord[] {});
                        Invoker.EDT.invoke(() -> {
                            listener.addItemsToFileList(rr);
                        });
                        records.clear();
                        startTime[0] = currentTime;
                    }

                    var c = counter[0]++;
                    if (c % 10 != 0) {
                        return;
                    }

                    var m = max[0];
                    
                    if (endFlag) {
                        throw new CancellationException();
                    }
                    
                    Invoker.EDT.invoke(() -> {
                        if (m == 0) {
                            return;
                        }
                        msg.say("Downloading file metadata: " + lookup.lookup(type) + " "
                                + (c * (100)) / m + "%");
                    });

                }
            });

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