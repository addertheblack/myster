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

import com.myster.filemanager.FileItem;
import com.myster.filemanager.FileTypeListManager;
import com.myster.mml.MessagePak;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.type.MysterType;

public class FileStatsStreamServer extends ServerStreamHandler {
    public static final int NUMBER = 77;

    public int getSectionNumber() {
        return NUMBER;
    }

    /**
     * in Filetype in FileName
     */
    public void section(ConnectionContext context) throws IOException {
        try {
            MysterDataInputStream in = context.socket().in;
            MysterDataOutputStream out = context.socket().out;

            MysterType type = in.readType();
            String filename = in.readUTF();

            FileItem fileItem = context.fileManager().getFileItem(
                    type, filename);
            MessagePak messagePack;

            if (fileItem == null) { //file not found
                messagePack = MessagePak.newEmpty();
            } else {
                messagePack = fileItem.getMessagePackRepresentation();
            }

            out.writeMessagePack(messagePack);
        } catch (IOException ex) {
            ex.printStackTrace();
            throw ex;
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            throw ex;
        }
    }


    //*/
}