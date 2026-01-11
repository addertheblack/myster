package com.myster.net;

import java.net.Socket;
import java.security.KeyPair;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Optional;

import javax.net.ssl.X509KeyManager;

import com.myster.identity.Identity;

/**
 * Custom KeyManager that uses an Identity object to provide the key pair and certificate
 * for TLS connections.
 */
public class IdentityKeyManager implements X509KeyManager {
    private final Identity identity;
    private KeyPair keyPair;
    private X509Certificate[] certificateChain;
    
    public IdentityKeyManager(Identity identity) {
        if (identity==null) {
            throw new NullPointerException();
        }
        
        this.identity = identity;
        loadIdentityData();
    }
    
    private void loadIdentityData() {
        Optional<KeyPair> keyPairOpt = identity.getMainIdentity();
        if (keyPairOpt.isEmpty()) {
            throw new IllegalStateException("No main identity available");
        }
        this.keyPair = keyPairOpt.get();
        
        // Get the certificate chain from the identity
        Optional<Certificate[]> certChainOpt = identity.getMainIdentityCertificateChain();
        if (certChainOpt.isEmpty()) {
            throw new IllegalStateException("No certificate chain available");
        }
        
        Certificate[] certs = certChainOpt.get();
        this.certificateChain = new X509Certificate[certs.length];
        for (int i = 0; i < certs.length; i++) {
            this.certificateChain[i] = (X509Certificate) certs[i];
        }
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        if ("RSA".equals(keyType)) {
            return new String[] { "myster-identity" };
        }
        return null;
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        for (String type : keyType) {
            if ("RSA".equals(type)) {
                return "myster-identity";
            }
        }
        return null;
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        if ("RSA".equals(keyType)) {
            return new String[] { "myster-identity" };
        }
        return null;
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        if ("RSA".equals(keyType)) {
            return "myster-identity";
        }
        return null;
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        if ("myster-identity".equals(alias) && certificateChain != null) {
            return certificateChain;
        }
        return null;
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        if ("myster-identity".equals(alias) && keyPair != null) {
            return keyPair.getPrivate();
        }
        return null;
    }
    
    /**
     * Gets the Identity object used by this KeyManager
     */
    public Identity getIdentity() {
        return identity;
    }
}