/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.net.stream.server;

import java.io.IOException;
import java.util.logging.Logger;

import com.myster.filemanager.FileTypeListManager;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;

public class RequestDirThread extends ServerStreamHandler {
    public static final int NUMBER = 78;

    private static final Logger log = Logger.getLogger(RequestDirThread.class.getName());

    public int getSectionNumber() {
        return NUMBER;
    }

    public void section(ConnectionContext context) throws IOException {
        MysterDataInputStream in = context.socket().getInputStream();
        MysterDataOutputStream out = context.socket().getOutputStream();

        var type = in.readType();
        log.info("Reading: " + type);
        String[] array = context.fileManager().getDirList(type);

        if (array == null) {
            log.info("Null Pointer");
            out.writeInt(0);
        } else {
            log.info("Sending: " + array.length + " Strings");
            out.writeInt(array.length);

            for (int j = 0; j < array.length; j++) {
                out.writeUTF(array[j]);
                log.fine("Outputting: " + array[j]);
            }
        }
    }
}