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
    
    private final List<JmDNS> jmdnsInstances;
    private final ServiceInfo serviceInfo;
    
    /**
     * Starts announcing this Myster server on the network.
     * 
     * @param serverName Human-readable name for this server
     * @param port TCP port the server is listening on
     * @param identity Server's cryptographic identity
     * @throws IOException if mDNS initialization fails
     */
    public MysterMdnsAnnouncer(String serverName, int port, Identity identity) throws IOException {
        try {
            // Find LAN addresses only (excludes public IPs, VPNs, etc.)
            List<InetAddress> lanAddresses = ServerUtils.findMyLanAddress();
            
            if (lanAddresses.isEmpty()) {
                throw new IOException("No LAN addresses found for mDNS announcement");
            }
            
            // Build metadata (TXT records) about this server
            Map<String, String> props = new HashMap<>();
            props.put("version", "10.0.0");
            
            // Include identity if available
            identity.getMainIdentity().ifPresent(pair -> {
                String publicKeyStr = com.myster.identity.Util.keyToString(pair.getPublic());
                // TXT records have a 255 byte limit per value, so we might need to truncate or split
                if (publicKeyStr.length() <= 255) {
                    props.put("identity", publicKeyStr);
                } else {
                    log.warning("Identity public key too long for TXT record, omitting");
                }
            });
            
            // Create and register the service
            // Name will be made unique automatically if there's a conflict (e.g., "Server" -> "Server (2)")
            serviceInfo = ServiceInfo.create(
                SERVICE_TYPE,            // Service type
                serverName,              // Instance name (will be made unique if needed)
                port,                    // Port
                0,                       // Weight (for load balancing, 0 = no preference)
                0,                       // Priority (0 = no preference)
                props                    // TXT record metadata
            );
            
            // Create JmDNS instance for each LAN interface and register the service
            jmdnsInstances = new java.util.ArrayList<>();
            for (InetAddress lanAddress : lanAddresses) {
                JmDNS jmdns = JmDNS.create(lanAddress);
                jmdns.registerService(serviceInfo);
                jmdnsInstances.add(jmdns);
                log.info("mDNS service announced on " + lanAddress.getHostAddress() + ": " + serverName + " on port " + port);
            }
            
        } catch (SocketException e) {
            log.warning("Failed to find LAN addresses for mDNS announcement: " + e.getMessage());
            throw new IOException("Failed to find LAN addresses", e);
        } catch (IOException e) {
            log.warning("Failed to start mDNS announcement: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Stops announcing this server and releases mDNS resources.
     */
    @Override
    public void close() {
        for (JmDNS jmdns : jmdnsInstances) {
            try {
                jmdns.unregisterService(serviceInfo);
                jmdns.close();
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
            // Unregister the old service from all instances
            for (JmDNS jmdns : jmdnsInstances) {
                jmdns.unregisterService(serviceInfo);
            }
            
            // Create new service info with the same details but new name
            Map<String, String> props = new HashMap<>();
            
            // Copy existing properties from old service
            var propKeys = serviceInfo.getPropertyNames();
            while (propKeys.hasMoreElements()) {
                String key = propKeys.nextElement();
                String value = serviceInfo.getPropertyString(key);
                if (value != null) {
                    props.put(key, value);
                }
            }
            
            // Create and register new service with updated name
            ServiceInfo newServiceInfo = ServiceInfo.create(
                SERVICE_TYPE,
                newServerName,
                serviceInfo.getPort(),
                0,
                0,
                props
            );
            
            // Re-register on all LAN interfaces
            for (JmDNS jmdns : jmdnsInstances) {
                jmdns.registerService(newServiceInfo);
            }
            
            log.info("mDNS service name updated to: " + newServerName + " on all LAN interfaces");
            
        } catch (IOException e) {
            log.warning("Failed to update mDNS service name: " + e.getMessage());
        }
    }
}
