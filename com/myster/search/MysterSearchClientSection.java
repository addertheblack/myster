package com.myster.search;

import java.io.IOException;

import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;


public interface MysterSearchClientSection {
	public void start();
	public void search(MysterSocket socket, MysterAddress address) throws IOException;
	public void flagToEnd(); //flag search to end ansynchronously
	public void end(); //signal the search to end and join();
}