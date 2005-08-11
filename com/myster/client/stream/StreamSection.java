package com.myster.client.stream;

import java.io.IOException;

import com.general.thread.CancellableCallable;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;

/**
 * Use this class if you want to do a streamed connection but don't want to do
 * it in the standard way allowed by by the convenience methods in
 * StandardSuite.
 */
public abstract class StreamSection implements CancellableCallable {
    protected MysterAddress address;

    protected MysterSocket socket = null;

    public StreamSection(MysterAddress address) {
        this.address = address;
    }

    /**
     * Instead of over-riding call, implementors of this class should over-ride
     * doSection() as we need to make sure the socket object is opened and
     * closed correctly.
     */
    public final Object call() throws IOException {
        try {
            socket = MysterSocketFactory.makeStreamConnection(address);
            return doSection();
        } finally {
            try {
                socket.close();
            } catch (Exception ex) {
                //ignore
            }
        }
    }

    /**
     * An over-ridden version of call() takes care of creating/destroying the
     * stream for you.
     * 
     * @throws IOException
     */
    protected abstract Object doSection() throws IOException;

    /**
     * Closes the socket. Subclasses should call this method using super if they
     * over-ride this method.
     */
    public void cancel() {
        if (socket == null)
            return;

        try {
            socket.close();
        } catch (IOException ex) {
        }
    }
}