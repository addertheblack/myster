package com.myster.net;

public interface DataPacket { //ImmutablePacket should have this too? no
	public MysterAddress getAddress();
	public byte[] getData(); //returns ONLY& the data part
	public byte[] getBytes();	//returns data + header
	public byte[] getHeader(); //returns header?
}
