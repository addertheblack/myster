package com.myster.server;

import java.net.Socket;
import com.general.util.*;
import com.myster.net.MysterAddress;

public class ConnectionContext { //struct
	public  Socket socket;
	public  MysterAddress serverAddress;
	public Object sectionObject;
	public DownloadQueue downloadQueue;
}