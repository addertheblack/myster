/**Lets other objects outside get a handle on MysterIP information.
*/

package com.myster.tracker;

import com.myster.net.*;
import com.myster.type.MysterType;

public interface MysterServer {
	public boolean getStatus() ;
	public boolean getStatusPassive() ;
	public MysterAddress getAddress() ;
	public int getNumberOfFiles(MysterType type) ;
	public double getSpeed() ;
	public double getRank(MysterType type) ;
	public String getServerIdentity();
	public int getPingTime();
	public boolean isUntried();
}