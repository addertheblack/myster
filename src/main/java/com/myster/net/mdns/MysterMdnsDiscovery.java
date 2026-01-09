package com.myster.net.mdns;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import com.myster.net.MysterAddress;
import com.myster.net.server.ServerUtils;

/**
 * Discovers Myster servers on the local network via mDNS/DNS-SD.
 * Listens for "_myster._tcp.local." service announcements.
 * 
 * This is the CLIENT side of mDNS - it listens for "who's there?"
 */
public class MysterMdnsDiscovery implements AutoCloseable {
    private static final Logger log = Logger.getLogger(MysterMdnsDiscovery.class.getName());
    private static final String SERVICE_TYPE = "_myster._tcp.local.";
    
    private final List<JmDNS> jmdnsInstances;
    private final ServiceListener serviceListener;
    
    private static Optional<MysterAddress> extractAddress(ServiceEvent event) {
        // Full service info received
        ServiceInfo info = event.getInfo();
        
        if (info == null) {
            log.warning("Service info is null for: " + event.getName());
            return Optional.empty();
        }
        
        try {
            // Get the server's address and port
            InetAddress[] addresses = info.getInetAddresses();
            if (addresses.length == 0) {
                log.warning("No addresses for service: " + info.getName());
                return Optional.empty();
            }
            
            InetAddress address = addresses[0]; // Use first address
            int port = info.getPort();
            String name = info.getName();
            
            // Create MysterAddress
            String addressString = address.getHostAddress() + ":" + port;
            MysterAddress mysterAddress = MysterAddress.createMysterAddress(addressString);
            
            return Optional.of(mysterAddress);
        } catch (UnknownHostException e) {
            log.warning("Failed to create MysterAddress from mDNS service: " + e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Starts discovering Myster servers on the network.
     * Calls the onServerFound callback whenever a server is discovered.
     * 
     * @param onServerFound Callback invoked when a server is found (receives MysterAddress)
     * @throws IOException if mDNS initialization fails
     */
    public MysterMdnsDiscovery(Consumer<MysterAddress> onServerFound, Consumer<MysterAddress> serverGoByeBye) throws IOException {
        try {
            // Find LAN addresses only (excludes public IPs, VPNs, etc.)
            List<InetAddress> lanAddresses = ServerUtils.findMyLanAddress();
            
            if (lanAddresses.isEmpty()) {
                throw new IOException("No LAN addresses found for mDNS discovery");
            }
            
            // Create listener for service events
            serviceListener = new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    // A service was detected, request full info
                    log.fine("mDNS service detected: " + event.getName());
                    // Request info from the JmDNS instance that fired this event
                    event.getDNS().requestServiceInfo(event.getType(), event.getName());
                }
                
                @Override
                public void serviceResolved(ServiceEvent event) {
                    extractAddress(event).ifPresent(address -> {
                        onServerFound.accept(address);
                        log.info("mDNS server detected: " + event.getName());
                    });
                }
                
                @Override
                public void serviceRemoved(ServiceEvent event) {
                    extractAddress(event).ifPresent(address -> {
                        serverGoByeBye.accept(address);
                        log.info("mDNS server removed: " + event.getName());
                    });
                }
            };
            
            // Create JmDNS instance for each LAN interface and start listening
            jmdnsInstances = new java.util.ArrayList<>();
            for (InetAddress lanAddress : lanAddresses) {
                JmDNS jmdns = JmDNS.create(lanAddress);
                jmdns.addServiceListener(SERVICE_TYPE, serviceListener);
                jmdnsInstances.add(jmdns);
                log.info("mDNS discovery started on " + lanAddress.getHostAddress() + ", listening for " + SERVICE_TYPE);
            }
            
        } catch (SocketException e) {
            log.warning("Failed to find LAN addresses for mDNS discovery: " + e.getMessage());
            throw new IOException("Failed to find LAN addresses", e);
        } catch (IOException e) {
            log.warning("Failed to start mDNS discovery: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Stops discovering servers and releases mDNS resources.
     */
    @Override
    public void close() {
        for (JmDNS jmdns : jmdnsInstances) {
            try {
                jmdns.removeServiceListener(SERVICE_TYPE, serviceListener);
                jmdns.close();
            } catch (Exception e) {
                log.warning("Error closing mDNS instance: " + e.getMessage());
            }
        }
        log.info("mDNS discovery stopped on all LAN interfaces");
    }
}