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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.filemanager.FileTypeListManager;
import com.myster.server.ConnectionContext;
import com.myster.type.MysterType;

public class FileTypeLister extends ServerThread {
    public static final int NUMBER = 79;

    public int getSectionNumber() {
        return NUMBER;
    }

    public void section(ConnectionContext context) throws IOException {
        MysterType[] temp;

        DataInputStream in = context.socket
                .getInputStream();
        DataOutputStream out = context.socket
                .getOutputStream();

        temp = FileTypeListManager.getInstance().getFileTypeListing();

        for (int i = 0; i < temp.length; i++) {
            out.writeUTF(temp[i].toString()); //BAD protocol
        }

        out.writeUTF("");
    }
}