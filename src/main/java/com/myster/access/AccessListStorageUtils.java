package com.myster.access;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import com.myster.type.MysterType;

/**
 * Handles binary serialization and deserialization of access lists.
 *
 * <p>File format:
 * <pre>
 * [Header]
 *   magic:       0x4D595354 ("MYST")
 *   version:     1
 *   myster_type: [16 bytes] (MysterType shortBytes — MD5 of type's RSA public key)
 *   hash_alg:    0x01 (SHA-256)
 *   sig_alg:     0x01 (Ed25519)
 * [Block 0: Genesis]
 * [Block 1]
 * ...
 * [Block N]
 * </pre>
 */
public class AccessListStorageUtils {
    private static final int MAGIC = 0x4D595354; // "MYST"
    private static final int VERSION = 1;
    private static final int HASH_ALG_SHA256 = 1;
    private static final int SIG_ALG_ED25519 = 1;

    /**
     * Writes an access list header to the output stream.
     *
     * @param out output stream
     * @param mysterTypeBytes the MysterType shortBytes (16 bytes)
     * @throws IOException if an I/O error occurs
     */
    public static void writeHeader(OutputStream out, byte[] mysterTypeBytes) throws IOException {
        if (mysterTypeBytes == null || mysterTypeBytes.length != 16) {
            throw new IllegalArgumentException("mysterTypeBytes must be 16 bytes");
        }

        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(MAGIC);
        dos.writeInt(VERSION);
        dos.write(mysterTypeBytes);
        dos.writeInt(HASH_ALG_SHA256);
        dos.writeInt(SIG_ALG_ED25519);
    }

    /**
     * Reads and validates the access list header.
     *
     * @param in input stream
     * @return the MysterType shortBytes read from the header (16 bytes)
     * @throws IOException if an I/O error occurs or header is invalid
     */
    public static byte[] readHeader(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);

        int magic = dis.readInt();
        if (magic != MAGIC) {
            throw new IOException("Invalid magic number: 0x" + Integer.toHexString(magic));
        }

        int version = dis.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported version: " + version);
        }

        byte[] mysterTypeBytes = new byte[16];
        dis.readFully(mysterTypeBytes);

        int hashAlg = dis.readInt();
        if (hashAlg != HASH_ALG_SHA256) {
            throw new IOException("Unsupported hash algorithm: " + hashAlg);
        }

        int sigAlg = dis.readInt();
        if (sigAlg != SIG_ALG_ED25519) {
            throw new IOException("Unsupported signature algorithm: " + sigAlg);
        }

        return mysterTypeBytes;
    }

    /**
     * Writes a single block to the output stream.
     *
     * @param block the block to write
     * @param out output stream
     * @throws IOException if an I/O error occurs
     */
    public static void writeBlock(AccessBlock block, OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);

        dos.write(block.getPrevHash());
        dos.writeLong(block.getHeight());
        dos.writeLong(block.getTimestamp());

        byte[] pubkeyEncoded = block.getWriterPubkey().getEncoded();
        dos.writeInt(pubkeyEncoded.length);
        dos.write(pubkeyEncoded);

        dos.write(block.getPayloadHash());

        byte[] payload = block.getPayload();
        dos.writeInt(payload.length);
        dos.write(payload);

        byte[] signature = block.getSignature();
        dos.writeInt(signature.length);
        dos.write(signature);
    }

    /**
     * Reads a single block from the input stream.
     *
     * @param in input stream
     * @return the read block
     * @throws IOException if an I/O error occurs
     */
    public static AccessBlock readBlock(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);

        byte[] prevHash = new byte[32];
        dis.readFully(prevHash);

        long height = dis.readLong();
        long timestamp = dis.readLong();

        int pubkeyLength = dis.readInt();
        byte[] pubkeyEncoded = new byte[pubkeyLength];
        dis.readFully(pubkeyEncoded);
        PublicKey writerPubkey = decodePublicKey(pubkeyEncoded);

        byte[] payloadHash = new byte[32];
        dis.readFully(payloadHash);

        int payloadLength = dis.readInt();
        byte[] payload = new byte[payloadLength];
        dis.readFully(payload);

        int signatureLength = dis.readInt();
        byte[] signature = new byte[signatureLength];
        dis.readFully(signature);

        return new AccessBlock(prevHash, height, timestamp, writerPubkey, payload, signature);
    }

    /**
     * Writes a complete access list to the output stream.
     *
     * @param accessList the access list to write
     * @param out output stream
     * @throws IOException if an I/O error occurs
     */
    public static void write(AccessList accessList, OutputStream out) throws IOException {
        writeHeader(out, accessList.getMysterType().toBytes());

        for (AccessBlock block : accessList.getBlocks()) {
            writeBlock(block, out);
        }
    }

    /**
     * Reads a complete access list from the input stream.
     *
     * @param in input stream
     * @return the loaded access list
     * @throws IOException if an I/O error occurs
     */
    public static AccessList read(InputStream in) throws IOException {
        byte[] mysterTypeBytes = readHeader(in);
        MysterType mysterType = new MysterType(mysterTypeBytes);

        List<AccessBlock> blocks = new ArrayList<>();

        try {
            while (true) {
                blocks.add(readBlock(in));
            }
        } catch (java.io.EOFException e) {
            // End of stream — expected termination
        }

        if (blocks.isEmpty()) {
            throw new IOException("No blocks found in access list");
        }

        AccessList accessList = AccessList.fromBlocks(blocks, mysterType);

        if (!java.util.Arrays.equals(mysterTypeBytes, accessList.getMysterType().toBytes())) {
            throw new IOException("MysterType mismatch: header does not match genesis block");
        }

        return accessList;
    }

    /**
     * Serializes an access list to bytes.
     *
     * @param accessList the access list
     * @return the serialized bytes
     * @throws IOException if serialization fails
     */
    public static byte[] toBytes(AccessList accessList) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        write(accessList, baos);
        return baos.toByteArray();
    }

    /**
     * Deserializes an access list from bytes.
     *
     * @param bytes the serialized bytes
     * @return the deserialized access list
     * @throws IOException if deserialization fails
     */
    public static AccessList fromBytes(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        return read(bais);
    }

    private static PublicKey decodePublicKey(byte[] encoded) throws IOException {
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);

        String[] algorithms = {"Ed25519", "EdDSA", "EC", "RSA"};
        for (String alg : algorithms) {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance(alg);
                return keyFactory.generatePublic(keySpec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                // Try next algorithm
            }
        }

        throw new IOException("Could not decode public key with any known algorithm");
    }
}

