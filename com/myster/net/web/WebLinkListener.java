package com.myster.net.web;

import com.general.events.*;

public abstract class WebLinkListener extends EventListener {

	public final void fireEvent(GenericEvent e) {
		WebLinkEvent event = (WebLinkEvent)e;
		
		switch (event.getID()) {
			case WebLinkEvent.LINK :
				openURLLink(event);
				break;
			default:
				err();
		}
	}
	
	public abstract void openURLLink(WebLinkEvent event) ;
}