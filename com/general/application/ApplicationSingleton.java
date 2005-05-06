package com.general.application;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import com.general.util.SafeThread;
import com.general.util.Util;

/**
 * Use this class to assert that there's only one version of the program currently running. Will
 * take into account the case where two different version for the app are running under different
 * users (so long as they are launched with different lock files.).
 * <p>
 * This class works by creating a Server on a certain port which listens for connections. The server
 * binds only to the localhost. A "lockfile" is then created containing a randomly chosen password.
 * When another app is launched it looks for the lock file. If it finds the lock file it reads the
 * "password" from it and tries to contact the specified port. If it can successfully contact the
 * remote port, it sends the password and the program args. When that happens the servers notifies
 * the ApplicationSingletonListener and passes the listener the args.
 * <p>
 * A race condition currently exists where two apps launched soon after each other might not manage
 * to contact each other and throw an Exception. Oh well...
 */
public class ApplicationSingleton {
    private File lockFile;

    private int port;

    private ApplicationServer server;

    private final ApplicationSingletonListener listener;

    private final String[] args;

    /**
     * The lockFile is a directory and file name where this ApplicationSingleton should try and
     * write information that only the current user can write to. Doing this will insure that only
     * one user at a time can launch this app. The port should be a port knwon to both.
     * 
     * @param lockFile
     * @param port
     * @param listener
     * @param args
     */
    public ApplicationSingleton(File lockFile, int port, ApplicationSingletonListener listener,
            String[] args) {
        this.lockFile = lockFile;
        this.port = port;
        this.listener = listener;
        this.args = args;
    }

    /**
     * Call this method to try to connect to self and send the args and return false or, if there is
     * no currently running app then try to make a socket and return true.
     * 
     * @return false if there is already an instance of this Application running, false otherwise.
     * @throws IOException
     *             if the currently running program cannot be contacted and this
     *             ApplicationSingleton cannot create its ServerSocket.
     */
    public boolean start() throws IOException {
        try {
            if (lockFile.exists()) {
                connectToSelf(lockFile, args);
                return false;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        newSelf(lockFile, listener);
        return true;
    }

    private void connectToSelf(File file, String[] args) throws IOException {
        DataInputStream in = null;

        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
            int password = in.readInt();
            int port = in.readInt();

            if (port != this.port)
                throw new IOException("Garbage in lock file.");

            connectToSelf(password, args);
        } finally {
            try {
                in.close();
            } catch (Exception ex) {
            }
        }
    }

    private void connectToSelf(int password, String[] args) throws IOException {
        Socket socket = null;
        DataOutputStream out = null;
        DataInputStream in = null;

        try {
            socket = new Socket(InetAddress.getLocalHost(), port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            out.writeInt(password);
            sendArgs(out, args);
            int result = in.read();
            if (result == 1) {
                return;
            } else {
                throw new ApplicationSingletonException(
                        "Other Myster Program wrote back error of type: " + result, result);
            }

        } finally {
            try {
                in.close();
            } catch (Exception ex) {
            }
            try {
                out.close();
            } catch (Exception ex) {
            }
            try {
                socket.close();
            } catch (Exception ex) {
            }
        }
    }

    private void sendArgs(DataOutputStream out, String[] args) throws IOException {
        out.writeInt(args.length);
        for (int i = 0; i < args.length; i++) {
            out.writeUTF(args[i]);
        }
    }

    //public static int password=-1;
    private void newSelf(File file, ApplicationSingletonListener listener) throws IOException {
        DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
        Math.random();
        Math.random();
        Math.random();
        double temp = Math.random();
        int password = (int) (32000 * temp);

        out.writeInt(password);
        out.writeInt(port);
        out.close();

        server = new ApplicationServer(password, new ServerSocket(port, 1, InetAddress
                .getLocalHost()), listener);
        server.start();
    }

    /**
     * Call this when you are finished with this ApplicationSingleton.. Like if your app is quit.
     *  
     */
    public void close() {
        lockFile.delete();
        server.end();
    }

}

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
            DataInputStream in;
            DataOutputStream out;

            for (;;) {
                if (endFlag)
                    return;
                Socket socket = serverSocket.accept();
                if (endFlag)
                    return;
                try {
                    in = new DataInputStream(socket.getInputStream());
                    out = new DataOutputStream(socket.getOutputStream());

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

/**
 * This type of IOException is thrown when the other instance of the program was contacted but
 * refused the connection invocation request for whatever reason.
 */
class ApplicationSingletonException extends IOException {

    private final int result;

    /**
     * Builds a new ApplicationSingletonException.
     * 
     * @param string
     * @param result
     */
    public ApplicationSingletonException(String string, int result) {
        super(string);
        this.result = result;

    }

    /**
     * Use this to get the error code response that caused this exception.
     * 
     * @return the error code returned by the other invocation.
     */
    public int getResult() {
        return result;
    }
}