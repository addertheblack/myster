package com.myster.client.stream;



import java.util.Vector;

import java.util.Hashtable;



import com.myster.util.FileProgressWindow;



public class MSDownloadHandler extends MSDownloadListener {

	private FileProgressWindow 	progress;

	private int 				macBarCounter;

	private Vector				freeBars;

	private Hashtable			segmentListeners;

	

	public MSDownloadHandler(FileProgressWindow progress) {

		this.progress 	= progress;

		

		macBarCounter		= 1; // the first bar is used for overall progress

		freeBars			= new Vector();

		segmentListeners 	= new Hashtable();

	}

	

	public void startDownload(MultiSourceEvent event) {

		progress.setText("MSDownload is starting...");

		progress.startBlock(0, 0, event.getMultiSourceDownload().getLength());

	}

	

	public void progress(MultiSourceEvent event) {

		progress.setValue(event.getMultiSourceDownload().getProgress());

	}

	

	public void startSegmentDownloader(MSSegmentEvent event) {

		SegmentDownloaderHandler handler = new SegmentDownloaderHandler(progress, getAppropriateBarNumber());

	

		segmentListeners.put(event.getSegmentDownloader(), handler);

	

		event.getSegmentDownloader().addListener(handler);

	}

	

	public void endSegmentDownloader(MSSegmentEvent event) {

		SegmentDownloaderHandler handler = (SegmentDownloaderHandler)(segmentListeners.remove(event.getSegmentDownloader()));

		

		if (handler == null) throw new RuntimeException("Could not find a segment downloader to match a segment download that has ended");

	

		returnBarNumber(handler.getBarNumber());

	}

	

	public void endDownload(MultiSourceEvent event) {

		progress.setText("Download Stopped");







	}

	

	public void doneDownload(MultiSourceEvent event) {

		/*

		String path = theFile.getAbsolutePath();

		File someFile = someFile = new File(path.substring(0, path.length()-2)); //-2 is for .i

		

		if (someFile.exists()) {

			AnswerDialog.simpleAlert(progress, "Could not rename file from \""+theFile.getName()+"\" to \""+someFile.getName()+"\" because a file by that name already exists.");

			return;

		}

		

		if (!theFile.renameTo(someFile)) {

			AnswerDialog.simpleAlert(progress, "Could not rename file from \""+theFile.getName()+"\" to \""+someFile.getName()+"\" because an unspecified error occured.");

			return;

		}

		*/

	}

	

	/**

	*	This routine returns the number of the bar that should be used Next.

	*

	*	It also demonstrates the worst about Java syntax and API calls.

	*/

	private int getAppropriateBarNumber() {

		if (freeBars.size() == 0) {

			progress.setProgressBarNumber(macBarCounter+1);

			

			return macBarCounter++;

		}

		

		int minimum = ((Integer)(freeBars.elementAt(0))).intValue(); //no templates

		int min_index = 0;

		for (int i = 0; i< freeBars.size(); i++) {

			int temp_int = ((Integer)(freeBars.elementAt(i))).intValue(); //no autoboxing

			

			if (temp_int<minimum) {

				minimum = temp_int;

				min_index = i;

			}

		}

		

		int temp_int = ((Integer)(freeBars.elementAt(min_index))).intValue();

		

		freeBars.removeElementAt(min_index); //remove element doens't return what it's removing

		

		return temp_int;

	}

	

	/**

	*	returns a "bar" number to the re-distribution heap

	*/

	private void returnBarNumber(int barNumber) {

		freeBars.addElement(new Integer(barNumber));

	}

}



class SegmentDownloaderHandler extends SegmentDownloaderListener {

	final int bar;

	final FileProgressWindow progress;

	

	public SegmentDownloaderHandler(FileProgressWindow progress, int bar) {

		this.bar = bar;

		this.progress = progress;

	}

	

	public void connected(SegmentDownloaderEvent e) {

		progress.setValue(0, bar);

	}

	

	public void queued(SegmentDownloaderEvent e) {

		progress.setText("You are in queue position "+e.getQueuePosition(), bar);

	}

	

	public void startSegment(SegmentDownloaderEvent e) {	

		progress.startBlock(bar, e.getOffset(), e.getOffset()+e.getLength());

		progress.setPreviouslyDownloaded(e.getOffset(), bar);

		progress.setValue(e.getOffset(), bar);

		progress.setText("Downloading from "+e.getMysterFileStub().getMysterAddress(), bar);

	}

	

	public void downloadedBlock(SegmentDownloaderEvent e) {

		progress.setValue(e.getProgress()+e.getOffset(), bar);

	}

	

	public void endSegment(SegmentDownloaderEvent e) {

		progress.setValue(progress.getMax(bar), bar);

	}

	

	public void endConnection(SegmentDownloaderEvent e) {

		progress.setValue(0, bar);

		//if (!stopDownload) progress.setText("Searching for new source...", bar);

	}

	

	public int getBarNumber() {

		return bar;

	}

}