package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Optional;
import java.util.logging.Logger;

import com.myster.application.MysterGlobals;
import com.myster.type.MysterType;

/**
 * Utilities for persisting the Ed25519 admin keypair that authorises appending
 * blocks to a type's access list.
 *
 * <p>Keys are stored at:
 * <pre>  {PrivateDataPath}/AccessListKeys/{mysterType_hex}.key</pre>
 *
 * <p>The filename is derived deterministically from the {@link MysterType} hex string, so
 * given any {@code MysterType} the system can check admin status with a simple file-existence
 * test — no separate index or flag needed.
 *
 * <p>File format (binary, written with {@link DataOutputStream}):
 * <ol>
 *   <li>[4 bytes] version = 1</li>
 *   <li>[4 bytes] private key length N</li>
 *   <li>[N bytes] PKCS8-encoded private key</li>
 *   <li>[4 bytes] public key length M</li>
 *   <li>[M bytes] X.509-encoded public key</li>
 * </ol>
 */
public class AccessListKeyUtils {
    private static final Logger log = Logger.getLogger(AccessListKeyUtils.class.getName());
    private static final int KEY_VERSION = 1;
    private static final String KEY_DIR_NAME = "AccessListKeys";

    private AccessListKeyUtils() {}

    /**
     * Saves an Ed25519 admin keypair for the given type. Overwrites any existing file.
     *
     * @param keyPair the Ed25519 keypair to save
     * @param mysterType the type this keypair administers
     * @throws IOException if writing fails
     */
    public static void saveKeyPair(KeyPair keyPair, MysterType mysterType) throws IOException {
        File dir = getAccessListKeysDir();
        File keyFile = new File(dir, mysterType.toHexString() + ".key");
        File tempFile = new File(dir, mysterType.toHexString() + ".key.tmp");

        byte[] privateBytes = keyPair.getPrivate().getEncoded();
        byte[] publicBytes = keyPair.getPublic().getEncoded();

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(tempFile))) {
            out.writeInt(KEY_VERSION);
            out.writeInt(privateBytes.length);
            out.write(privateBytes);
            out.writeInt(publicBytes.length);
            out.write(publicBytes);
        }

        if (!tempFile.renameTo(keyFile)) {
            tempFile.delete();
            throw new IOException("Failed to save admin key file for type: " + mysterType.toHexString());
        }

        log.info("Saved admin key for type: " + mysterType.toHexString());
    }

    /**
     * Loads the admin keypair for the given type.
     * Returns {@link Optional#empty()} if no key file exists for this type.
     *
     * @param mysterType the type to load the admin key for
     * @return the keypair, or empty if this machine did not create this type
     * @throws IOException if the file exists but cannot be read or is corrupt
     */
    public static Optional<KeyPair> loadKeyPair(MysterType mysterType) throws IOException {
        File keyFile = new File(getAccessListKeysDir(), mysterType.toHexString() + ".key");

        if (!keyFile.exists()) {
            return Optional.empty();
        }

        try (DataInputStream in = new DataInputStream(new FileInputStream(keyFile))) {
            int version = in.readInt();
            if (version != KEY_VERSION) {
                throw new IOException("Unknown key file version " + version + " for type: " + mysterType.toHexString());
            }

            int privateKeyLen = in.readInt();
            byte[] privateKeyBytes = new byte[privateKeyLen];
            in.readFully(privateKeyBytes);

            int publicKeyLen = in.readInt();
            byte[] publicKeyBytes = new byte[publicKeyLen];
            in.readFully(publicKeyBytes);

            PrivateKey privateKey = decodePrivateKey(privateKeyBytes);
            PublicKey publicKey = decodePublicKey(publicKeyBytes);

            return Optional.of(new KeyPair(publicKey, privateKey));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to load admin key for type " + mysterType.toHexString(), e);
        }
    }

    /**
     * Returns {@code true} if an admin key file exists for the given type on this machine,
     * meaning this machine created the type and is its administrator.
     *
     * Note that this function does file IO. If speed is essential please cache the result.
     *
     * @param mysterType the type to check
     * @return true if this machine is the admin for this type
     */
    public static boolean hasKeyPair(MysterType mysterType) {
        return new File(getAccessListKeysDir(), mysterType.toHexString() + ".key").exists();
    }

    /**
     * Deletes the admin key file for the given type. Used when the type itself is deleted.
     *
     * @param mysterType the type whose admin key should be removed
     */
    public static void deleteKeyPair(MysterType mysterType) {
        File keyFile = new File(getAccessListKeysDir(), mysterType.toHexString() + ".key");
        if (keyFile.exists() && keyFile.delete()) {
            log.info("Deleted admin key for type: " + mysterType.toHexString());
        }
    }

    private static File getAccessListKeysDir() {
        File dir = new File(MysterGlobals.getPrivateDataPath(), KEY_DIR_NAME);
        if (!dir.mkdirs()) {
            if (!dir.exists()) {
                throw new IllegalStateException("Failed to create AccessListKeys directory at: " + dir.getAbsolutePath());
            }
        }
        return dir;
    }

    private static PrivateKey decodePrivateKey(byte[] bytes)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return KeyFactory.getInstance("EdDSA").generatePrivate(spec);
        }
    }

    private static PublicKey decodePublicKey(byte[] bytes)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return KeyFactory.getInstance("EdDSA").generatePublic(spec);
        }
    }
}

