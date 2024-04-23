package com.myster.server;

import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.server.transferqueue.TransferQueue;

public class ConnectionContext { //struct
    public MysterSocket socket;

    /**
     * Remote server to which this socket is connected.
     */
    public MysterAddress serverAddress;

    public Object sectionObject;

    public TransferQueue transferQueue;
}