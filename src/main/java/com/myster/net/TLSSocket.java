package com.myster.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.myster.bandwidth.ThrottledInputStream;
import com.myster.bandwidth.ThrottledOutputStream;
import com.myster.client.stream.MysterDataInputStream;
import com.myster.client.stream.MysterDataOutputStream;
import com.myster.identity.Identity;

/**
 * TLS implementation of MysterSocket that uses explicit TLS negotiation (STARTTLS pattern).
 * The connection starts as plaintext and upgrades to TLS after protocol negotiation.
 */
public class TLSSocket extends MysterSocket {
    private final SSLSocket sslSocket;
    
    // Custom protocol for explicit TLS negotiation using Myster connection sections
    public static final int STLS_CONNECTION_SECTION = 0x53544C53; // "STLS" - Start TLS connection section
    
    private TLSSocket(SSLSocket socket) throws IOException {
        super(buildInput(socket), buildOutput(socket));
        this.sslSocket = socket;
    }
    
    /**
     * Creates a client TLS socket by first establishing a plaintext connection,
     * then negotiating TLS upgrade using Myster protocol pattern.
     */
    public static TLSSocket createClientSocket(MysterAddress address, Identity identity, Optional<PublicKey> expectedServerIdentity) throws IOException {
        Socket plainSocket = null;
        try {
            // First establish plaintext connection
            plainSocket = new Socket(address.getInetAddress(), address.getPort());
            plainSocket.setSoTimeout(2 * 60 * 1000); // 2 minutes timeout
            
            // Create temporary streams for negotiation
            MysterDataOutputStream tempOut = new MysterDataOutputStream(plainSocket.getOutputStream());
            MysterDataInputStream tempIn = new MysterDataInputStream(plainSocket.getInputStream());
            
            // Send STLS connection section request using Myster protocol pattern
            tempOut.writeInt(STLS_CONNECTION_SECTION);
            tempOut.flush();
            System.out.println("Sent STLS connection section request: " + STLS_CONNECTION_SECTION + " (\"STLS\")");
            
            // Read server response byte (Myster protocol: 1 = good, 0 = bad)
            int responseByte = tempIn.read();
            if (responseByte != 1) {
                // Server doesn't support TLS or rejected - fall back to plaintext
                System.out.println("Server rejected STLS negotiation, falling back to plaintext. Response: " + responseByte);
                // Return null to indicate fallback needed
                if (plainSocket != null && !plainSocket.isClosed()) {
                    try { plainSocket.close(); } catch (IOException ignored) {}
                }
                return null; // Caller should handle fallback to regular socket
            }
            
            System.out.println("Server accepted STLS negotiation, upgrading to TLS...");
            
            // Now upgrade to TLS
            SSLSocket sslSocket = upgradeToTLS(plainSocket, identity, expectedServerIdentity, true);
            
            return new TLSSocket(sslSocket);
            
        } catch (Exception e) {
            if (plainSocket != null && !plainSocket.isClosed()) {
                try {
                    plainSocket.close();
                } catch (IOException _) {
                    // ignored
                }
            }
            throw new IOException("Failed to create TLS client socket", e);
        }
    }
    
    /**
     * Creates a server TLS socket from an already accepted socket that has completed TLS negotiation.
     * This is used when the TLS negotiation is handled separately (like in ConnectionRunnable).
     */
    public static TLSSocket upgradeServerSocket(Socket acceptedSocket, Identity identity) throws IOException {
        try {
            System.out.println("Upgrading accepted socket to TLS...");
            
            // Upgrade to TLS (negotiation already completed)
            SSLSocket sslSocket = upgradeToTLS(acceptedSocket, identity, Optional.empty(), false);
            
            return new TLSSocket(sslSocket);
            
        } catch (Exception e) {
            if (!acceptedSocket.isClosed()) {
                try {
                    acceptedSocket.close();
                } catch (IOException _) {
                    // ignored
                }
            }
            throw new IOException("Failed to upgrade server socket to TLS", e);
        }
    }
    
