/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.search;

import com.myster.net.MysterAddress;
import com.myster.type.MysterType;

public class MysterFileStub {
	MysterAddress ip;
	MysterType type;
	String name;

	public MysterFileStub(MysterAddress ip, MysterType type, String name) {
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
	
	public MysterType getType() {
		return type;
	}
	
	public String toString() {
		return ip + " -> " + type + " -> "+name;
	}
}

