/**
	Typical stuff in here.
*/

package com.myster.server.event;


public abstract class ConnectionManagerAdapter implements ConnectionManagerListener {
	public void sectionEventConnect(ConnectionManagerEvent e) {}
	public void sectionEventDisconnect(ConnectionManagerEvent e) {}
}