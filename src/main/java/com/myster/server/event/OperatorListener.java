/**
 * 
 * Details the types of routines done by the connection manager.
 *  
 */

package com.myster.server.event;

public interface OperatorListener  {
    // a "ping" is a connect / disconnect without any connection section.
    public void pingEvent(OperatorEvent e);

    public void disconnectEvent(OperatorEvent e);

    public void connectEvent(OperatorEvent e);
}