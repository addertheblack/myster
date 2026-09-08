package com.general.application;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;

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
 * Startup preferences are flushed after the socket is bound, and readers synchronize with the
 * backing store before discovery. Only the instance owning the listener removes these preferences.
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
     * @return false if the arguments were forwarded to an existing instance, true if this instance
     *         started its own listener.
     * @throws IOException
     *             if the currently running program cannot be contacted and this
     *             ApplicationSingleton cannot create its ServerSocket or publish its preferences.
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

    private void connectToSelf(String[] args) throws IOException, BackingStoreException {
        Preferences prefs = getPreferences();
        prefs.sync();
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
        Preferences parent = Preferences.userNodeForPackage(getClass());
        parent.sync();
        return parent.nodeExists("startup");
    }

    private void newSelf(ApplicationSingletonListener listener) throws IOException {
        // Owning the port must precede any change to the running instance's credentials.
        ServerSocket socket = new ServerSocket(port, 2, InetAddress.getLocalHost());
        boolean started = false;
        try {
            Preferences node = getPreferences();
            int password = (int) (32000 * Math.random());
            node.putInt("password", password);
            node.putInt("port", port);
            node.flush();

            server = new ApplicationServer(password, socket, listener);
            server.start();
            started = true;
        } catch (BackingStoreException exception) {
            throw new IOException("Could not publish application startup preferences", exception);
        } finally {
            if (!started) {
                socket.close();
            }
        }
    }

    /**
     * Stops this instance's listener and flushes removal of its startup preferences. Does nothing
     * if this instance did not start a listener or has already been closed.
     *  
     */
    public void close() {
        if (server == null) {
            return;
        }

        // Keep the port bound until removal is published, so a new owner cannot be erased.
        try {
            Preferences parent = Preferences.userNodeForPackage(getClass());
            if (parent.nodeExists("startup")) {
                parent.node("startup").removeNode();
                parent.flush();
            }
        } catch (BackingStoreException exception) {
            // Cleanup is best effort; a later owner can replace stale credentials.
        } finally {
            server.end();
            server = null;
        }
    }

}
