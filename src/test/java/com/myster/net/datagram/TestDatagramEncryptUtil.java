package com.myster.net.datagram;

import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.myster.identity.Identity;
import com.myster.net.datagram.DatagramEncryptUtil.DecryptionException;
import com.myster.net.datagram.DatagramEncryptUtil.EncryptedRequest;
import com.myster.net.datagram.DatagramEncryptUtil.Lookup;
import com.myster.net.datagram.DatagramEncryptUtil.R;

class TestDatagramEncryptUtil {
    
    @TempDir
    static Path tempDir;
    
    private static String clientKeystoreFilename = "testClientIdentity.keystore";
    private static String serverKeystoreFilename = "testServerIdentity.keystore";
    private static File keystorePath;
    private static Identity clientIdentity;
    private static Identity serverIdentity;
    private static KeyPair clientKeyPair;
    private static KeyPair serverKeyPair;
    
    @BeforeAll
    static void setupAll() {
        keystorePath = tempDir.toFile();
        clientIdentity = new Identity(clientKeystoreFilename, keystorePath);
        serverIdentity = new Identity(serverKeystoreFilename, keystorePath);
        
        clientKeyPair = clientIdentity.getMainIdentity().get();
        serverKeyPair = serverIdentity.getMainIdentity().get();
    }
    
    @Test
    void testEncryptPacketWithClientIdentity() {
        byte[] testPayload = "Hello, encrypted world!".getBytes();
        PublicKey serverPublicKey = serverKeyPair.getPublic();
        Optional<Identity> clientIdentityOpt = Optional.of(clientIdentity);
        
        // Test encryption
        EncryptedRequest encryptedRequest = DatagramEncryptUtil.encryptPacket(
            testPayload, 
            serverPublicKey, 
            clientIdentityOpt
        );
        
        Assertions.assertNotNull(encryptedRequest);
        Assertions.assertNotNull(encryptedRequest.encryptedPacket);
        Assertions.assertNotNull(encryptedRequest.symmetricKey);
        Assertions.assertEquals(32, encryptedRequest.symmetricKey.length); // 256-bit key
        
        // Verify the encrypted packet is not the same as the original
        Assertions.assertFalse(java.util.Arrays.equals(testPayload, encryptedRequest.encryptedPacket));
        
        // Verify minimum packet structure (3 sections with length prefixes = 6 bytes minimum)
        Assertions.assertTrue(encryptedRequest.encryptedPacket.length > 6);
    }
    
    @Test
    void testEncryptPacketWithoutClientIdentity() {
        byte[] testPayload = "Hello, unsigned encrypted world!".getBytes();
        PublicKey serverPublicKey = serverKeyPair.getPublic();
        Optional<Identity> noClientIdentity = Optional.empty();
        
        // Test encryption without client identity (unsigned)
        EncryptedRequest encryptedRequest = DatagramEncryptUtil.encryptPacket(
            testPayload, 
            serverPublicKey, 
            noClientIdentity
        );
        
        Assertions.assertNotNull(encryptedRequest);
        Assertions.assertNotNull(encryptedRequest.encryptedPacket);
        Assertions.assertNotNull(encryptedRequest.symmetricKey);
        Assertions.assertEquals(32, encryptedRequest.symmetricKey.length);
        
        // Verify the encrypted packet is not the same as the original
        Assertions.assertFalse(java.util.Arrays.equals(testPayload, encryptedRequest.encryptedPacket));
    }
    
    @Test
    void testRoundTripWithClientIdentity() throws DecryptionException {
        byte[] originalPayload = "Round trip test with signature".getBytes();
        PublicKey serverPublicKey = serverKeyPair.getPublic();
        PrivateKey serverPrivateKey = serverKeyPair.getPrivate();
        Optional<Identity> clientIdentityOpt = Optional.of(clientIdentity);
        
        // Create a mock lookup service
        Lookup mockLookup = createMockLookup(serverPrivateKey);
        
        // Encrypt
        EncryptedRequest encryptedRequest = DatagramEncryptUtil.encryptPacket(
            originalPayload, 
            serverPublicKey, 
            clientIdentityOpt
        );
        
        // Decrypt
        R decryptResult = DatagramEncryptUtil.decryptRequestPacket(
            encryptedRequest.encryptedPacket, 
            mockLookup
        );
        
        // Verify decryption
        Assertions.assertArrayEquals(originalPayload, decryptResult.payload);
        Assertions.assertArrayEquals(encryptedRequest.symmetricKey, decryptResult.syncDecryptKey);
        Assertions.assertTrue(decryptResult.publicKey.isPresent());
        Assertions.assertTrue(decryptResult.keyHash.isPresent()); // CID should be present
        Assertions.assertEquals(16, decryptResult.keyHash.get().length); // CID is 16 bytes
    }
    
