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

public class RequestSearchThread extends ServerThread {

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

        byte[] type = new byte[4];
        String searchstring;

        in.readFully(type);
        searchstring = in.readUTF();

        String[] stringarray;

        stringarray = FileTypeListManager.getInstance().getDirList(new MysterType(type),
                searchstring);

        dispatcher.fireEvent(new ServerSearchEvent(ServerSearchEvent.REQUESTED, new MysterAddress(
                c.socket.getInetAddress()), NUMBER, searchstring, new MysterType(type), null));

        if (stringarray != null) {
            dispatcher.fireEvent(new ServerSearchEvent(ServerSearchEvent.RESULTS,
                    new MysterAddress(c.socket.getInetAddress()), NUMBER, searchstring, new MysterType(
                            type), stringarray));
            for (int j = 0; j < stringarray.length; j++) {
                out.writeUTF(stringarray[j]);

            }
        }

        out.writeUTF("");
    }
}