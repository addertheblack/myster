package com.myster.net.datagram;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.util.Optional;

import javax.crypto.Cipher;

import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.myster.identity.Identity;
import com.myster.mml.MessagePack;

/**
 * Contains an encrypt packet and decrypt packet which work on byte[]
 * Implements the MSD (Myster Secure Datagram) Rev C protocol
 */
public class DatagramEncryptUtil {
    private static final String MSD_REQUEST_CONTEXT = "MSD-REQ";
    private static final String MSD_RESPONSE_CONTEXT = "MSD-RES";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int NONCE_SIZE = 12;
    private static final int KEY_SIZE = 32;
    private static final int CID_SIZE = 16;
    
    // Transaction code for encrypted packets - "STLS" as 32-bit int
    public static final int STLS_CODE = 0x53544C53; // "STLS" - Start TLS connection section
    
    private static final SecureRandom random = new SecureRandom();
    
    static {
        // Register BouncyCastle provider if not already registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
    
    /**
     * @param payload to encrypt
     * @param serverKey to use to encode the sync encryption key
     * @param clientIdentity if present to use to sign. If not present then don't sign the packet.
     * @return encrypted request packet with symmetric key for response decryption
     */
    public static EncryptedRequest encryptPacket(byte[] payload, PublicKey serverKey, Optional<Identity> clientIdentity) {
        try {
            // Generate random key and nonce for Section 3
            byte[] symmetricKey = new byte[KEY_SIZE];
            byte[] nonce = new byte[NONCE_SIZE];
            random.nextBytes(symmetricKey);
            random.nextBytes(nonce);
            
            // Build Section 1 (Encrypted Keying Info)
            byte[] section1Plaintext = buildSection1Plaintext(symmetricKey, nonce);
            byte[] section1Ciphertext = encryptWithPublicKey(section1Plaintext, serverKey);
            
            // Build Section 3 (Encrypted Payload)
            byte[] section3Ciphertext = encryptPayload(payload, symmetricKey, nonce, hashBytes(section1Ciphertext));
            
            // Build Section 2 (Client Signature Block)
            byte[] section2 = buildSection2(section1Ciphertext, section3Ciphertext, clientIdentity);
            
            // Combine all sections with length prefixes
            byte[] encryptedPacket = combineSections(section1Ciphertext, section2, section3Ciphertext);
            
            // Return both the encrypted packet and the symmetric key for response decryption
            return new EncryptedRequest(encryptedPacket, symmetricKey);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt packet", e);
        }
    }
    
    /**
     * Encrypt a response packet using the same symmetric key from the request
     */
    public static byte[] encryptResponsePacket(byte[] responsePayload, byte[] symmetricKey, Optional<Identity> serverIdentity) {
        try {
            // Generate new nonce for response (never reuse nonces)
            byte[] nonce = new byte[NONCE_SIZE];
            random.nextBytes(nonce);
            
            // Build Section 1 (Response Keying Info - plaintext since client already has the key)
            byte[] section1Plaintext = buildResponseSection1Plaintext(nonce);
            
            // Build Section 3 (Encrypted Response Payload)
            byte[] section3Ciphertext = encryptPayload(responsePayload, symmetricKey, nonce, hashBytes(section1Plaintext));
            
            // Build Section 2 (Optional Server Signature Block)
            byte[] section2 = buildResponseSection2(section1Plaintext, section3Ciphertext, serverIdentity);
            
            // Combine all sections with length prefixes
            return combineSections(section1Plaintext, section2, section3Ciphertext);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt response packet", e);
        }
    }
    
    private static byte[] buildSection1Plaintext(byte[] key, byte[] nonce) throws IOException {
        MessagePack section1 = MessagePack.newEmpty();
        section1.put("/alg", "chacha20poly1305");
        section1.putByteArray("/key", key);
        section1.putByteArray("/nonce", nonce);
        return section1.toBytes();
    }
    
    private static byte[] buildResponseSection1Plaintext(byte[] nonce) throws IOException {
        MessagePack section1 = MessagePack.newEmpty();
        section1.put("/alg", "chacha20poly1305");
        section1.putByteArray("/nonce", nonce);
        // Note: No key field for responses since client already has it
        return section1.toBytes();
    }
    
    private static byte[] encryptWithPublicKey(byte[] plaintext, PublicKey publicKey) throws Exception {
        // Use RSA/OAEP instead of ECIES for better compatibility
        String algorithm = publicKey.getAlgorithm();
        Cipher cipher;
        
        if ("RSA".equals(algorithm)) {
            cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        } else if ("EC".equals(algorithm)) {
            // For EC keys, try ECIES with BouncyCastle
            cipher = Cipher.getInstance("ECIES", "BC");
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm: " + algorithm);
        }
        
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(plaintext);
    }
    
    private static byte[] encryptPayload(byte[] payload, byte[] key, byte[] nonce, byte[] aad) throws Exception {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        KeyParameter keyParam = new KeyParameter(key);
        ParametersWithIV params = new ParametersWithIV(keyParam, nonce);
        
        cipher.init(true, params);
        if (aad != null) {
            cipher.processAADBytes(aad, 0, aad.length);
        }
        
        byte[] output = new byte[cipher.getOutputSize(payload.length)];
        int len = cipher.processBytes(payload, 0, payload.length, output, 0);
        cipher.doFinal(output, len);
        
        return output;
    }
    
    private static byte[] buildSection2(byte[] section1Ciphertext, byte[] section3Ciphertext, 
                                       Optional<Identity> clientIdentity) throws Exception {
        MessagePack section2 = MessagePack.newEmpty();
        long timestamp = System.currentTimeMillis();
        
        section2.putLong("/ts", timestamp);
        
        if (clientIdentity.isPresent()) {
            Identity identity = clientIdentity.get();
            PublicKey publicKey = identity.getMainIdentity().get().getPublic();
            PrivateKey privateKey = identity.getMainIdentity().get().getPrivate();
            
            // Create signature with appropriate algorithm
            byte[] signature = createSignature(section1Ciphertext, section3Ciphertext, timestamp, privateKey, MSD_REQUEST_CONTEXT);
            byte[] cid = generateCid(publicKey);
            
            section2.putByteArray("/sig", signature);
            section2.putByteArray("/pub", publicKey.getEncoded());
            section2.putByteArray("/cid", cid);
            section2.put("/sig_alg", getSignatureAlgorithm(privateKey));
        }
        
        return section2.toBytes();
    }
    
    private static byte[] buildResponseSection2(byte[] section1Plaintext, byte[] section3Ciphertext, 
                                               Optional<Identity> serverIdentity) throws Exception {
        MessagePack section2 = MessagePack.newEmpty();
        long timestamp = System.currentTimeMillis();
        
        section2.putLong("/ts", timestamp);
        
        if (serverIdentity.isPresent()) {
            Identity identity = serverIdentity.get();
            PublicKey publicKey = identity.getMainIdentity().get().getPublic();
            PrivateKey privateKey = identity.getMainIdentity().get().getPrivate();
            
            // Create response signature
            byte[] signature = createSignature(section1Plaintext, section3Ciphertext, timestamp, privateKey, MSD_RESPONSE_CONTEXT);
            byte[] cid = generateCid(publicKey);
            
            section2.putByteArray("/sig", signature);
            section2.putByteArray("/pub", publicKey.getEncoded());
            section2.putByteArray("/cid", cid);
            section2.put("/sig_alg", getSignatureAlgorithm(privateKey));
        }
        
        return section2.toBytes();
    }
    
    private static byte[] createSignature(byte[] section1Bytes, byte[] section3Ciphertext, 
                                         long timestamp, PrivateKey privateKey, String context) throws Exception {
        byte[] h1 = hashBytes(section1Bytes);
        byte[] h3 = hashBytes(section3Ciphertext);
        
        // Build signature input: context || h1 || h3 || timestamp
        ByteBuffer toSign = ByteBuffer.allocate(
            context.length() + h1.length + h3.length + 8
        );
        toSign.put(context.getBytes());
        toSign.put(h1);
        toSign.put(h3);
        toSign.putLong(timestamp);
        
        String signatureAlgorithm = getSignatureAlgorithm(privateKey);
        Signature signature = Signature.getInstance(signatureAlgorithm);
        signature.initSign(privateKey);
        signature.update(toSign.array());
        return signature.sign();
    }
    
    /**
     * Determine the appropriate signature algorithm based on the private key type
     */
    private static String getSignatureAlgorithm(PrivateKey privateKey) {
        String algorithm = privateKey.getAlgorithm();
        switch (algorithm) {
            case "RSA":
                return "SHA256withRSA";
            case "EC":
                return "SHA256withECDSA";
            case "Ed25519":
                return "Ed25519";
            case "EdDSA":
                return "EdDSA";
            default:
                // Default to RSA for unknown key types
                return "SHA256withRSA";
        }
    }
    
    private static byte[] generateCid(PublicKey publicKey) throws Exception {
        byte[] hash = hashBytes(publicKey.getEncoded());
        byte[] cid = new byte[CID_SIZE];
        System.arraycopy(hash, 0, cid, 0, CID_SIZE);
        return cid;
    }
    
    private static byte[] hashBytes(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        return digest.digest(data);
    }
    
    private static byte[] combineSections(byte[] section1, byte[] section2, byte[] section3) {
        ByteBuffer buffer = ByteBuffer.allocate(6 + section1.length + section2.length + section3.length);
        
        // Section 1: [len1 | sec1_ciphertext]
        buffer.putShort((short) section1.length);
        buffer.put(section1);
        
        // Section 2: [len2 | sec2_cleartext]
        buffer.putShort((short) section2.length);
        buffer.put(section2);
        
        // Section 3: [len3 | sec3_ciphertext]
        buffer.putShort((short) section3.length);
        buffer.put(section3);
        
        return buffer.array();
    }
    
    /**
     * Decrypt a response packet that was encrypted with the same symmetric key from the original request
     */
    public static byte[] decryptResponsePacket(byte[] responsePacket, byte[] symmetricKey) {
        try {
            // Parse the response packet structure
            ByteBuffer buffer = ByteBuffer.wrap(responsePacket);
            
            // Read Section 1 length and data (plaintext for responses)
            int len1 = buffer.getShort() & 0xFFFF;
            byte[] section1 = new byte[len1];
            buffer.get(section1);
            
            // Read Section 2 length and data (signature - optional)
            int len2 = buffer.getShort() & 0xFFFF;
            byte[] section2 = new byte[len2];
            buffer.get(section2);
            
            // Read Section 3 length and data (encrypted payload)
            int len3 = buffer.getShort() & 0xFFFF;
            byte[] section3 = new byte[len3];
            buffer.get(section3);
            
            // Parse Section 1 to get decryption parameters
            MessagePack section1Data = MessagePack.fromBytes(section1);
            String algorithm = section1Data.get("/alg").orElse("chacha20poly1305");
            byte[] nonce = section1Data.getByteArray("/nonce").orElseThrow(
                () -> new RuntimeException("Missing nonce in response Section 1"));
            
            // Decrypt Section 3 using the symmetric key from the original request
            return decryptPayload(section3, symmetricKey, nonce, hashBytes(section1));
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt response packet", e);
        }
    }
    
    private static byte[] decryptPayload(byte[] ciphertext, byte[] key, byte[] nonce, byte[] aad) throws Exception {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        KeyParameter keyParam = new KeyParameter(key);
        ParametersWithIV params = new ParametersWithIV(keyParam, nonce);
        
        cipher.init(false, params);
        if (aad != null) {
            cipher.processAADBytes(aad, 0, aad.length);
        }
        
        byte[] output = new byte[cipher.getOutputSize(ciphertext.length)];
        int len = cipher.processBytes(ciphertext, 0, ciphertext.length, output, 0);
        cipher.doFinal(output, len);
        
        return output;
    }
    
    /**
     * Result of encrypting a request packet - includes both the encrypted packet and the symmetric key needed to decrypt responses
     */
    public static class EncryptedRequest {
        public final byte[] encryptedPacket;
        public final byte[] symmetricKey;
        
        public EncryptedRequest(byte[] encryptedPacket, byte[] symmetricKey) {
            this.encryptedPacket = encryptedPacket;
            this.symmetricKey = symmetricKey;
        }
    }
    
    /**
     * Result of decrypting the request packet
     */
    public static class R {
        public final Optional<PublicKey> publicKey;
        public final Optional<byte[]> keyHash; // Changed to byte[] for CID
        public final byte[] payload;
        public final byte[] syncDecryptKey;
        
        public R() {
            this(Optional.empty(), Optional.empty(), new byte[] {}, new byte[] {});
        }
        
        private R(Optional<PublicKey> publicKey, Optional<byte[]> keyHash, byte[] payload, byte[] syncDecryptKey) {
            this.publicKey = publicKey;
            this.keyHash = keyHash;
            this.payload = payload;
            this.syncDecryptKey = syncDecryptKey;
        }

        public R withPublicKey(Optional<PublicKey> publicKey) {
            return new R(publicKey, this.keyHash, this.payload, this.syncDecryptKey);
        }

        public R withKeyHash(Optional<byte[]> keyHash) {
            return new R(this.publicKey, keyHash, this.payload, this.syncDecryptKey);
        }

        public R withPayload(byte[] payload) {
            return new R(this.publicKey, this.keyHash, payload, this.syncDecryptKey);
        }
        
        public R withSyncDecryptKey(byte[] syncDecryptKey) {
            return new R(this.publicKey, this.keyHash, this.payload, syncDecryptKey);
        }
    }
    
    public interface Lookup {
        Optional<PublicKey> findPublicKey(byte[]  keyHash); // Changed to byte[] for CID
        Optional<PrivateKey> getServerPrivateKey(Object serverId); // For decryption
    }
    
    // In my world all parsing or format errors are IOExceptions. It's a Myster coding convention.
    public static class DecryptionException extends IOException {
        public DecryptionException(String message) {
            super(message);
        }
        
        public DecryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Gets a request packet and decrypts it
     * @param encryptedRequestPacket the MSD packet to decrypt
     * @param lookup service to find keys and identities
     * @return decrypted result with payload and metadata
     */
    public static R decryptRequestPacket(byte[] encryptedRequestPacket, Lookup lookup) throws DecryptionException {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedRequestPacket);
            
            // Parse Section 1 (Encrypted Keying Info)
            int len1 = buffer.getShort() & 0xFFFF;
            byte[] section1Ciphertext = new byte[len1];
            buffer.get(section1Ciphertext);
            
            // Parse Section 2 (Client Signature Block)
            int len2 = buffer.getShort() & 0xFFFF;
            byte[] section2 = new byte[len2];
            buffer.get(section2);
            
            // Parse Section 3 (Encrypted Payload)
            int len3 = buffer.getShort() & 0xFFFF;
            byte[] section3Ciphertext = new byte[len3];
            buffer.get(section3Ciphertext);
            
            // Parse Section 2 to get signature info
            MessagePack section2Data = MessagePack.fromBytes(section2);
            Optional<Integer> srvKid = section2Data.getInt("/srv_kid");
            
            // Get server private key for decryption
            Optional<PrivateKey> serverPrivateKey = lookup.getServerPrivateKey(srvKid.orElse(null));
            if (serverPrivateKey.isEmpty()) {
                throw new DecryptionException("Server private key not found for srv_kid: " + srvKid);
            }
            
            // Decrypt Section 1 to get symmetric key
            byte[] section1Plaintext = decryptWithPrivateKey(section1Ciphertext, serverPrivateKey.get());
            MessagePack section1Data = MessagePack.fromBytes(section1Plaintext);
            
            byte[] symmetricKey = section1Data.getByteArray("/key").orElseThrow(
                () -> new DecryptionException("Missing symmetric key in Section 1"));
            byte[] nonce = section1Data.getByteArray("/nonce").orElseThrow(
                () -> new DecryptionException("Missing nonce in Section 1"));
            
            // Verify signature if present
            Optional<PublicKey> clientPublicKey = Optional.empty();
            Optional<byte[]> cid = Optional.empty();
            
            if (section2Data.getByteArray("/sig").isPresent()) {
                clientPublicKey = verifySignature(section1Ciphertext, section3Ciphertext, section2Data, lookup, MSD_REQUEST_CONTEXT);
                cid = section2Data.getByteArray("/cid");
            }
            
            // Decrypt Section 3 payload
            byte[] payload = decryptPayload(section3Ciphertext, symmetricKey, nonce, hashBytes(section1Ciphertext));
            
            return new R()
                .withPublicKey(clientPublicKey)
                .withKeyHash(cid)
                .withPayload(payload)
                .withSyncDecryptKey(symmetricKey);
                
        } catch (Exception e) {
            throw new DecryptionException("Failed to decrypt request packet", e);
        }
    }
    
