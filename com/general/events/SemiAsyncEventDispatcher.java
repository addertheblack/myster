package com.general.events;

import java.util.Vector;

import com.general.util.Channel;
import com.myster.util.MysterThread;

/**
 * Warning this call creates yet another thread..
 */

public final class SemiAsyncEventDispatcher extends EventDispatcher { //untested
    MysterThread thread;

    Channel channel = new Channel();

    public SemiAsyncEventDispatcher() {
        thread = new FireEvent(channel, listeners);
        thread.start();
    }

    public void fireEvent(GenericEvent e) {
        channel.in.put(e);
    }

    protected void finalize() throws Throwable {
        try {
            thread.end();
        } finally {
            super.finalize();
        }
    }

    private static class FireEvent extends MysterThread {
        Channel channel;

        Vector listeners;

        public FireEvent(Channel c, Vector l) {
            channel = c;
            listeners = l;
        }

        public void run() {
            do {
                GenericEvent e = (GenericEvent) (channel.out.get());

                synchronized (listeners) {
                    for (int i = 0; i < listeners.size(); i++) {
                        try {
                            ((EventListener) (listeners.elementAt(i)))
                                    .fireEvent(e);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            } while (true);
        }
    }
}