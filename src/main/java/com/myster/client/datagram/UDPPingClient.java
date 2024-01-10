package com.myster.client.datagram;

import com.general.thread.PromiseFuture;
import com.myster.net.DatagramProtocolManager;
import com.myster.net.MysterAddress;

public class UDPPingClient {
    private final DatagramProtocolManager protocolManager;

    public UDPPingClient(DatagramProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
    }

    public PromiseFuture<PingResponse> ping(MysterAddress address) {
        return protocolManager.accessPort(address.getPort(), (transportManager) -> {
            PongTransport t = (PongTransport) transportManager.getTransport(PongTransport.TRANSPORT_NUMBER);
            
            if (t == null ) {
                t = new PongTransport(transportManager::sendPacket);
                transportManager.addTransport(t);
            }
            
            PongTransport transport = t;
            
            return PromiseFuture.newPromiseFuture( context -> {
                transport.ping(address, new PingEventListener() {
                    @Override
                    public void pingReply(PingEvent e) {
                        context.setResult(new PingResponse(address, e.getPingTime()));
                        protocolManager.accessPort(address.getPort(),
                                                   (t) -> transportManager
                                                           .removeTransportIfEmpty(transport));
                    }
                }); 
            });
        });
    }
}