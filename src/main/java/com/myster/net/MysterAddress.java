/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2002-2004
 */

package com.myster.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Represents the address of a MysterServer. The server may not be up.
 */
public class MysterAddress {
    private InetAddress fullAddress;

    private int port;

    private static final int DEFAULT_PORT = 6669;

    /**
     * Builds a MysterAddress based on this String. The string should be of format : ip:port. If the
     * host name is invalid or the string is badly formated an UnknownHostException is thrown.
     * <p>
     * WARNING: MAY BLOCK ON IO (DNS) FOR A LOOONNNNNGGG TIME!
     * @return 
     * 
     * @see java.net.UnknownHostException
     */
    public static MysterAddress createMysterAddress(String s) throws UnknownHostException {
        String ip = s;
        int port = DEFAULT_PORT;

        if (s.lastIndexOf(":") != -1) {
            String portstr = s.substring(s.indexOf(":") + 1);
            try {
                port = Integer.parseInt(portstr); //need some checks here.
            } catch (NumberFormatException ex) {
                throw new UnknownHostException("Port value is not a number: " + s);
            }
            ip = s.substring(0, s.indexOf(":"));
        }
        
        return new MysterAddress(InetAddress.getByName(ip), port);
    }
    

    /**
     * Constructs a MysterAddres object by using an InetAddress. Assumes the default port is the
     * Myster's default port (usually 6669).
     */
    public MysterAddress(InetAddress i) {
        this(i, DEFAULT_PORT);
    }

    /**
     * Constructs a MysterAddres object by using an InetAddress.
     */
    public MysterAddress(InetAddress i, int port) { //should throw a runtime
        fullAddress = i;
        this.port = port; //need some checks here

        if (port > 0xFFFF)
            throw new IllegalArgumentException("Port is out of range -> " + port);
    }
    
    /**
     * Returns the IP address associated with this object in textual presentation.
     * 
     * @return the raw IP address in a string format.
     */
    public String getIP() {
        return fullAddress.getHostAddress();
    }

    /**
     * Returns the address associated with this object.
     * 
     * @return the address in InetAddres format
     */
    public InetAddress getInetAddress() {
        return fullAddress;
    }

    /**
     * Returns the port associated with this object.
     * 
     * @return the port.
     */
    public int getPort() {
        return port;
    }

    public String toString() {
        return (port != DEFAULT_PORT ? getIP() + ":" + port : getIP());
    }

    public boolean equals(Object aa) {
        if (!(aa instanceof MysterAddress))
            return false;
        MysterAddress a = (MysterAddress) aa;
        if (fullAddress.equals(a.fullAddress) && (port == a.port))
            return true;
        return false;
    }

    public int hashCode() {
        return fullAddress.hashCode() ^ port;
    }
}