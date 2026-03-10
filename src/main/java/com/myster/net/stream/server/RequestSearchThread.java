package com.myster.net.stream.server;

import java.io.IOException;

import com.myster.access.AccessEnforcementUtils;
import com.myster.access.AccessListReader;
import com.myster.net.MysterAddress;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.server.event.ServerSearchDispatcher;
import com.myster.server.event.ServerSearchEvent;
import com.myster.type.MysterType;

/**
 * Section 35 — returns filenames matching a search string for a given type.
 *
 * <p>Non-members of a private type receive an empty response (the empty-string terminator only).
 */
public class RequestSearchThread extends ServerStreamHandler {

    public static final int NUMBER = 35;

    private final AccessListReader accessListReader;

    public RequestSearchThread(AccessListReader accessListReader) {
        this.accessListReader = accessListReader;
    }

    public int getSectionNumber() {
        return NUMBER;
    }

    public Object getSectionObject() {
        return new ServerSearchDispatcher();
    }

    /**
     * Protocol: Send 35 Send Type (4 bytes) get Set of strings of names of
     * files that match.
     */
    public void section(ConnectionContext context) throws IOException {
        MysterDataInputStream in = context.socket().getInputStream();
        MysterDataOutputStream out = context.socket().getOutputStream();

        ServerSearchDispatcher dispatcher = (ServerSearchDispatcher) (context.sectionObject());

        MysterType type = in.readType();
        String searchstring = in.readUTF();

        if (!AccessEnforcementUtils.isAllowed(type, context.callerCid(), accessListReader)) {
            out.writeUTF("");
            return;
        }

        String[] stringarray = context.fileManager().getDirList(type, searchstring);

        dispatcher.fire().searchRequested(new ServerSearchEvent(
                new MysterAddress(context.socket().getInetAddress()),
                NUMBER,
                searchstring,
                type,
                null));

        if (stringarray != null) {
            dispatcher.fire().searchResult(new ServerSearchEvent(
                    new MysterAddress(context.socket().getInetAddress()),
                    NUMBER,
                    searchstring,
                    type,
                    stringarray));
            for (int j = 0; j < stringarray.length; j++) {
                out.writeUTF(stringarray[j]);
            }
        }

        out.writeUTF("");
    }
}

