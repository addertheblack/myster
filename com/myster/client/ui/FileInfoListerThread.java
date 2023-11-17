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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.myster.client.stream.StandardSuite;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.search.MysterFileStub;
import com.myster.util.MysterThread;

public class FileInfoListerThread extends MysterThread {
    private ClientWindow w;

    private MysterSocket socket = null;
    
    public FileInfoListerThread(ClientWindow w) {
        this.w = w;
    }

    public void run() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            return;
        }

        FileInfoListerThread msg = this;

        try {
            msg.say("Connecting to server...");
            if (endFlag)
                return;
            socket = MysterSocketFactory
                    .makeStreamConnection(new MysterAddress(w.getCurrentIP()));
            msg.say("Getting file information...");

            if (endFlag)
                return;
            RobustMML mml = new RobustMML(StandardSuite.getFileStats(socket,
                    new MysterFileStub(new MysterAddress(w.getCurrentIP()), w
                            .getCurrentType(), w.getCurrentFile())));

            msg.say("Parsing file information...");

            Map<String, String> keyvalue = new HashMap<String, String>();
            keyvalue.put("File Name", w.getCurrentFile());

            listDir(mml, keyvalue, "/", "");

            showFileStats(keyvalue);

            msg.say("Idle...");

        } catch (IOException ex) {
            msg.say("Transmission errorm could not get File Stats.");
        } finally {
            try {
                socket.close();
            } catch (Exception ex) {
                // nothing
            }
        }
    }

    private void listDir(RobustMML mml, Map<String, String> keyValue, String directory,
            String prefix) {
        List<String> dirList = mml.list(directory);

        if (dirList == null)
            return;

        for (int i = 0; i < dirList.size(); i++) {
            String name = dirList.get(i);

            if (name == null)
                return;

            String newPath = directory + name;

            if (mml.isADirectory(newPath + "/")) {
                keyValue.put(name, " ->");
                listDir(mml, keyValue, newPath + "/", prefix + "  ");
            } else {
                keyValue.put(prefix + (dirList.get(i)), mml
                .get(newPath));
            }
        }
    }
    
    private synchronized void showFileStats(final Map<String, String> keyValue) {
        if (endFlag)
            return;
        w.showFileStats(keyValue);
    }
    
    private synchronized void say(final String message) {
        if (endFlag)
            return;
        w.say(message);
    }
    
    public synchronized void flagToEnd() {
        endFlag = true;
        try {
           socket.close();
        } catch (Exception ex){
            // nothing
        }
        
        
        interrupt();
    }
  
    public synchronized void end() {
        flagToEnd();
        try {
            join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            
            throw new IllegalStateException("Unexpected InterruptedException");
        }
    }
}