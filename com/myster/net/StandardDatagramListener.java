package com.myster.net;

public interface StandardDatagramListener {
	public void response(StandardDatagramEvent event);
	public void timeout(StandardDatagramEvent event);
	public void error(StandardDatagramEvent event);
}

