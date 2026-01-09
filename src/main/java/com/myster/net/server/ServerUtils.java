package com.myster.net.server;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.ParamBuilder;
import com.myster.tracker.Tracker;

public class ServerUtils {
    private static final Logger log = Logger.getLogger(ServerUtils.class.getName());
    
    /** 
     * Finds all LAN (private) IP addresses of the local machine.
     * 
     * @return A list of InetAddress objects representing the LAN addresses.
     * @throws SocketException If an I/O error occurs.
     */
    public static List<InetAddress> findMyLanAddress() throws SocketException {
        List<InetAddress> allMyIps = new ArrayList<>();
        for (NetworkInterface networkInterface: Collections.list(NetworkInterface.getNetworkInterfaces())) {
            
            // Optionally, filter out interfaces that are down or loopback
            if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                continue;
            }
            
            for (InetAddress address: Collections.list(networkInterface.getInetAddresses())) {
                allMyIps.add(address);
            }
        }
        
        List<InetAddress> networkAddresses = new ArrayList<>();
        log.fine("Looking for LAN address of this machine");
        for (InetAddress ip: allMyIps) {
            if (isLanAddress(ip)) {
                log.fine("    Machine LAN address found ->" + ip.getHostAddress());
                networkAddresses.add(ip);
            } else {
                log.fine("    Machine Not LAN address   ->" + ip.getHostAddress());
            }
        }
        
        if (networkAddresses.size() == 0 ) {
            log.fine("Could not find LAN address");
        }
        
        return networkAddresses;
    }
    
    public static boolean isLanAddress(InetAddress address) {
        byte[] addrBytes = address.getAddress();
        if ((addrBytes[0] & 0xFF) == 10) {
            // 10.0.0.0 - 10.255.255.255
            return true;
        } else if ((addrBytes[0] & 0xFF) == 172 && (addrBytes[1] & 0xF0) == 16) {
            // 172.16.0.0 - 172.31.255.255
            return true;
        } else if ((addrBytes[0] & 0xFF) == 192 && (addrBytes[1] & 0xFF) == 168) {
            // 192.168.0.0 - 192.168.255.255
            return true;
        }
        return false;
    }
    
    public static void massPing(MysterProtocol protocol, Tracker tracker) throws  SocketException {
        List<InetAddress> allMyIps = ServerUtils.findMyLanAddress();
        
        log.info("Pinging all 255 addresses on the 24 bit subnet");
        List<byte[]> addressesToPing = new ArrayList<>();
        for (InetAddress inetAddress : allMyIps) {
            byte[] addrBytes = inetAddress.getAddress();
            
            for(int i = 1; i < 255; i++) {
                addrBytes[3] = (byte) i;
                
                if (i == inetAddress.getAddress()[3]) {
                    continue;
                }
                
                addressesToPing.add(addrBytes.clone());
                log.finest("Going to ping " + inetAddress + " -> "+i);
            }
        }
        
        addressesToPing.forEach(address -> protocol.getDatagram()
                .ping(new ParamBuilder(new MysterAddress(newAddressNoThrows(address), 6669)))
                .addResultListener(result -> {
                    if (result.isTimeout()) {
                        log.finest("LAN ping Timeout: " + result.address());
                    } else {
                        log.info  ("Found a Myster server on the LAN: " + result.address());
                        tracker.addIp(result.address());
                    }
                }));
    }
    
    private static InetAddress newAddressNoThrows(byte[] address) {
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException exception) {
            throw new IllegalStateException(exception);
        }
    }

}