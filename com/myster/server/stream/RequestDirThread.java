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

public class RequestDirThread extends ServerThread {
    public static final int NUMBER = 78;

    public int getSectionNumber() {
        return NUMBER;
    }

    public void section(ConnectionContext context) throws IOException {
        byte[] b = new byte[4];

        DataInputStream in = new DataInputStream(context.socket
                .getInputStream());
        DataOutputStream out = new DataOutputStream(context.socket
                .getOutputStream());

        in.readFully(b);
        System.out.println("Reading: " + (new String(b)));
        String[] array = FileTypeListManager.getInstance().getDirList(
                new MysterType(b));
        if (array == null) {
            System.out.println("Null Pointer");
            out.writeInt(0);
        } else {
            System.out.println("Sending: " + array.length + " Strings");
            out.writeInt(array.length);

            for (int j = 0; j < array.length; j++) {
                out.writeUTF(array[j]);
                //System.out.println("Outputting: "+array[j]);
            }
        }
    }
}