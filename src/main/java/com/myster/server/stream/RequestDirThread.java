/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.server.stream;

import java.io.IOException;
import java.util.logging.Logger;

import com.myster.client.stream.MysterDataInputStream;
import com.myster.client.stream.MysterDataOutputStream;
import com.myster.filemanager.FileTypeListManager;
import com.myster.server.ConnectionContext;

public class RequestDirThread extends ServerStreamHandler {
    public static final int NUMBER = 78;

    private static final Logger LOGGER = Logger.getLogger(RequestDirThread.class.getName());

    public int getSectionNumber() {
        return NUMBER;
    }

    public void section(ConnectionContext context) throws IOException {
        MysterDataInputStream in = context.socket().getInputStream();
        MysterDataOutputStream out = context.socket().getOutputStream();

        var type = in.readType();
        LOGGER.info("Reading: " + type);
        String[] array = context.fileManager().getDirList(type);

        if (array == null) {
            LOGGER.info("Null Pointer");
            out.writeInt(0);
        } else {
            LOGGER.info("Sending: " + array.length + " Strings");
            out.writeInt(array.length);

            for (int j = 0; j < array.length; j++) {
                out.writeUTF(array[j]);
                LOGGER.fine("Outputting: " + array[j]);
            }
        }
    }
}