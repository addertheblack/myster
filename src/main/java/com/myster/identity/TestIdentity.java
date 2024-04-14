package com.myster.identity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestIdentity {
    // JUnit 5 will automatically create and clean up this temporary directory
    @TempDir
    Path tempDir; 

    private String keystoreFilename = "testIdentity.keystore";
    private File keystorePath;
    private Identity identity;

    @BeforeEach
    void setUp() {
        keystorePath = tempDir.toFile(); // Convert the Path to File, as your Identity class uses File
        identity = new Identity(keystoreFilename, keystorePath);
    }

    @AfterEach
    void tearDown() {
        // JUnit 5 handles the cleanup of @TempDir, so no manual cleanup is required here
    }


    @Test
    void testIdentityCreationAndPersistence() throws Exception {
        // Ensure no identity exists at the beginning
        KeyStore keyStore = identity.getKeyStore();
        assertNull(keyStore.getKey(Identity.MAIN_IDENTITY_ALIAS, Identity.MAIN_IDENTITY_PW.toCharArray()));

        // Create identity and verify it's correctly generated
        KeyPair generatedKeyPair = identity.getMainIdentity().get();
        assertNotNull(generatedKeyPair.getPublic(), "Public key should not be null after identity creation");
        assertNotNull(generatedKeyPair.getPrivate(), "Private key should not be null after identity creation");

        // Reload identity to verify persistence
        Identity reloadedIdentity = new Identity(keystoreFilename, keystorePath);
        KeyPair reloadedKeyPair = reloadedIdentity.getMainIdentity().get();
        assertNotNull(reloadedKeyPair.getPublic(), "Public key should not be null after reloading identity");
        assertNotNull(reloadedKeyPair.getPrivate(), "Private key should not be null after reloading identity");

        
        assertArrayEquals(generatedKeyPair.getPublic().getEncoded(), reloadedKeyPair.getPublic().getEncoded(), "Public keys should match");
        assertArrayEquals(generatedKeyPair.getPrivate().getEncoded(), reloadedKeyPair.getPrivate().getEncoded(), "Private keys should match");
    }

    @Test
    void testKeystoreFileOperations() {
        File keystoreFile = new File(keystorePath, keystoreFilename);
        File newKeystoreFile = new File(keystorePath, identity.keystoreNameNew());
        File backupKeystoreFile = new File(keystorePath, identity.keystoreNameOld());

        // Initially, no files should exist
        assertFalse(keystoreFile.exists(), "Keystore file should not exist initially");
        assertFalse(newKeystoreFile.exists(), "New keystore file should not exist initially");
        assertFalse(backupKeystoreFile.exists(), "Backup keystore file should not exist initially");

        // Trigger identity generation, which should also trigger keystore file creation
        identity.getMainIdentity();

        // Verify that the keystore file now exists
        assertTrue(keystoreFile.exists(), "Keystore file should exist after identity generation");
        assertFalse(newKeystoreFile.exists(), "New keystore file should not exist initially");
        assertFalse(backupKeystoreFile.exists(), "Backup keystore file should not exist initially");
    }
}
