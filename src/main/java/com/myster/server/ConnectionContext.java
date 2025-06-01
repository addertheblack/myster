package com.myster.server;

import com.myster.filemanager.FileTypeListManager;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.server.transferqueue.TransferQueue;

public record ConnectionContext(MysterSocket socket,
                                MysterAddress serverAddress,
                                Object sectionObject,
                                TransferQueue transferQueue,
                                FileTypeListManager fileManager) {
    
    public ConnectionContext withSectionObject(Object newSectionObject) {
        return new ConnectionContext(socket, serverAddress, newSectionObject, transferQueue, fileManager);
    }
}