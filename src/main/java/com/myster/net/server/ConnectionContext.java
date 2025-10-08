package com.myster.net.server;

import com.myster.filemanager.FileTypeListManager;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.stream.server.transferqueue.TransferQueue;

public record ConnectionContext(MysterSocket socket,
                                MysterAddress serverAddress,
                                Object sectionObject,
                                TransferQueue transferQueue,
                                FileTypeListManager fileManager) {
    
    public ConnectionContext withSectionObject(Object newSectionObject) {
        return new ConnectionContext(socket, serverAddress, newSectionObject, transferQueue, fileManager);
    }
}