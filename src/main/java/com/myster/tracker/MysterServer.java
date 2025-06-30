/**
 * Lets other objects outside get a handle on MysterIP information.
 */

package com.myster.tracker;

import java.util.Optional;

import com.myster.net.MysterAddress;
import com.myster.type.MysterType;

public interface MysterServer {
    public static final int UNTRIED = -1;
    public static final int DOWN = -2;
    
    /**
     * @return true if we think this server is "up"
     */
    public boolean getStatus();

    /**
     * @return best address to try and communicate with this server
     */
    public Optional<MysterAddress> getBestAddress();
    
    /**
     * @return [] if no addresses for this server or returns ALL addresses
     */
    public MysterAddress[] getAddresses();
    
    /**
     * @return the addresses that were replying to ping requests last time we checked
     */
    public MysterAddress[] getUpAddresses();

    /**
     * @param type
     * @return the number of files the server claims to support for this MysterType
     */
    public int getNumberOfFiles(MysterType type);

    /**
     * @return a double representing the speed the server tells us. Might be BS.
     */
    public double getSpeed();

    /**
     * @param type
     * @return a number representing our estimate of how awesome this server is.
     *         High numbers are better.
     */
    public double getRank(MysterType type);

    /**
     * @return Human readable name for the server or null if no name specified
     */
    public String getServerName();

    /**
     * @return ping or {@link MysterServer#UNTRIED} if not checked or {@link MysterServer#DOWN} if down
     */
    public int getPingTime();

    /**
     * @return true if we have not heard a response back (ie: from a ping request) from the server yet.
     */
    public boolean isUntried();

    /**
     * @return the uptime that the server tells us. Might be BS.
     */
    public long getUptime();
    
    public MysterIdentity getIdentity();

    /**
     * @return A string of gibberish (ie: a hash) created from the MysterIdentity for this
     *         server. This is useful because the MysterIdentity string can be
     *         really long but this string is much shorter. The hash is based on md5.
     */
    public ExternalName getExternalName();

    /**
     * We have received a ping request from this server and it is most likely up.
     * We need to check to make sure that the server is marked as up and if not send a ping request
     */
    public void tryPingAgain(MysterAddress address);
}