package com.myster.bandwidth;

import java.io.IOException;
import java.io.InputStream;

public class ThrottledInputStream extends InputStream {
    InputStream in;

    public ThrottledInputStream(InputStream in) {
        this.in = in;
    }

    public int read() throws IOException {
        while (BandwidthManager.requestBytesIncoming(1) != 1) {
        }
        return in.read();
    }

    public int read(byte b[]) throws IOException {
        return in.read(b);//, 0, b.length);
    }

    public int read(byte b[], int off, int len) throws IOException {
        int amountRead = in.read(b, off, len);//bytesDownloadable);

        if (amountRead > 0) {
            //System.out.println(""+amountRead);
            BandwidthManager.requestBytesIncoming(amountRead);
        }

        return amountRead;
    }

    public long skip(long n) throws IOException {
        return in.skip(n); //bad.
    }

    public int available() throws IOException {
        return in.available();
    }

    public void close() throws IOException {
        in.close();
    }

    public synchronized void mark(int readlimit) {
        in.mark(readlimit);
    }

    public synchronized void reset() throws IOException {
        in.reset();
    }

    public boolean markSupported() {
        return in.markSupported();
    }
}