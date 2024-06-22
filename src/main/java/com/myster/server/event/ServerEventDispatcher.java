/**
 * 
 * The ServerEventDispatcher is responsible for managing events in the Sever. If a
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

import com.general.events.NewGenericDispatcher;
import com.general.thread.Invoker;

public class ServerEventDispatcher {
    private final NewGenericDispatcher<ConnectionManagerListener> connectionDispatcher;
    private final NewGenericDispatcher<OperatorListener> operatorDispatcher;

    public ServerEventDispatcher() {
        connectionDispatcher =
                new NewGenericDispatcher<ConnectionManagerListener>(ConnectionManagerListener.class,
                                                                    Invoker.SYNCHRONOUS);
        operatorDispatcher = new NewGenericDispatcher<OperatorListener>(OperatorListener.class,
                                                                        Invoker.SYNCHRONOUS);
    }

    public ServerContext getServerContext() {
        return new ServerContextImpl();
    }

    private class ServerContextImpl  implements ServerContext {
        public void addConnectionManagerListener(ConnectionManagerListener l) {
            connectionDispatcher.addListener(l);
        }

        public void removeConnectionManagerListener(ConnectionManagerListener l) {
            connectionDispatcher.removeListener(l);
        }

        public void addOperatorListener(OperatorListener l) {
            operatorDispatcher.addListener(l);
        }

        public void removeOperatorListener(OperatorListener l) {
            operatorDispatcher.removeListener(l);
        }
    }
    
    public NewGenericDispatcher<ConnectionManagerListener> getConnectionDispatcher() {
        return connectionDispatcher;
    }
    
    public NewGenericDispatcher<OperatorListener> getOperationDispatcher() {
        return operatorDispatcher;
    }
}
