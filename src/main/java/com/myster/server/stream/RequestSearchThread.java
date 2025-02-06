/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */
package com.myster.server.stream;

import java.io.IOException;

import com.myster.client.stream.MysterDataInputStream;
import com.myster.client.stream.MysterDataOutputStream;
import com.myster.filemanager.FileTypeListManager;
import com.myster.net.MysterAddress;
import com.myster.server.ConnectionContext;
import com.myster.server.event.ServerSearchDispatcher;
import com.myster.server.event.ServerSearchEvent;
import com.myster.type.MysterType;

public class RequestSearchThread extends ServerStreamHandler {

    public static final int NUMBER = 35;

    public int getSectionNumber() {
        return NUMBER;
    }

    public Object getSectionObject() {
        return new ServerSearchDispatcher();
    }

    /**
     * Protocal: Send 35 Send Type (4 bytes) get Set of strings of names of files that match.
     */

    public void section(ConnectionContext c) throws IOException {
        MysterDataInputStream in = c.socket.getInputStream();
        MysterDataOutputStream out = c.socket.getOutputStream();

        ServerSearchDispatcher dispatcher = (ServerSearchDispatcher) (c.sectionObject);

        String searchstring;

        var type = in.readType();
        searchstring = in.readUTF();

        String[] stringarray;

        stringarray = FileTypeListManager.getInstance().getDirList(type, searchstring);

        dispatcher.fire()
                .searchRequested(new ServerSearchEvent(new MysterAddress(c.socket.getInetAddress()),
                                                       NUMBER,
                                                       searchstring,
                                                       type,
                                                       null));

        if (stringarray != null) {
            dispatcher.fire().searchResult(new ServerSearchEvent(
                                                                 new MysterAddress(c.socket
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