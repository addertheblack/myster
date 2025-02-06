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

import com.myster.filemanager.FileTypeListManager;
import com.myster.server.ConnectionContext;
import com.myster.type.MysterType;

public class FileTypeLister extends ServerStreamHandler {
    public static final int NUMBER = 74;

    public int getSectionNumber() {
        return NUMBER;
    }

    public void section(ConnectionContext context) throws IOException {
        MysterType[] types = FileTypeListManager.getInstance()
                .getFileTypeListing();

        context.socket.out.writeInt(types.length);

        for (int i = 0; i < types.length; i++) {
            context.socket.out.writeType(types[i]);
        }
    }
}