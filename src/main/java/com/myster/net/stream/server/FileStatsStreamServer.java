package com.myster.net.stream.server;

import java.io.IOException;

import com.myster.access.AccessEnforcementUtils;
import com.myster.access.AccessListReader;
import com.myster.filemanager.FileItem;
import com.myster.mml.MessagePak;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.type.MysterType;

/**
 * Section 77 — returns metadata for a single named file.
 *
 * <p>Non-members of a private type receive an empty {@link MessagePak} (same as "file not found").
 */
public class FileStatsStreamServer extends ServerStreamHandler {
    public static final int NUMBER = 77;

    private final AccessListReader accessListReader;

    public FileStatsStreamServer(AccessListReader accessListReader) {
        this.accessListReader = accessListReader;
    }

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

            if (!AccessEnforcementUtils.isAllowed(type, context.callerCid(), accessListReader)) {
                out.writeMessagePack(MessagePak.newEmpty());
                return;
            }

            FileItem fileItem = context.fileManager().getFileItem(type, filename);
            MessagePak messagePack = (fileItem == null) ? MessagePak.newEmpty()
                                                        : fileItem.getMessagePackRepresentation();
            out.writeMessagePack(messagePack);
        } catch (IOException ex) {
            ex.printStackTrace();
            throw ex;
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            throw ex;
        }
    }
}

