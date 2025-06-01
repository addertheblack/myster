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

import com.myster.client.stream.MysterDataInputStream;
import com.myster.client.stream.MysterDataOutputStream;
import com.myster.filemanager.FileItem;
import com.myster.filemanager.FileTypeListManager;
import com.myster.mml.MML;
import com.myster.server.ConnectionContext;
import com.myster.type.MysterType;

public class FileInfoLister extends ServerStreamHandler {
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
            MML mml;

            if (fileItem == null) { //file not found
                mml = new MML();
            } else {
                mml = fileItem.getMMLRepresentation();
            }

            out.writeUTF(mml.toString());
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