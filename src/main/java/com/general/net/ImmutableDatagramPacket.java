package com.general.net;

/**
 * Class used to wrap incomprehensibly stupid behavior of the native java
 * DatagramPacket.
 * 
 * 1) Makes packet imutable 2) No longer have to worry about packet truncation
 * or data.length not matching getLength()
 * 
 * Contains either des address or source address depending on whether it's
 * comming or going.
 */

import java.net.DatagramPacket;
import java.net.InetAddress;

public final class ImmutableDatagramPacket {
    final InetAddress address;

    final int port;

    final byte[] data;

    public ImmutableDatagramPacket(InetAddress i, int port, byte[] d) {
        address = i;
        this.port = port;
        data = d;
    }

    public ImmutableDatagramPacket(DatagramPacket p) {
        address = p.getAddress();
        port = p.getPort();
        byte[] temp_data = p.getData();
        int length = p.getLength();

        byte[] b_temp = new byte[length];

        if (length < temp_data.length) {
            b_temp = new byte[length];

            for (int i = 0; i < length; i++) {
                b_temp[i] = temp_data[i];
            }
        } else if (length > temp_data.length) { //should not happen.
            throw new RuntimeException(
                    "Length is bigger then  message, packet is garbage.");
        }

        data = b_temp;
    }

    /**
     * Returns a copy of the data contained by this Packet.
     */
    public byte[] getData() {
        byte[] b_temp = new byte[data.length];

        System.arraycopy(data, 0, b_temp, 0, data.length); //I hate this
                                                           // nonesence routine.

        return b_temp;
    }

    public byte getDataAt(int index) {
        return data[index];
    }

    public byte[] getDataRange(int start, int end) {
        byte[] b_temp = new byte[end - start];

        System.arraycopy(data, start, b_temp, 0, end - start);

        return b_temp;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public int getSize() {
        return data.length;
    }

    /**
     * Returns a well formed DatagramPacket representation of the
     * ImutableDatagramPacket (Is a COPY)
     */
    public DatagramPacket getDatagramPacket() {
        return new DatagramPacket(getData(), data.length, address, port);
    }
}