/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.net;

import java.io.IOException;
import java.net.Socket;

import com.myster.client.stream.TCPSocket;

public class MysterSocketFactory {

    private static Socket makeTCPSocket(MysterAddress ip) throws IOException {
        Socket socket;

        socket = new Socket(ip.getInetAddress(), ip.getPort());

        socket.setSoTimeout(2 * 60 * 1000);// timeout 2 mins

        return socket;
    }

//    private static Socket makeNoProxy(String s) throws IOException {
//        int port = 6669;
//        String ip = s;
//        if (s.indexOf(":") != -1) {
//            String portstr = s.substring(s.indexOf(":") + 1);
//            port = Integer.parseInt(portstr);
//            ip = s.substring(0, s.indexOf(":"));
//        }
//
//        //System.out.println(ip+":"+port);
//        return new Socket(ip, port);
//    }

    public static MysterSocket makeStreamConnection(MysterAddress ip)
            throws IOException {
        return new TCPSocket(makeTCPSocket(ip));
    }

    public static void makeTransactionConnection(MysterAddress ip)
            throws IOException { //TBD to be done
        throw new IOException("");
    }
}