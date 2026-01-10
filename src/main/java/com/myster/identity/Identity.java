package com.myster.identity;

import static com.myster.identity.Util.keyToString;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import com.myster.application.MysterGlobals;

public class Identity {
    private static final Logger log = Logger.getLogger(Identity.class.getName());
    private static final String KEYSTORE_PASSWORD = "If I am typing this somethig is terribly wrong.";

    /* Package protected for unit tests */
    static final String MAIN_IDENTITY_ALIAS = "Main Identity";
    
    /* Package protected for unit tests */
    static final String MAIN_IDENTITY_PW = "Main Identity PW";

    private final File keyStorePath;
    private final String keyStoreFilename;

    private KeyStore keyStore;
    
    // cached because it's really slow with the debugger
    private KeyPair cachedMainIdentity;
    
    public static void main(String[] args) throws IOException {
        List<String> types = List.of("MPG3", "MACS", "WINT", "PICT", "MooV", "TEXT", "ROMS");
        
        
        for (String type : types) {
            File keyStorePath = new File(MysterGlobals.getAppDataPath(), "someNewIdentity");
            String keyStoreFilename = "tmp_store.keystore";
            Identity identity = new Identity(keyStoreFilename, 
                                             keyStorePath);
            
            KeyPair mainIdentity = identity.getMainIdentity().get();
            
            File outFile = new File("D:\\Documents and Settings\\andrew\\My Documents\\keys", type + ".txt");
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(outFile))) {
                writer.println(type);
                writer.println("Public Key ----");
                writer.println(keyToString(mainIdentity.getPublic()));
                writer.println("Private Key ----");
                writer.println(keyToString(mainIdentity.getPrivate()));
            }
            
