/**

	Details the types of routines done by the connection manager.

*/

package com.myster.server.event;


public interface ConnectionManagerListener {
	public void sectionEventConnect(ConnectionManagerEvent e);
	public void sectionEventDisconnect(ConnectionManagerEvent e);
}