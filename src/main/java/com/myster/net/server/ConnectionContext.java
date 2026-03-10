package com.myster.net.server;

import java.util.Optional;

import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Cid128;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.stream.server.transferqueue.TransferQueue;

/**
 * Carries all per-connection state through the section-handler dispatch chain.
 *
 * <p>{@code callerCid} is present only when the connection was upgraded to TLS via
 * {@link com.myster.net.TLSSocket#STLS_CONNECTION_SECTION} and the peer certificate was
 * successfully read. Plaintext connections always carry {@link Optional#empty()}.
 */
public record ConnectionContext(MysterSocket socket,
                                MysterAddress serverAddress,
                                Object sectionObject,
                                TransferQueue transferQueue,
                                FileTypeListManager fileManager,
                                Optional<Cid128> callerCid) {

    public ConnectionContext withSectionObject(Object newSectionObject) {
        return new ConnectionContext(socket, serverAddress, newSectionObject, transferQueue, fileManager, callerCid);
    }
}