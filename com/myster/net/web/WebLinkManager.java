package com.myster.net.web;

import java.net.URL;

import com.general.events.*;

public class WebLinkManager {
	private static SyncEventDispatcher dispatcher = new SyncEventDispatcher();

	public static void addWebLinkListener(WebLinkListener l) {
		dispatcher.addListener(l);
	}
	
	public static void removeWebLinkListener(WebLinkListener l) {
		dispatcher.removeListener(l);
	}
	
	public static int getNumberOfListeners() {
		return dispatcher.getNumberOfListeners();
	}
	
	public static void openURL(URL url) {
		dispatcher.fireEvent(new WebLinkEvent(WebLinkEvent.LINK, url));
	}
}



