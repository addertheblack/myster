package com.myster.net.mdns;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import com.myster.identity.Identity;
import com.myster.net.server.ServerUtils;

/**
 * Announces this Myster server via mDNS/DNS-SD (Bonjour/Zeroconf).
 * Broadcasts the service as "_myster._tcp.local." with metadata about the server.
 * 
 * This is the SERVER side of mDNS - it announces "I'm here!"
 */
public class MysterMdnsAnnouncer implements AutoCloseable {
    private static final Logger log = Logger.getLogger(MysterMdnsAnnouncer.class.getName());
    private static final String SERVICE_TYPE = "_myster._tcp.local.";
    
    /** Holds a JmDNS instance paired with its own ServiceInfo */
    private record MdnsEntry(JmDNS jmdns, ServiceInfo serviceInfo) {}
    
    private final List<MdnsEntry> mdnsEntries;
    private final String serverName;
    private final int port;
    private final Map<String, String> props;
    
    /**
     * Starts announcing this Myster server on the network.
     * 
     * @param serverName Human-readable name for this server
     * @param port TCP port the server is listening on
     * @param identity Server's cryptographic identity
     * @throws IOException if mDNS initialization fails
     */
    public MysterMdnsAnnouncer(String serverName, int port, Identity identity) throws IOException {
        this.serverName = serverName;
        this.port = port;
        this.props = new HashMap<>();
        
        try {
            // Find LAN addresses only (excludes public IPs, VPNs, etc.)
            List<InetAddress> lanAddresses = ServerUtils.findMyLanAddress();
            
            if (lanAddresses.isEmpty()) {
                throw new IOException("No LAN addresses found for mDNS announcement");
            }
            
            // Build metadata (TXT records) about this server
            props.put("version", "10.0.0");
            
            // Include identity if available
            identity.getMainIdentity().ifPresent(pair -> {
                String publicKeyStr = com.myster.identity.Util.keyToString(pair.getPublic());
                props.put("port", port + ""); // to prevent caching issues when port changes
                // TXT records have a 255 byte limit per value, so we might need to truncate or split
                if (publicKeyStr.length() <= 255) {
                    props.put("identity", publicKeyStr);
                } else {
                    log.warning("Identity public key too long for TXT record, omitting");
                }
            });
            
            // Create JmDNS instance for each LAN interface, each with its own ServiceInfo
			// jmdns insists on one ServiceInfo per jmdns instance
            mdnsEntries = new java.util.ArrayList<>();
            for (InetAddress lanAddress : lanAddresses) {
                try {
                    JmDNS jmdns = JmDNS.create(lanAddress);
                    
                    // Create a unique ServiceInfo for this JmDNS instance
                    ServiceInfo serviceInfo = ServiceInfo.create(
                        SERVICE_TYPE,            // Service type
                        serverName,              // Instance name (will be made unique if needed)
                        port,                    // Port
                        0,                       // Weight (for load balancing, 0 = no preference)
                        0,                       // Priority (0 = no preference)
                        props                    // TXT record metadata
                    );
                    
                    jmdns.registerService(serviceInfo);
                    mdnsEntries.add(new MdnsEntry(jmdns, serviceInfo));
                    log.info("mDNS service announced on " + lanAddress.getHostAddress() + ": " + serverName + " on port " + port);
                } catch (IOException e) {
                    // Skip interfaces that don't support multicast or have binding issues
                    log.warning("Failed to announce mDNS on " + lanAddress.getHostAddress() + ": " + e.getMessage());
                }
            }
            
            if (mdnsEntries.isEmpty()) {
                throw new IOException("Failed to announce mDNS on any network interface");
            }
            
        } catch (SocketException e) {
            log.warning("Failed to find LAN addresses for mDNS announcement: " + e.getMessage());
            throw new IOException("Failed to find LAN addresses", e);
        }
    }
    
    /**
     * Stops announcing this server and releases mDNS resources.
     */
    @Override
    public void close() {
        for (MdnsEntry entry : mdnsEntries) {
            try {
                entry.jmdns().unregisterService(entry.serviceInfo());
                entry.jmdns().close();
            } catch (Exception e) {
                log.warning("Error closing mDNS instance: " + e.getMessage());
            }
        }
        log.info("mDNS service unregistered from all LAN interfaces");
    }
    
    /**
     * Updates the server name in the mDNS announcement.
     * The service will be re-registered with the new name on all LAN interfaces.
     * 
     * @param newServerName the new server name
     */
    public void updateServerName(String newServerName) {
        try {
            // Unregister old services and re-register with new name
            List<MdnsEntry> newEntries = new java.util.ArrayList<>();
            
            for (MdnsEntry entry : mdnsEntries) {
                entry.jmdns().unregisterService(entry.serviceInfo());
                
                // Create new service info with updated name
                ServiceInfo newServiceInfo = ServiceInfo.create(
                    SERVICE_TYPE,
                    newServerName,
                    port,
                    0,
                    0,
                    props
                );
                
                entry.jmdns().registerService(newServiceInfo);
                newEntries.add(new MdnsEntry(entry.jmdns(), newServiceInfo));
            }
            
            // Replace the entries list
            mdnsEntries.clear();
            mdnsEntries.addAll(newEntries);
            
            log.info("mDNS service name updated to: " + newServerName + " on all LAN interfaces");
            
        } catch (IOException e) {
            log.warning("Failed to update mDNS service name: " + e.getMessage());
        }
    }
}
