/**
	...
*/


package com.myster.server.event;

public class ServerDownloadEvent extends ServerEvent {
	public final static int STARTED=0;
	public final static int BLOCKSENT=1;
	public final static int FINISHED=2;
	public final static int QUEUED=3;
	
	String type;
	String filename;
	int d;
	long filesofar;
	long filelength;


	//if id is 3 (QUEUED) the 'i' argument is queue position.
	public ServerDownloadEvent(int id, String s, int section,
							 String filename, String type, 
							 int i, long filesofar, long filelength) {
		super(id, s, section);
		this.type=type;
		this.filename=filename;
		this.d=i;
		this.filesofar=filesofar;
		this.filelength=filelength;	 
	}

	public int getBlockType() {
		return (getID()==QUEUED?(int)'q':d);
	}
	
	public long dataSoFar() {
		return filesofar;
	}
	
	public int getQueuePosition() {
		return (getID()==QUEUED?d:0);
	}
	
	public String getFileName() {
		return filename;
	}
	
	public String getFileType() {
		return type;
	}
	
	public long getFileLength() {
		return filelength;
	}
}