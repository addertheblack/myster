package com.myster.net.stream.server;

import java.io.IOException;

import com.myster.access.AccessEnforcementUtils;
import com.myster.access.AccessListReader;
import com.myster.net.MysterAddress;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;

/**
 * Section 10 — returns a list of known servers for a given type.
 *
 * <p>Non-members of a private type receive only the empty-string terminator (no server addresses).
 */
public class MysterServerLister extends ServerStreamHandler {
    public static final int NUMBER = 10;
    
    private final Tracker tracker;
    private final AccessListReader accessListReader;

    public MysterServerLister(Tracker tracker, AccessListReader accessListReader) {
        this.tracker = tracker;
        this.accessListReader = accessListReader;
    }

    public int getSectionNumber() {
        return NUMBER;
    }

    /**
     * Protocol: Send 10 (done) Send TYPE(4 bytes) get String array (get a bunch
     * of strings) NO length sent
     */
    public void section(ConnectionContext context) throws IOException {
        MysterDataInputStream in = context.socket().in;
        MysterDataOutputStream out = context.socket().getOutputStream();

        tracker.addIp(new MysterAddress(context.socket().getInetAddress()));

        MysterType type = in.readType();

        if (!AccessEnforcementUtils.isAllowed(type, context.callerCid(), accessListReader)) {
            out.writeUTF("");
            return;
        }

        MysterServer[] topten = tracker.getTop(type, 100);

        if (topten != null) {
            for (int i = 0; i < topten.length; i++) {
                if (topten[i] == null)
                    break;

                var addresses = topten[i].getUpAddresses();

                for (MysterAddress address : addresses) {
                    out.writeUTF(address.toString());
                }
            }
        }

        out.writeUTF(""); //"" Signals the end of the list!
    }
}


