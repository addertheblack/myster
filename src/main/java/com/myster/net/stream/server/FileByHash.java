package com.myster.net.stream.server;

import java.io.IOException;

import com.myster.access.AccessEnforcementUtils;
import com.myster.access.AccessListReader;
import com.myster.hash.FileHash;
import com.myster.hash.SimpleFileHash;
import com.myster.net.server.ConnectionContext;
import com.myster.type.MysterType;

/**
 * Section 150 — resolves a file hash to a filename for a given type.
 *
 * <p>Non-members of a private type receive the "not found" response ({@code writeUTF("")}).
 */
public class FileByHash extends ServerStreamHandler {
    public static final int NUMBER = 150;

    public static final String HASH_TYPE = "/Hash Type";

    public static final String HASH = "/Hash";

    private final AccessListReader accessListReader;

    public FileByHash(AccessListReader accessListReader) {
        this.accessListReader = accessListReader;
    }

    public int getSectionNumber() {
        return NUMBER;
    }

    public void section(ConnectionContext context) throws IOException {
        try {
            MysterType type = context.socket().in.readType();

            FileHash md5Hash = null;

            for (;;) {
                String hashType = context.socket().in.readUTF();
                if (hashType.equals("")) {
                    break;
                }
                int lengthOfHash = 0xffff & context.socket().in.readShort();

                byte[] hashBytes = new byte[lengthOfHash];
                context.socket().in.readFully(hashBytes, 0, hashBytes.length);

                if (hashType.equalsIgnoreCase(com.myster.hash.HashManager.MD5)) {
                    md5Hash = SimpleFileHash.buildFileHash(hashType, hashBytes);
                }
            }

            if (!AccessEnforcementUtils.isAllowed(type, context.callerCid(), accessListReader)) {
                context.socket().out.writeUTF("");
                return;
            }

            com.myster.filemanager.FileItem file = null;

            if (md5Hash != null) {
                file = context.fileManager().getFileFromHash(type, md5Hash);
            }

            if (file == null) {
                context.socket().out.writeUTF("");
            } else {
                context.socket().out.writeUTF(file.getName());
            }
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            throw ex;
        }
    }
}

