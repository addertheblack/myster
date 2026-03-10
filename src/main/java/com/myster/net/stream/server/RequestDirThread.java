package com.myster.net.stream.server;

import java.io.IOException;
import java.util.logging.Logger;

import com.myster.access.AccessEnforcementUtils;
import com.myster.access.AccessListReader;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.type.MysterType;

/**
 * Section 78 — returns the file listing for a given type.
 *
 * <p>Non-members of a private type receive an empty response ({@code writeInt(0)}).
 */
public class RequestDirThread extends ServerStreamHandler {
    public static final int NUMBER = 78;

    private static final Logger log = Logger.getLogger(RequestDirThread.class.getName());

    private final AccessListReader accessListReader;

    public RequestDirThread(AccessListReader accessListReader) {
        this.accessListReader = accessListReader;
    }

    public int getSectionNumber() {
        return NUMBER;
    }

    public void section(ConnectionContext context) throws IOException {
        MysterDataInputStream in = context.socket().getInputStream();
        MysterDataOutputStream out = context.socket().getOutputStream();

        MysterType type = in.readType();
        log.info("Reading: " + type);

        if (!AccessEnforcementUtils.isAllowed(type, context.callerCid(), accessListReader)) {
            out.writeInt(0);
            return;
        }

        String[] array = context.fileManager().getDirList(type);

        if (array == null) {
            log.info("Null Pointer");
            out.writeInt(0);
        } else {
            log.info("Sending: " + array.length + " Strings");
            out.writeInt(array.length);

            for (int j = 0; j < array.length; j++) {
                out.writeUTF(array[j]);
                log.fine("Outputting: " + array[j]);
            }
        }
    }
}

