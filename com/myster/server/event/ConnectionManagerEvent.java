/**

	Event specifcaly for the connection manager.
*/

package com.myster.server.event;

import com.general.events.EventDispatcher;

public class ConnectionManagerEvent extends ServerEvent {
	public static final int SECTIONCONNECT=0;
	public static final int SECTIONDISCONNECT=1;
	
	Object object;

	public ConnectionManagerEvent(int id, String ip, int section, Object d) {
		super(id,ip,section);
		object=d;
	}
	
	public Object getSectionObject() {
		return object;
	}
}