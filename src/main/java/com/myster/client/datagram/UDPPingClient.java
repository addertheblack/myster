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

    /*
     * public static boolean ping(String s) { DatagramSocket dsocket=null; int
     * port=6669; String ip=s; if (s.indexOf(":")!=-1) { String
     * portstr=s.substring(s.indexOf(":")+1); port=Integer.parseInt(portstr);
     * ip=s.substring(0, s.indexOf(":")); }
     * 
     * 
     * 
     * try { dsocket=new DatagramSocket(); //random port
     * 
     * DatagramPacket workingPacket; DatagramPacket outgoingPacket; InetAddress
     * address=InetAddress.getByName(ip); for (;;) { byte[]
     * outdata={(byte)'P',(byte)'I',(byte)'N', (byte)'G'}; outgoingPacket=new
     * DatagramPacket(outdata, outdata.length, address, port);
     * //System.out.println("Sending to "+ip+":"+port);
     * dsocket.send(outgoingPacket);
     * 
     * workingPacket=new DatagramPacket(new byte[4], 4);
     * dsocket.setSoTimeout(10000); for (int i=0; true; i++) { try {
     * dsocket.receive(workingPacket); byte[] data=workingPacket.getData(); if
     * (data[0]=='P'&&data[1]=='O'&&data[2]=='N'&&data[3]=='G') {
     * //System.out.println("SUCESS!"); return true; } continue; } catch
     * (InterruptedIOException ex) { dsocket.send(outgoingPacket); if (i <2)
     * continue; else return false; } catch (IOException ex) { return false; } } } }
     * catch (IOException ex) { //System.out.println("UDP CLIENT sub system
     * died"); //ex.printStackTrace(); return false; } finally { try {
     * dsocket.close(); } catch (Exception ex) {} } }
     */

}