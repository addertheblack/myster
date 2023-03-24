
package com.myster;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.igd.PortMappingListener;
import org.fourthline.cling.support.model.PortMapping;

import com.myster.application.MysterGlobals;

public class UpnpManager {
    public static class HostAddress
    {
        public final InetAddress inetAddress;
        public final int port;
        
        public HostAddress(InetAddress p_inetAddress, int p_port ) {
            inetAddress = p_inetAddress;
            port = p_port;
        }
    }

    private static UpnpService upnpService;

    public static void initMapping(HostAddress[] addresses) {
        List<PortMapping> portMappings = new ArrayList<>();
        for (HostAddress hostAddress : addresses) {
            PortMapping tcpPortMapping = new PortMapping(hostAddress.port,
                                            "" + hostAddress.inetAddress.getHostAddress(),
                                            PortMapping.Protocol.TCP,
                    "Myster TCP");
            tcpPortMapping.setLeaseDurationSeconds(new UnsignedIntegerFourBytes(100));
            portMappings.add(tcpPortMapping);
            PortMapping udpPortMapping = new PortMapping(MysterGlobals.DEFAULT_PORT,
                                             "" + hostAddress.inetAddress.getHostAddress(),
                                             PortMapping.Protocol.UDP,
                    "Myster UDP");
            udpPortMapping.setLeaseDurationSeconds(new UnsignedIntegerFourBytes(100));
            portMappings.add(udpPortMapping);
        }

        UpnpService upnpService = new UpnpServiceImpl(new PortMappingListener(portMappings
                .toArray(new PortMapping[0])));

        upnpService.getControlPoint().search();

        UpnpManager.upnpService = upnpService;
    }

    public static void shutdown() {
        if (upnpService == null) {
            System.out.println("Can't shutdown upnp service. It was not started correctly.");
            return;
        }
        upnpService.shutdown();
    }
}
