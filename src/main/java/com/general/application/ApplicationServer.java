package com.general.application;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.general.util.Util;
import com.myster.client.stream.MysterDataInputStream;
import com.myster.client.stream.MysterDataOutputStream;

class ApplicationServer {
    private final int password;
    private final ServerSocket serverSocket;
    private final ApplicationSingletonListener listener;
    private final ExecutorService excecutor;

    public ApplicationServer(int password, ServerSocket serverSocket,
            ApplicationSingletonListener listener) {
        this.password = password;
        this.serverSocket = serverSocket;
        this.listener = listener;

        excecutor =  Executors.newVirtualThreadPerTaskExecutor();
    }

    public void start() {
        excecutor.execute(this::run);
    }

    private void run() {
        try {
            for (;;) {
                if (excecutor.isShutdown())
                    return;
                Socket socket = serverSocket.accept();
                if (excecutor.isShutdown())
                    return;
                try (MysterDataInputStream in = new MysterDataInputStream(socket.getInputStream());
                        MysterDataOutputStream out = new MysterDataOutputStream(socket.getOutputStream())) {

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
            if (excecutor.isShutdown())
                return;
            Util.invokeLater(new Runnable() {
                public void run() {
                    listener.errored(ex);
                }
            });
        }
    }

    private String[] getArgs(MysterDataInputStream in) throws IOException {
        String[] args = new String[in.readInt()];
        for (int i = 0; i < args.length; i++) {
            args[i] = in.readUTF();
        }
        return args;
    }

    public void flagToEnd() {
        excecutor.isShutdown();
        try {
            serverSocket.close();
        } catch (Exception ex) {
            // nothing
        }
    }

    public void end() {
        flagToEnd();
        try {
            excecutor.shutdown();
            excecutor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // nothing
        }
    }
}