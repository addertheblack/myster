package com.myster.transferqueue;

import java.io.IOException;

import com.myster.net.MysterAddress;

/**
*	Objects repsonsible for handling errors or download request should
*	implement this
*
*	It has been designed for use in the TransferQueue objects.
*/

public interface Downloader {
	
	/**
	*	This method is called when the download is ready to begin.
	* This method should block until the download is completed as
	* The TransferQueue will use this method returning as
	* a signal that the download has completed and the
	* download spot is now available.
	*
	* IUmplementors should feel free about returning exceptions.
	* Exceptions will be passed up to the original caller of the
	* Transfer Queue's download method.
	*/
	public void download() throws IOException ; //is called when ready to d/l
	
	/**
	*	Implementors should make this routine none-blocking.
	*	This method is called by a TransferQueue when a download
	*	is queued, its queue position changes or the getMaxTimeInterval() of
	*	the TransferQueue has elapsed.
	*/		
	public void queued(QueuedStats stats) throws IOException ;	//is called when ready to download
	
	/**
	*	Implementors should return the IP that this downloader is connected to.
	*	This information is used to make sure two downloads form the same source don't happen 
	*	at the same time.
	*/
	public MysterAddress getAddress();
}