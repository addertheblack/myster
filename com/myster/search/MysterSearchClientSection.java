package com.myster.search;

import java.io.IOException;

import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.type.MysterType;


public interface MysterSearchClientSection {
	public void start();
	public void search(MysterSocket socket, MysterAddress address, MysterType type) throws IOException;
	public void flagToEnd(); //flag search to end ansynchronously
	public void end(); //signal the search to end and join();
}