    /**
     * Upgrades an existing plaintext socket to TLS.
     */
    private static SSLSocket upgradeToTLS(Socket plainSocket,
                                          Identity identity,
                                          Optional<PublicKey> expectedServerIdentity,
                                          boolean isClient)
            throws Exception {
        // Create SSL context with the identity's key manager
        SSLContext sslContext = SSLContext.getInstance("TLS");
        KeyManager[] keyManagers = new KeyManager[] { new IdentityKeyManager(identity) };
        
        // Use a trust manager that validates expected server identity
        TrustManager[] trustManagers = new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // Accept all client certificates for P2P
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                if (!expectedServerIdentity.isPresent() || chain.length == 0) {
                    return;
                }
                // Get the public key from the server's certificate
                java.security.PublicKey serverPublicKey = chain[0].getPublicKey();

                // Compare with expected server's public key
                if (!serverPublicKey.equals(expectedServerIdentity.get())) {
                    throw new java.security.cert.CertificateException(
                        "Server certificate does not match expected identity");
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } };
        
        sslContext.init(keyManagers, trustManagers, new SecureRandom());
        
        // Create SSL socket layered over the existing socket
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(
            plainSocket, 
            plainSocket.getInetAddress().getHostAddress(), 
            plainSocket.getPort(), 
            true  // autoClose - close underlying socket when SSL socket closes
        );
        
        // Configure SSL socket
        sslSocket.setUseClientMode(isClient);
        if (isClient) {
            sslSocket.setNeedClientAuth(true);
        } else {
            sslSocket.setWantClientAuth(true);
        }
        
        // Perform the handshake
        sslSocket.startHandshake();
        
        return sslSocket;
    }
    
    /**
     * Gets the peer's certificate chain from the TLS session.
     * The first certificate contains the peer's public key.
     */
    public X509Certificate[] getPeerCertificateChain() throws IOException {
        try {
            return (X509Certificate[]) sslSocket.getSession().getPeerCertificates();
        } catch (Exception e) {
            throw new IOException("Failed to get peer certificate chain", e);
        }
    }
    
    /**
     * Gets the peer's public key from their certificate.
     */
    public java.security.PublicKey getPeerPublicKey() throws IOException {
        X509Certificate[] chain = getPeerCertificateChain();
        if (chain.length > 0) {
            return chain[0].getPublicKey();
        }
        throw new IOException("No peer certificate available");
    }

    private static MysterDataInputStream buildInput(SSLSocket socket) throws IOException {
        return new MysterDataInputStream(new ThrottledInputStream(socket.getInputStream()));
    }

    private static MysterDataOutputStream buildOutput(SSLSocket socket) throws IOException {
        return new MysterDataOutputStream(new ThrottledOutputStream(socket.getOutputStream()));
    }

    @Override
    public InetAddress getInetAddress() {
        return sslSocket.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        return sslSocket.getLocalAddress();
    }

    @Override
    public int getPort() {
        return sslSocket.getPort();
    }

    @Override
    public int getLocalPort() {
        return sslSocket.getLocalPort();
    }

    @Override
    public MysterDataInputStream getInputStream() throws IOException {
        return in;
    }

    @Override
    public MysterDataOutputStream getOutputStream() throws IOException {
        return out;
    }

    @Override
    public void setSoLinger(boolean on, int val) throws SocketException {
        sslSocket.setSoLinger(on, val);
    }

    @Override
    public int getSoLinger() throws SocketException {
        return sslSocket.getSoLinger();
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        sslSocket.setSoTimeout(timeout);
    }

    @Override
    public int getSoTimeout() throws SocketException {
        return sslSocket.getSoTimeout();
    }

    @Override
    public void close() throws IOException {
        sslSocket.close();
        in.close();
        out.close();
    }

    @Override
    public String toString() {
        return "TLSSocket[" + sslSocket.toString() + "]";
    }
}