    private static byte[] decryptWithPrivateKey(byte[] ciphertext, PrivateKey privateKey) throws Exception {
        String algorithm = privateKey.getAlgorithm();
        Cipher cipher;
        
        if ("RSA".equals(algorithm)) {
            cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        } else if ("EC".equals(algorithm)) {
            cipher = Cipher.getInstance("ECIES", "BC");
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm: " + algorithm);
        }
        
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(ciphertext);
    }
    
    private static Optional<PublicKey> verifySignature(byte[] section1Bytes, byte[] section3Ciphertext,
                                                      MessagePack section2Data, Lookup lookup, String context) throws Exception {
        
        byte[] signature = section2Data.getByteArray("/sig").orElseThrow(
            () -> new DecryptionException("Missing signature"));
        long timestamp = section2Data.getLong("/ts").orElseThrow(
            () -> new DecryptionException("Missing timestamp"));
        
        PublicKey publicKey = null;
        
        // Try to get public key from the packet or lookup
        Optional<byte[]> pubKeyBytes = section2Data.getByteArray("/pub");
        if (pubKeyBytes.isPresent()) {
            // Convert bytes to PublicKey (implementation depends on key format)
            // For now, we'll use the CID lookup method
            // publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(pubKeyBytes.get()));
        }
        
        Optional<byte[]> cidOpt = section2Data.getByteArray("/cid");
        if (cidOpt.isPresent()) {
            Optional<PublicKey> foundKey = lookup.findPublicKey(cidOpt.get());
            if (foundKey.isPresent()) {
                publicKey = foundKey.get();
            }
        }
        
        if (publicKey == null) {
            throw new DecryptionException("Cannot verify signature: no public key available");
        }
        
        // Verify the signature
        byte[] h1 = hashBytes(section1Bytes);
        byte[] h3 = hashBytes(section3Ciphertext);
        
        ByteBuffer toVerify = ByteBuffer.allocate(
            context.length() + h1.length + h3.length + 8
        );
        toVerify.put(context.getBytes());
        toVerify.put(h1);
        toVerify.put(h3);
        toVerify.putLong(timestamp);
        
        String signatureAlgorithm = section2Data.get("/sig_alg").orElse(getSignatureAlgorithm(publicKey));
        Signature verifier = Signature.getInstance(signatureAlgorithm);
        verifier.initVerify(publicKey);
        verifier.update(toVerify.array());
        
        if (!verifier.verify(signature)) {
            throw new DecryptionException("Signature verification failed");
        }
        
        return Optional.of(publicKey);
    }
    
    /**
     * Get signature algorithm for public key (used for verification)
     */
    private static String getSignatureAlgorithm(PublicKey publicKey) {
        String algorithm = publicKey.getAlgorithm();
        switch (algorithm) {
            case "RSA":
                return "SHA256withRSA";
            case "EC":
                return "SHA256withECDSA";
            case "Ed25519":
                return "Ed25519";
            case "EdDSA":
                return "EdDSA";
            default:
                return "SHA256withRSA";
        }
    }
}