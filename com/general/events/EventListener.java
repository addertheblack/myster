package com.general.events;


/**
*	This class is used for general dispatching by a dispatcher. 
*
*	The events can be manually dispatched to different sub functions using a case table
*/

public abstract class EventListener {

	public abstract void fireEvent(GenericEvent e) ;
	public void err(){}
}