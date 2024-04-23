
package com.myster.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.myster.client.net.MysterProtocol;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterServerManager;

public class ServerUtils {
    private static final Logger LOGGER = Logger.getLogger(ServerUtils.class.getName());
    
    public static List<InetAddress> findPublicLandAddress() throws UnknownHostException {
        List<InetAddress> networkAddresses = new ArrayList<>();
        
        var localhostAddress = InetAddress.getLocalHost();
        InetAddress[] allMyIps = InetAddress.getAllByName(localhostAddress.getCanonicalHostName());
        if (allMyIps == null) {
            return networkAddresses;
        }

        LOGGER.fine("Looking for LAN address of this machine");
        for (int i = 0; i < allMyIps.length; i++) {
            if (isLanAddress(allMyIps[i])) {
                LOGGER.fine("    Machine LAN address found ->" + allMyIps[i].getHostAddress());
                networkAddresses.add(allMyIps[i]);
            } else {
                LOGGER.fine("    Machine Not LAN address   ->" + allMyIps[i].getHostAddress());
            }
        }
        
        if (networkAddresses.size() == 0 ) {
            LOGGER.fine("Could not find LAN address.. Addding " + localhostAddress);
            networkAddresses.add(localhostAddress);
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
    
    public static void massPing(MysterProtocol protocol, MysterServerManager ipListManager) throws UnknownHostException {
        List<InetAddress> allMyIps = ServerUtils.findPublicLandAddress();
        
        LOGGER.info("Pinging all 255 addresses on the 24 bit subnet");
        List<byte[]> addressesToPing = new ArrayList<>();
        for (InetAddress inetAddress : allMyIps) {
            byte[] addrBytes = inetAddress.getAddress();
            
            for(int i = 1; i < 255; i++) {
                addrBytes[3] = (byte) i;
                
                if (i == inetAddress.getAddress()[3]) {
                    continue;
                }
                
                addressesToPing.add(addrBytes.clone());
                LOGGER.finest("Going to ping " + inetAddress + " -> "+i);
            }
        }
        
        addressesToPing.forEach(address -> protocol.getDatagram()
                .ping(new MysterAddress(newAddressNoThrows(address), 6669))
                .addResultListener(result -> {
                    if (result.isTimeout()) {
                        LOGGER.finest("LAN ping Timeout: " + result.address());
                    } else {
                        LOGGER.info  ("Found a Myster server on the LAN: " + result.address());
                        ipListManager.addIp(result.address());
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
