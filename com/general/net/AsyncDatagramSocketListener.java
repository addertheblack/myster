package com.general.net;

public interface AsyncDatagramSocketListener {
	public void packetReceived(ImmutableDatagramPacket p);
}