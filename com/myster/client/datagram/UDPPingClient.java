package com.myster.client.datagram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.myster.net.MysterAddress;

public class UDPPingClient {
    private static Optional<PongTransport> ponger = Optional.empty();
    private static List<PingElement> backlog = new ArrayList<>();

    public synchronized static void setPonger(PongTransport p) {
        if (ponger.isEmpty()) {
            ponger = Optional.of(p);
            
            for (PingElement pingElement : backlog) {
                ponger.get().ping(pingElement.address, pingElement.listener);
            }
        } else {
            throw new IllegalStateException("UDPPingClient initialized twice.");
        }
    }

    public synchronized static void ping(MysterAddress address, PingEventListener listener)
            throws IOException {
        ponger.ifPresentOrElse(ponger -> {
            ponger.ping(address, listener);
        }, () -> {
            backlog.add(new PingElement(address, listener));
        });
    }

    private record PingElement(MysterAddress address, PingEventListener listener) {}

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