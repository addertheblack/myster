
package com.myster.bandwidth;

import java.io.OutputStream;
import java.io.IOException;

public class ThrottledOutputStream extends OutputStream {
	OutputStream out;
	
	public ThrottledOutputStream(OutputStream out) {
		this.out=out;
	}

	public void write(int b) throws IOException {
		while(BandwidthManager.requestBytesOutgoing(1)!=1) {} //spin!
		
		out.write(b);
	}
	
	
	public void write(byte b[]) throws IOException {
		write(b,0,b.length);
	}
	
	
	public void write(byte b[], int off, int len) throws IOException {
		for (int bytesSent=off, bytesThatCanBeSent; bytesSent<len; bytesSent+=bytesThatCanBeSent) {
			bytesThatCanBeSent=BandwidthManager.requestBytesOutgoing(len-bytesSent);
			out.write(b, off+bytesSent, bytesThatCanBeSent);
		}
	}
	
	public void flush() throws IOException {
		out.flush();
	}
	
	public void close() throws IOException {
		out.close();
	}
}
