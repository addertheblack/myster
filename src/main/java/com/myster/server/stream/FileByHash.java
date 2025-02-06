package com.myster.server.stream;

/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

import java.io.IOException;

import com.myster.hash.FileHash;
import com.myster.hash.SimpleFileHash;
import com.myster.server.ConnectionContext;
import com.myster.type.MysterType;

public class FileByHash extends ServerStreamHandler {
    public static final int NUMBER = 150;

    public static final String HASH_TYPE = "/Hash Type";

    public static final String HASH = "/Hash";

    public int getSectionNumber() {
        return NUMBER;
    }

    public void section(ConnectionContext context) throws IOException {
        try {
            MysterType type = context.socket.in.readType();

            FileHash md5Hash = null;

            for (;;) { //get hash name, get hash length, get hash data until
                // hashname is ""
                String hashType = context.socket.in.readUTF();
                if (hashType.equals("")) {
                    break;
                }
                int lengthOfHash = 0xffff & context.socket.in.readShort();

                byte[] hashBytes = new byte[lengthOfHash];
                context.socket.in.readFully(hashBytes, 0, hashBytes.length);

                if (hashType.equalsIgnoreCase(com.myster.hash.HashManager.MD5)) {
                    md5Hash = SimpleFileHash.buildFileHash(hashType, hashBytes);
                }
            }

            com.myster.filemanager.FileItem file = null;

            if (md5Hash != null) {
                file = com.myster.filemanager.FileTypeListManager.getInstance().getFileFromHash(
                        type, md5Hash);
            }

            if (file == null) {
                context.socket.out.writeUTF("");
            } else {
                context.socket.out.writeUTF(file.getName());
            }
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            throw ex;
        }
    }
}