            new File(keyStorePath, keyStoreFilename).delete();
        }
    }
    
    public static Identity newIdentity() {
        return new Identity("main_store.keystore", new File(MysterGlobals.getAppDataPath(), "identity"));
    }

    public Identity(String keyStoreFilename, File keyStorePath) {
        this.keyStorePath = keyStorePath;
        this.keyStoreFilename = keyStoreFilename;
    }
    
    synchronized KeyStore getKeyStore() {
        if (keyStore==null) {
            load();
        }
        
        return keyStore;
    }

    public synchronized Optional<KeyPair> getMainIdentity() {
        // Return cached value if available
        if (cachedMainIdentity != null) {
            return Optional.of(cachedMainIdentity);
        }
        
        KeyStore k = getKeyStore();
        ensureIdentity(k);

        try {
            PrivateKey privateKey =
                    (PrivateKey) k.getKey(MAIN_IDENTITY_ALIAS, MAIN_IDENTITY_PW.toCharArray());
            Certificate[] certificateChain = k.getCertificateChain(MAIN_IDENTITY_ALIAS);
            Certificate certificate = certificateChain[0];
            PublicKey publicKey = certificate.getPublicKey();
            
            // Cache the result
            cachedMainIdentity = new KeyPair(publicKey, privateKey);
            return Optional.of(cachedMainIdentity);
        } catch (UnrecoverableKeyException | KeyStoreException
                | NoSuchAlgorithmException exception) {
            exception.printStackTrace();
            
            return Optional.empty();
        }
    }
    
    /**
     * Gets the certificate chain for the main identity.
     * This is useful for TLS connections where the certificate needs to be sent to the peer.
     */
    public Optional<Certificate[]> getMainIdentityCertificateChain() {
        KeyStore k = getKeyStore();
        
        ensureIdentity(k);

        try {
            Certificate[] certificateChain = k.getCertificateChain(MAIN_IDENTITY_ALIAS);
            return Optional.ofNullable(certificateChain);
        } catch (KeyStoreException exception) {
            exception.printStackTrace();
            return Optional.empty();
        }
    }
    
    private void ensureIdentity(KeyStore k) {
        try {
            PrivateKey privateKey =
                    (PrivateKey) k.getKey(MAIN_IDENTITY_ALIAS, MAIN_IDENTITY_PW.toCharArray());
            
            if (privateKey == null) {
                generateIdentity(k);
            }
        } catch (UnrecoverableKeyException | KeyStoreException
                | NoSuchAlgorithmException exception) {
            exception.printStackTrace();
            
            throw new RuntimeException();
        }
    }
    

    private void generateIdentity(KeyStore k) throws UnrecoverableKeyException {
        try {
            // Step 1: Generate Key Pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048); // You can choose different key sizes
            KeyPair keyPair = keyGen.generateKeyPair();

            PrivateKey privateKey = keyPair.getPrivate();
            PublicKey publicKey = keyPair.getPublic();

            // Step 2: Save the keys (as Base64 encoded strings for this
            // example)
            String encodedPrivateKey = Base64.getEncoder().encodeToString(privateKey.getEncoded());
            String encodedPublicKey = Base64.getEncoder().encodeToString(publicKey.getEncoded());

            log.finest("Private Key: " + encodedPrivateKey);
            log.fine("Public Key: " + encodedPublicKey);

            Certificate certificate = generateSelfSignedCertificate(keyPair);

            // Add the key pair and certificate to the keystore
            Certificate[] certificateChain = new Certificate[] { certificate };
            k.setKeyEntry(MAIN_IDENTITY_ALIAS,
                          keyPair.getPrivate(),
                          MAIN_IDENTITY_PW.toCharArray(),
                          certificateChain);

            
            // You can save these keys to files or a database as needed.
            save();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException exception) {
            exception.printStackTrace();
        } catch (OperatorCreationException exception) {
            exception.printStackTrace();
        } catch (CertificateException exception) {
            exception.printStackTrace();
        }
    }


    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair)
            throws OperatorCreationException, CertificateException {
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        long now = System.currentTimeMillis();
        Date startDate = new Date(now);

        // Customize with your information
        X500Name dnName = new X500Name("CN=" + UUID.randomUUID().toString());
        BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // Using
                                                                          // the
                                                                          // current
                                                                          // timestamp
                                                                          // as
                                                                          // serial
                                                                          // number
        Date endDate = new Date(now + 365 * 24 * 60 * 60 * 1000L); // Valid for
                                                                   // 1 year

        X509v3CertificateBuilder certificateBuilder =
                new JcaX509v3CertificateBuilder(dnName,
                                                certSerialNumber,
                                                startDate,
                                                endDate,
                                                dnName,
                                                publicKey);

        ContentSigner contentSigner =
                new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(privateKey);
        X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(certificateBuilder.build(contentSigner));

        return certificate;
    }

    private void load() {
        if (loadFrom(keystoreName())) {
            log.fine("Keystore loaded from usual place");
            return;
        } else if (loadFrom(keystoreNameNew())) {
            log.info("Keystore loaded from new file");
            return;
        } else if (loadFrom(keystoreNameOld())) {
            log.warning("Keystore loaded from backup");
            return;
        }

        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException exception) {
            exception.printStackTrace();
        }
        log.info("Keystore could not be loaded so creating a new one");
    }

    private String keystoreName() {
        return keyStoreFilename;
    }
    
    String keystoreNameNew() {
        return keyStoreFilename + ".new";
    }
    
    String keystoreNameOld() {
        return keyStoreFilename + ".backup";
    }
    
    private boolean loadFrom(String filename) {
        final File file = new File(keyStorePath, filename);
        
        if (!file.exists()) {
            return false;
        }
        
        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

            if (file.exists()) {
                try (FileInputStream fin = new FileInputStream(file)) {
                    keyStore.load(fin, KEYSTORE_PASSWORD.toCharArray());
                }
            }

            return true;
        } catch (KeyStoreException _) {
            // ignore
        } catch (NoSuchAlgorithmException exception) {
            exception.printStackTrace();
        } catch (CertificateException exception) {
            exception.printStackTrace();
        } catch (FileNotFoundException exception) {
            exception.printStackTrace();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        
        if (file.exists()) {
            file.renameTo(new File(file.getParentFile(), UUID.randomUUID().toString() + ".backup"));
        }
        
        return false;
    }

    private void save() {
        try {
            boolean success =   keyStorePath.mkdirs();
            if (!success && !keyStorePath.exists()) {
                log.severe("Could not make key store path - directories could not be created: "+keyStorePath);
            }
            final File file = new File(keyStorePath, keystoreNameNew());
            
            if (!file.exists()) {
                file.createNewFile();
            }
            
            try (FileOutputStream fout = new FileOutputStream(file)) {
                keyStore.store(fout, KEYSTORE_PASSWORD.toCharArray());
            }
            
            new File(keyStorePath, keystoreNameOld()).delete();
            
            final File existing = new File(keyStorePath, keystoreName());
            if (existing.exists() && !existing.renameTo(new File(keyStorePath, keystoreNameOld())) ) {
                log.severe("Could not delete old keystore because I couldn't rename it out of the way.");
                return;
            }
            
            if (existing.exists()) {
                throw new IllegalStateException("keystore file with keystore name still exists somehow");
            }

            if (!file.renameTo(new File(keyStorePath, keystoreName()))) {
                log.severe("Could not rename new keystore.");
                return;
            }
        } catch (KeyStoreException exception) {
            exception.printStackTrace();
        } catch (NoSuchAlgorithmException exception) {
            exception.printStackTrace();
        } catch (CertificateException exception) {
            exception.printStackTrace();
        } catch (FileNotFoundException exception) {
            exception.printStackTrace();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}