package com.myster.client.stream;

import java.io.IOException;
import java.util.concurrent.CancellationException;

import com.general.thread.CancellableCallable;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;

/**
 * Use this class if you want to do a streamed connection but don't want to do
 * it in the standard way allowed by by the convenience methods in
 * StandardSuite.
 */
public abstract class StreamSection<T> implements CancellableCallable<T> {
    protected final MysterAddress address;

    protected volatile MysterSocket socket = null;
    
    protected volatile boolean cancelled = false;

    public StreamSection(MysterAddress address) {
        this.address = address;
    }

    /**
     * Instead of over-riding call, implementors of this class should over-ride
     * doSection() as we need to make sure the socket object is opened and
     * closed correctly.
     */
    public final T call() throws IOException {
        if (cancelled) {
            throw new CancellationException();
        }
        
        try {
            socket = MysterSocketFactory.makeStreamConnection(address);
            
            if (cancelled) {
                throw new CancellationException();
            }
            
            
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
    protected abstract T doSection() throws IOException;

    /**
     * Closes the socket. Subclasses should call this method using super if they
     * over-ride this method.
     */
    public void cancel() {
        cancelled = true;
        
        if (socket == null)
            return;

        try {
            socket.close();
        } catch (IOException ex) {
            // nothing
        }
    }
}