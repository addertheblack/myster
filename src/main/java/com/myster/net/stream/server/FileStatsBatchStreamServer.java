/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2025
 */

package com.myster.net.stream.server;

import java.io.IOException;

import com.myster.filemanager.FileItem;
import com.myster.mml.MessagePack;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.type.MysterType;

public class FileStatsBatchStreamServer extends ServerStreamHandler {
    public static final int NUMBER = 177;

    public int getSectionNumber() {
        return NUMBER;
    }

    /**
     * in MysterType (file type)
     * in int (number of files)
     * for each file:
     *   in String (filename)
     * 
     * out byte (protocol check = 1)
     * for each file:
     *   out MessagePack (file stats or empty if not found)
     */
    public void section(ConnectionContext context) throws IOException {
        try {
            MysterDataInputStream in = context.socket().in;
            MysterDataOutputStream out = context.socket().out;

            MysterType type = in.readType();
            int count = in.readInt();

            // Read all filenames first
            String[] filenames = new String[count]; 
            for (int i = 0; i < count; i++) {
                filenames[i] = in.readUTF(); // annoying mem spike
            }

            // Process and send all responses
            for (String filename : filenames) {
                FileItem fileItem = context.fileManager().getFileItem(type, filename);
                MessagePack messagePack = (fileItem == null) 
                    ? MessagePack.newEmpty() 
                    : fileItem.getMessagePackRepresentation();

                out.writeMessagePack(messagePack);
            }

            out.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
            throw ex;
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            throw ex;
        }
    }
}
