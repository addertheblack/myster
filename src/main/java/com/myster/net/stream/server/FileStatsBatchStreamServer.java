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
 * Section 177 — returns metadata for a batch of files of a given type.
 *
 * <p>Non-members of a private type receive a zero-entry response.
 */
public class FileStatsBatchStreamServer extends ServerStreamHandler {
    public static final int NUMBER = 177;

    private final AccessListReader accessListReader;

    public FileStatsBatchStreamServer(AccessListReader accessListReader) {
        this.accessListReader = accessListReader;
    }

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

            if (!AccessEnforcementUtils.isAllowed(type, context.callerCid(), accessListReader)) {
                out.writeInt(0);
                return;
            }

            String[] filenames = context.fileManager().getFileTypeList(type).getFileListAsStrings();
            out.writeInt(filenames.length);

            for (String filename : filenames) {
                out.writeUTF(filename);

                FileItem fileItem = context.fileManager().getFileItem(type, filename);
                MessagePak messagePack = (fileItem == null)
                        ? MessagePak.newEmpty()
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


