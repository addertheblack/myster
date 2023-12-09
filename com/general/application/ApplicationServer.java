package com.general.application;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import com.general.thread.SafeThread;
import com.general.util.Util;

class ApplicationServer extends SafeThread {
    private final int password;

    private final ServerSocket serverSocket;

    private final ApplicationSingletonListener listener;

    public ApplicationServer(int password, ServerSocket serverSocket,
            ApplicationSingletonListener listener) {
        this.password = password;
        this.serverSocket = serverSocket;
        this.listener = listener;
    }

    public void run() {
        try {
            for (;;) {
                if (endFlag)
                    return;
                Socket socket = serverSocket.accept();
                if (endFlag)
                    return;
                try (DataInputStream in = new DataInputStream(socket.getInputStream());
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                    System.out.println("getting connection form self");
                    int password = in.readInt();
                    final String[] args = getArgs(in);
                    if (password == this.password) {
                        out.write(1);
                        Util.invokeLater(new Runnable() {
                            public void run() {
                                listener.requestLaunch(args);
                            }
                        });
                    } else {
                        out.write(0); //password is wrong.
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                try {
                    socket.close();
                } catch (Exception ex) {
                }
            }
        } catch (final Exception ex) {
            if (endFlag)
                return;
            Util.invokeLater(new Runnable() {
                public void run() {
                    listener.errored(ex);
                }
            });
        }
    }

    private String[] getArgs(DataInputStream in) throws IOException {
        String[] args = new String[in.readInt()];
        for (int i = 0; i < args.length; i++) {
            args[i] = in.readUTF();
        }
        return args;
    }

    public void flagToEnd() {
        endFlag = true;
        try {
            serverSocket.close();
        } catch (Exception ex) {
        }
    }

    public void end() {
        flagToEnd();
        try {
            join();
        } catch (InterruptedException e) {
        }
    }
}