    @Test
    void testRoundTripWithoutClientIdentity() throws DecryptionException {
        byte[] originalPayload = "Round trip test unsigned".getBytes();
        PublicKey serverPublicKey = serverKeyPair.getPublic();
        PrivateKey serverPrivateKey = serverKeyPair.getPrivate();
        Optional<Identity> noClientIdentity = Optional.empty();
        
        // Create a mock lookup service
        Lookup mockLookup = createMockLookup(serverPrivateKey);
        
        // Encrypt
        EncryptedRequest encryptedRequest = DatagramEncryptUtil.encryptPacket(
            originalPayload, 
            serverPublicKey, 
            noClientIdentity
        );
        
        // Decrypt
        R decryptResult = DatagramEncryptUtil.decryptRequestPacket(
            encryptedRequest.encryptedPacket, 
            mockLookup
        );
        
        // Verify decryption
        Assertions.assertArrayEquals(originalPayload, decryptResult.payload);
        Assertions.assertArrayEquals(encryptedRequest.symmetricKey, decryptResult.syncDecryptKey);
        Assertions.assertTrue(decryptResult.publicKey.isEmpty()); // No signature
        Assertions.assertTrue(decryptResult.keyHash.isEmpty()); // No CID
    }
    
    @Test
    void testResponseEncryptionDecryption() {
        byte[] responsePayload = "This is a server response".getBytes();
        byte[] symmetricKey = new byte[32];
        // Fill with test data
        for (int i = 0; i < symmetricKey.length; i++) {
            symmetricKey[i] = (byte) i;
        }
        Optional<Identity> serverIdentityOpt = Optional.of(serverIdentity);
        
        // Encrypt response
        byte[] encryptedResponse = DatagramEncryptUtil.encryptResponsePacket(
            responsePayload, 
            symmetricKey, 
            serverIdentityOpt
        );
        
        Assertions.assertNotNull(encryptedResponse);
        Assertions.assertFalse(java.util.Arrays.equals(responsePayload, encryptedResponse));
        
        // Decrypt response
        byte[] decryptedResponse = DatagramEncryptUtil.decryptResponsePacket(
            encryptedResponse, 
            symmetricKey
        );
        
        Assertions.assertArrayEquals(responsePayload, decryptedResponse);
    }
    
    @Test
    void testResponseEncryptionDecryptionUnsigned() {
        byte[] responsePayload = "Unsigned server response".getBytes();
        byte[] symmetricKey = new byte[32];
        // Fill with different test data
        for (int i = 0; i < symmetricKey.length; i++) {
            symmetricKey[i] = (byte) (i * 2);
        }
        Optional<Identity> noServerIdentity = Optional.empty();
        
        // Encrypt response without signature
        byte[] encryptedResponse = DatagramEncryptUtil.encryptResponsePacket(
            responsePayload, 
            symmetricKey, 
            noServerIdentity
        );
        
        Assertions.assertNotNull(encryptedResponse);
        Assertions.assertFalse(java.util.Arrays.equals(responsePayload, encryptedResponse));
        
        // Decrypt response
        byte[] decryptedResponse = DatagramEncryptUtil.decryptResponsePacket(
            encryptedResponse, 
            symmetricKey
        );
        
        Assertions.assertArrayEquals(responsePayload, decryptedResponse);
    }
    
    @Test
    void testSTLSCodeConstant() {
        // Verify the STLS code is correct ("STLS" as 32-bit int)
        int expected = 0x53544C53; // "STLS" in ASCII
        Assertions.assertEquals(expected, DatagramEncryptUtil.STLS_CODE);
        
        // Verify it converts back to the right string
        byte[] bytes = new byte[4];
        bytes[0] = (byte) ((DatagramEncryptUtil.STLS_CODE >> 24) & 0xFF);
        bytes[1] = (byte) ((DatagramEncryptUtil.STLS_CODE >> 16) & 0xFF);
        bytes[2] = (byte) ((DatagramEncryptUtil.STLS_CODE >> 8) & 0xFF);
        bytes[3] = (byte) (DatagramEncryptUtil.STLS_CODE & 0xFF);
        String reconstructed = new String(bytes);
        Assertions.assertEquals("STLS", reconstructed);
    }
    
