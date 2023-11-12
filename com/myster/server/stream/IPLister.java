package com.myster.server.stream;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.net.MysterAddress;
import com.myster.server.ConnectionContext;
import com.myster.tracker.IPListManager;
import com.myster.tracker.MysterServer;
import com.myster.type.MysterType;

public class IPLister extends ServerThread {
    public static final int NUMBER = 10;
    
    private final IPListManager ipListManager;

    public IPLister(IPListManager ipListManager) {
        this.ipListManager = ipListManager;
    }

    public int getSectionNumber() {
        return NUMBER;
    }

    /**
     * Protocal: Send 10 (done) Send TYPE(4 bytes) get String array (get a bunch
     * of strings) NO length sent
     */

    public void section(ConnectionContext context) throws IOException {
        DataInputStream in = context.socket.in;
        DataOutputStream out = context.socket
                .getOutputStream();

        MysterServer[] topten;

        byte[] type = new byte[4];
        in.readFully(type);

        ipListManager.addIP(
                new MysterAddress(context.socket.getInetAddress()));

        topten = ipListManager.getTop(
                new MysterType(type), 100);
        if (topten != null) {
            for (int i = 0; i < topten.length; i++) {
                if (topten[i] == null)
                    break;
                out.writeUTF(topten[i].getAddress().getIP());
            }
        }
        out.writeUTF(""); //"" Signals the end of the list!
    }

}