package com.general.application;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.myster.client.stream.MysterDataInputStream;
import com.myster.client.stream.MysterDataOutputStream;

/**
 * Use this class to assert that there's only one version of the program currently running. Will
 * take into account the case where two different version for the app are running under different
 * users (so long as they are launched with different lock files.).
 * <p>
 * This class works by creating a Server on a certain port which listens for connections. The server
 * binds only to the localhost. A lock preferences is then created containing a randomly chosen password.
 * When another app is launched it looks for the lock preferences. If it finds the lock prefs it reads the
 * "password" from it and tries to contact the specified port. If it can successfully contact the
 * remote port, it sends the password and the program args. When that happens the servers notifies
 * the ApplicationSingletonListener and passes the listener the args.
 * <p>
 * A race condition currently exists where two apps launched soon after each other might not manage
 * to contact each other and throw an Exception. Oh well...
 */
public class ApplicationContext {
    private int port;
    private ApplicationServer server;
     
    private final ApplicationSingletonListener listener;
    private final String[] args;

    /**
     * The lockFile is a directory and file name where this ApplicationSingleton should try and
     * write information that only the current user can write to. Doing this will insure that only
     * one user at a time can launch this app. The port should be a port knwon to both.
     */
    public ApplicationContext(int port, ApplicationSingletonListener listener,
            String[] args) {
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
            if (prefencesExist()) {
                connectToSelf(args);
                return false;
            }
        } catch (BackingStoreException | IOException ex) {
            ex.printStackTrace();
        }

        newSelf(listener);
        return true;
    }

    private void connectToSelf(String[] args) throws IOException {
        Preferences prefs = getPreferences();
        int password = prefs.getInt("password", 666);
        int port = prefs.getInt("port", this.port);

        if (port != this.port)
            throw new IOException("Garbage in lock file.");

        connectToSelf(password, args);
    }

    private void connectToSelf(int password, String[] args) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLocalHost(), port);
                MysterDataInputStream in = new MysterDataInputStream(socket.getInputStream());
                MysterDataOutputStream out = new MysterDataOutputStream(socket.getOutputStream())) {

            out.writeInt(password);
            sendArgs(out, args);
            int result = in.read();
            if (result == 1) {
                return;
            } else {
                throw new ApplicationSingletonException("Other Myster Program wrote back error of type: "
                        + result, result);
            }
        }
    }

    private static void sendArgs(MysterDataOutputStream out, String[] args) throws IOException {
        out.writeInt(args.length);
        for (int i = 0; i < args.length; i++) {
            out.writeUTF(args[i]);
        }
    }
    
    private Preferences getPreferences() {
        return Preferences.userNodeForPackage(getClass()).node("startup");
    }
    
    private boolean prefencesExist() throws BackingStoreException {
        return Preferences.userNodeForPackage(getClass()).nodeExists("startup");
    }

    private void newSelf(ApplicationSingletonListener listener) throws IOException {
        try {
            getPreferences().removeNode();
        } catch (BackingStoreException exception) {
            // ignore - it's a best effort thing
        }
        
        Preferences node = getPreferences();
        
        double temp = Math.random();
        int password = (int) (32000 * temp);

        node.putInt("password", password);
        node.putInt("port", port);

        server = new ApplicationServer(password,
                                       new ServerSocket(port, 1, InetAddress.getLocalHost()),
                                       listener);
        server.start();
    }

    /**
     * Call this when you are finished with this ApplicationSingleton.. Like if your app is quit.
     *  
     */
    public void close() {
        try {
            getPreferences().removeNode();
        } catch (BackingStoreException exception) {
            // ignore - it's a best effort thing
        }

        if (server != null) {
            server.end();
        }
    }

}