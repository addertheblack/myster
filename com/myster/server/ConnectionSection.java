
package com.myster.server;

import java.io.IOException;


/**
*	This interface should be used by plugin developers who want to create
*	an add-on to the Myster server 1) from scatch or 2) that violates or abuses
*	the Myster protocol specs.
*
*	(It goes without saying that this is not a good thing)
*
*	Everyone else should inherit from "ServerThread". ServerThread contains
*	usefull utilities classes.
*/
public interface ConnectionSection {
	public void doSection(ConnectionContext context) throws IOException ;
	public int getSectionNumber() ;
	public Object getSectionObject() ;
}