package com.myster.server;

import java.net.Socket;
import com.general.util.*;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;

public class ConnectionContext { //struct
	public MysterSocket socket;
	public MysterAddress serverAddress;
	public Object sectionObject;
	public DownloadQueue downloadQueue;
}