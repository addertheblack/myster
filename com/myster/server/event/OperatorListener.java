/**

	Details the types of routines done by the connection manager.

*/

package com.myster.server.event;


public interface OperatorListener {
	public void ping(OperatorEvent e);
	public void disconnectEvent(OperatorEvent e);
}