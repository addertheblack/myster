package com.myster.server;

import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.transferqueue.TransferQueue;

public class ConnectionContext { //struct
	public MysterSocket socket;
	public MysterAddress serverAddress;
	public Object sectionObject;
	public TransferQueue transferQueue;
}