/**
 * Lets other objects outside get a handle on MysterIP information.
 */

package com.myster.tracker;

import java.util.Optional;

import com.myster.net.MysterAddress;
import com.myster.type.MysterType;

public interface MysterServer {
    public boolean getStatus();

    public boolean getStatusPassive();

    /**
     * @return best address to try and communicate with this server
     */
    public Optional<MysterAddress> getBestAddress();
    
    /**
     * @return [] if no addresses for this server or returns ALL addresses
     */
    public MysterAddress[] getAddresses();
    
    public MysterAddress[] getAvailableAddresses();

    public int getNumberOfFiles(MysterType type);

    public double getSpeed();

    public double getRank(MysterType type);

    public String getServerName();

    public int getPingTime();

    public boolean isUntried();

    public long getUptime();
    
    public MysterIdentity getIdentity();
}