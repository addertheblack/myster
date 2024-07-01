package com.myster.server.stream;

import java.io.IOException;

import com.myster.client.stream.MysterDataInputStream;
import com.myster.client.stream.MysterDataOutputStream;
import com.myster.net.MysterAddress;
import com.myster.server.ConnectionContext;
import com.myster.tracker.Tracker;
import com.myster.tracker.MysterServer;
import com.myster.type.MysterType;

public class MysterServerLister extends ServerStreamHandler {
    public static final int NUMBER = 10;
    
    private final Tracker tracker;

    public MysterServerLister(Tracker tracker) {
        this.tracker = tracker;
    }

    public int getSectionNumber() {
        return NUMBER;
    }

    /**
     * Protocol: Send 10 (done) Send TYPE(4 bytes) get String array (get a bunch
     * of strings) NO length sent
     */
    public void section(ConnectionContext context) throws IOException {
        MysterDataInputStream in = context.socket.in;
        MysterDataOutputStream out = context.socket.getOutputStream();

        MysterServer[] topten;

        byte[] type = new byte[4];
        in.readFully(type);

        tracker.addIp(new MysterAddress(context.socket.getInetAddress()));

        topten = tracker.getTop(new MysterType(type), 100);
        
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