    @Test
    void testLargePayload() throws DecryptionException {
        // Test with a larger payload
        byte[] largePayload = new byte[8192];
        for (int i = 0; i < largePayload.length; i++) {
            largePayload[i] = (byte) (i % 256);
        }
        
        PublicKey serverPublicKey = serverKeyPair.getPublic();
        PrivateKey serverPrivateKey = serverKeyPair.getPrivate();
        Optional<Identity> clientIdentityOpt = Optional.of(clientIdentity);
        
        Lookup mockLookup = createMockLookup(serverPrivateKey);
        
        // Encrypt
        EncryptedRequest encryptedRequest = DatagramEncryptUtil.encryptPacket(
            largePayload, 
            serverPublicKey, 
            clientIdentityOpt
        );
        
        // Decrypt
        R decryptResult = DatagramEncryptUtil.decryptRequestPacket(
            encryptedRequest.encryptedPacket, 
            mockLookup
        );
        
        // Verify
        Assertions.assertArrayEquals(largePayload, decryptResult.payload);
    }
    
    @Test
    void testEmptyPayload() throws DecryptionException {
        byte[] emptyPayload = new byte[0];
        PublicKey serverPublicKey = serverKeyPair.getPublic();
        PrivateKey serverPrivateKey = serverKeyPair.getPrivate();
        Optional<Identity> clientIdentityOpt = Optional.of(clientIdentity);
        
        Lookup mockLookup = createMockLookup(serverPrivateKey);
        
        // Encrypt
        EncryptedRequest encryptedRequest = DatagramEncryptUtil.encryptPacket(
            emptyPayload, 
            serverPublicKey, 
            clientIdentityOpt
        );
        
        // Decrypt
        R decryptResult = DatagramEncryptUtil.decryptRequestPacket(
            encryptedRequest.encryptedPacket, 
            mockLookup
        );
        
        // Verify
        Assertions.assertArrayEquals(emptyPayload, decryptResult.payload);
        Assertions.assertEquals(0, decryptResult.payload.length);
    }
    
    @Test
    void testDecryptionWithWrongKey() {
        byte[] testPayload = "Secret message".getBytes();
        PublicKey serverPublicKey = serverKeyPair.getPublic();
        
        // Create another server identity with different keys
        Identity otherServerIdentity = new Identity("otherServer.keystore", keystorePath);
        PrivateKey wrongPrivateKey = otherServerIdentity.getMainIdentity().get().getPrivate();
        
        Optional<Identity> clientIdentityOpt = Optional.of(clientIdentity);
        
        // Encrypt with correct key
        EncryptedRequest encryptedRequest = DatagramEncryptUtil.encryptPacket(
            testPayload, 
            serverPublicKey, 
            clientIdentityOpt
        );
        
        // Try to decrypt with wrong key
        Lookup mockLookupWithWrongKey = createMockLookup(wrongPrivateKey);
        
        // Should throw DecryptionException
        Assertions.assertThrows(DecryptionException.class, () -> {
            DatagramEncryptUtil.decryptRequestPacket(
                encryptedRequest.encryptedPacket, 
                mockLookupWithWrongKey
            );
        });
    }
    
    @Test
    void testDecryptionWithCorruptedData() {
        byte[] testPayload = "Test message".getBytes();
        PublicKey serverPublicKey = serverKeyPair.getPublic();
        PrivateKey serverPrivateKey = serverKeyPair.getPrivate();
        Optional<Identity> clientIdentityOpt = Optional.of(clientIdentity);
        
        Lookup mockLookup = createMockLookup(serverPrivateKey);
        
        // Encrypt
        EncryptedRequest encryptedRequest = DatagramEncryptUtil.encryptPacket(
            testPayload, 
            serverPublicKey, 
            clientIdentityOpt
        );
        
        // Corrupt the encrypted data
        byte[] corruptedData = encryptedRequest.encryptedPacket.clone();
        if (corruptedData.length > 10) {
            corruptedData[10] = (byte) (corruptedData[10] ^ 0xFF); // Flip bits
        }
        
        // Should throw DecryptionException
        Assertions.assertThrows(DecryptionException.class, () -> {
            DatagramEncryptUtil.decryptRequestPacket(corruptedData, mockLookup);
        });
    }
    
    /**
     * Create a mock lookup service for testing
     */
    private Lookup createMockLookup(PrivateKey serverPrivateKey) {
        return new Lookup() {
            @Override
            public Optional<PublicKey> findPublicKey(byte[] keyHash) {
                // For testing, return the client's public key if we have a hash
                if (keyHash != null && keyHash.length == 16) {
                    return Optional.of(clientKeyPair.getPublic());
                }
                return Optional.empty();
            }
            
            @Override
            public Optional<PrivateKey> getServerPrivateKey(Object serverId) {
                // For testing, always return our server private key
                return Optional.of(serverPrivateKey);
            }
        };
    }
}