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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import com.general.util.Util;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterProtocol;
import com.myster.net.stream.client.MysterSocketFactory;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;

public class FileInfoListerThread extends MysterThread {
    private static final Logger LOGGER = Logger.getLogger(FileInfoListerThread.class.getName());
    
    public interface FileStatsListener {
        void showFileStats(final Map<String, String> keyValue);   
    }

    private final FileStatsListener listener;
    private final String addressAsString;
    private final MysterType type;
    private final String file;
    private final Sayable msg;
    private final MysterProtocol protocol;

    public FileInfoListerThread(MysterProtocol protocol,
                                FileStatsListener listener,
                                Sayable msg,
                                String address,
                                MysterType type,
                                String file) {
        this.protocol = protocol;
        this.addressAsString = address;
        this.type = type;
        this.file = file;
        this.msg = (String s) -> Util.invokeLater(() -> {
            if (endFlag) {
                return;
            }
            msg.say(s);
        });
        
        this.listener = (Map<String, String> k) -> Util.invokeLater(()-> {
            if (endFlag) {
                return;
            }
            listener.showFileStats(k);
        });
    }

    public void run() {
        if (endFlag)
            return;
        try {
            Thread.sleep(1);
        } catch (InterruptedException ex) {
            return;
        }
        
        if (endFlag)
            return;

        msg.say("Looking up address...");
        if (endFlag)
            return;

        MysterAddress address;
        try {
            address = MysterAddress.createMysterAddress(addressAsString);
        } catch (UnknownHostException _) {
            msg.say("Could not find address...");
            return;
        }

        msg.say("Connecting to server...");
        if (endFlag)
            return;
        
        MysterFileStub stub = new MysterFileStub(address, type, file);
        try {
            LOGGER.info("Doing get files stats UDP");
            parseResult(protocol.getDatagram().getFileStats(stub).get());
            
            return;
        } catch (InterruptedException _) {
            return;
        } catch (ExecutionException ex) {
            LOGGER.warning("Unexpected exception from calling protocol.getDatagram().getFileStats(stub): "
                    + ex.getCause());
        }

        try (MysterSocket socket = MysterSocketFactory.makeStreamConnection(address)) {
            msg.say("Getting file information...");

            if (endFlag)
                return;
            
            MessagePak fileStats = protocol.getStream().getFileStats(socket,
                    stub);

            parseResult(fileStats);

        } catch (IOException _) {
            msg.say("Transmission error could not get File Stats.");
        }
    }

    private void parseResult(MessagePak fileStats) {
        msg.say("Parsing file information...");

        Map<String, String> keyvalue = new LinkedHashMap<String, String>();
        keyvalue.put("File Name", file);

        listDir(fileStats, keyvalue, "/", "");

        listener.showFileStats(keyvalue);

        msg.say("Idle...");
    }

    private void listDir(MessagePak fileStats,
                         Map<String, String> keyValue,
                         String directory,
                         String prefix) {
        List<String> dirList = fileStats.list(directory);

        if (dirList == null)
            return;

        for (int i = 0; i < dirList.size(); i++) {
            String name = dirList.get(i);

            if (name == null)
                return;

            String newPath = directory + name;

            if (fileStats.isADirectory(newPath + "/")) {
                keyValue.put(name, " ->");
                listDir(fileStats, keyValue, newPath + "/", prefix + "  ");
            } else {
                keyValue.put(prefix + (dirList.get(i)), fileStats.get(newPath).orElse(null));
            }
        }
    }

    public void flagToEnd() {
        endFlag = true;

        interrupt();
    }
  
    public synchronized void end() {
        flagToEnd();
        try {
            join();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            
            throw new IllegalStateException("Unexpected InterruptedException");
        }
    }
}