
package com.myster.server.event;

public interface ServerContext {
    public void addConnectionManagerListener(ConnectionManagerListener l);

    public void removeConnectionManagerListener(ConnectionManagerListener l);

    public void addOperatorListener(OperatorListener l);

    public void removeOperatorListener(OperatorListener l);
}
