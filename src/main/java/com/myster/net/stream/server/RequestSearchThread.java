/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */
package com.myster.net.stream.server;

import java.io.IOException;

import com.myster.net.MysterAddress;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.server.event.ServerSearchDispatcher;
import com.myster.server.event.ServerSearchEvent;

public class RequestSearchThread extends ServerStreamHandler {

    public static final int NUMBER = 35;

    public int getSectionNumber() {
        return NUMBER;
    }

    public Object getSectionObject() {
        return new ServerSearchDispatcher();
    }

    /**
     * Protocal: Send 35 Send Type (4 bytes) get Set of strings of names of
     * files that match.
     */

    public void section(ConnectionContext context) throws IOException {
        MysterDataInputStream in = context.socket().getInputStream();
        MysterDataOutputStream out = context.socket().getOutputStream();

        ServerSearchDispatcher dispatcher = (ServerSearchDispatcher) (context.sectionObject());

        String searchstring;

        var type = in.readType();
        searchstring = in.readUTF();

        String[] stringarray;

        stringarray = context.fileManager().getDirList(type, searchstring);

        dispatcher.fire().searchRequested(new ServerSearchEvent(
                                                                new MysterAddress(context.socket()
                                                                        .getInetAddress()),
                                                                NUMBER,
                                                                searchstring,
                                                                type,
                                                                null));

        if (stringarray != null) {
            dispatcher.fire().searchResult(new ServerSearchEvent(
                                                                 new MysterAddress(context.socket()
                                                                         .getInetAddress()),
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