/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.search;

import com.myster.net.MysterAddress;

public class MysterFileStub {
	MysterAddress ip;
	String type;
	String name;

	public MysterFileStub(MysterAddress ip, String type, String name) {
		this.ip=ip;
		this.type=type;
		this.name=name;
	}
	
	public String getName() {
		return name;
	}
	
	public String getIP() {
		return ip.toString();
	}
	
	public MysterAddress getMysterAddress() {
		return ip;
	}
	
	
	public String getType() {
		return type;
	}
}

