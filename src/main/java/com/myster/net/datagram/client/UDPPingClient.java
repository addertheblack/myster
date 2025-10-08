package com.myster.net.datagram.client;

import com.general.thread.PromiseFuture;
import com.myster.net.MysterAddress;
import com.myster.net.datagram.DatagramProtocolManager;

public class UDPPingClient {
    private final DatagramProtocolManager protocolManager;

    public UDPPingClient(DatagramProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
    }

    public PromiseFuture<PingResponse> ping(MysterAddress address) {
        return protocolManager.mutateTransportManager(address.getPort(), (transportManager) -> {
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
                        protocolManager
                                .mutateTransportManager(address.getPort(),
                                                        (t) -> t.removeTransportIfEmpty(transport));
                    }
                }); 
            });
        });
    }
}