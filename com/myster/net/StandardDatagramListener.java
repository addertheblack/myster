package com.myster.net;

public interface StandardDatagramListener {

	/**
	*	This is called if there was no error
	*/
	public void response(StandardDatagramEvent event);
	
	/**
	*	This is called if there was a timeout
	*/
	public void timeout(StandardDatagramEvent event);
	
	/**
	*	This is calle dif there was a negative responce
	*	(This can be assume to mean the protocol was not understood)
	*/
	public void error(StandardDatagramEvent event);
}

