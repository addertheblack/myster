/**
 * 
 * The ServerEventManager is responsible for managing events in the Sever. If a
 * connection section would like to register it's own event system, it must
 * extends from the ServerEventDispacther object. From there the new (unknown)
 * Connection Section can create it's own method, Events, Listeners or even
 * create its own sub Managers.
 * 
 * When a Connection Manager event occures, the Connection manager will pass
 * this new Connection Section's Dispatcher inside the event. From there, the
 * Listening object can register listeners to that specific connection section.
 * 
 * In most cases with simple connection sections, this approach is unnessesairy.
 * IN these cases, the programer might decide to not make a dispatcher. If this
 * is the case, the dispacther returned inside the event will be a null
 * reference (aka a null).
 *  
 */

package com.myster.server.event;

import java.util.Vector;

import com.general.events.EventDispatcher;
import com.general.events.SyncEventThreadDispatcher;
import com.general.util.Util;

public class ServerEventManager {
    //Vector dispatchers;
    Vector connectionlisteners;

    EventDispatcher operatorDispatcher;

    public ServerEventManager() {
        //dispatchers=new Vector(10,10);
        //TODO: Fix this so it works using the regular event system
        connectionlisteners = new Vector(10, 10);
        operatorDispatcher = new SyncEventThreadDispatcher();

    }

    public void addConnectionManagerListener(ConnectionManagerListener l) {
        connectionlisteners.addElement(l);
    }

    public void removeConnectionManagerListener(ConnectionManagerListener l) {
        connectionlisteners.removeElement(l);
    }

    public void addOperatorListener(OperatorListener l) {
        operatorDispatcher.addListener(l);
    }

    public void removeOperatorListener(OperatorListener l) {
        operatorDispatcher.removeListener(l);
    }

    //whatever events
    public synchronized void fireCEvent(final ConnectionManagerEvent e) {
        try {
            Util.invokeAndWait(new Runnable() {
                public void run() {
                    synchronized (connectionlisteners) { //gets the lock of...
                        switch (e.getID()) {
                        case ConnectionManagerEvent.SECTIONCONNECT:
                            for (int i = 0; i < connectionlisteners.size(); i++)
                                ((ConnectionManagerListener) (connectionlisteners.elementAt(i)))
                                        .sectionEventConnect(e);
                            break;
                        case ConnectionManagerEvent.SECTIONDISCONNECT:
                            for (int i = 0; i < connectionlisteners.size(); i++)
                                ((ConnectionManagerListener) (connectionlisteners.elementAt(i)))
                                        .sectionEventDisconnect(e);
                            break;
                        default:
                            err();
                        }
                    }
                }
            });
        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

    }

    private void err() {
        System.out.println("Unknown id type in ServerEnevt manager for Connection manager");
    }

    public void fireOEvent(final OperatorEvent event) { //should be private but
        operatorDispatcher.fireEvent(event);
    }

}