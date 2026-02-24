package com.myster.access;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a single block in an access list blockchain.
 *
 * <p>Each block contains:
 * <ul>
 *   <li>Previous block hash (32 bytes)</li>
 *   <li>Height (sequential block number starting at 0)</li>
 *   <li>Timestamp (Unix milliseconds)</li>
 *   <li>Writer public key (Ed25519)</li>
 *   <li>Payload (serialized operation)</li>
 *   <li>Payload hash (SHA-256)</li>
 *   <li>Signature (Ed25519 over canonical bytes)</li>
 * </ul>
 *
 * <p>The genesis block (height 0) has a previous hash of all zeros.
 */
public class AccessBlock {
    private final byte[] prevHash;
    private final long height;
    private final long timestamp;
    private final PublicKey writerPubkey;
    private final byte[] payloadHash;
    private final byte[] payload;
    private final byte[] signature;

    /**
     * Creates a new access block.
     *
     * @param prevHash hash of the previous block (32 bytes, all zeros for genesis)
     * @param height block height (0 for genesis, increments by 1)
     * @param timestamp Unix milliseconds
     * @param writerPubkey Ed25519 public key of the signer
     * @param payload serialized operation
     * @param signature Ed25519 signature over canonical bytes
     */
    public AccessBlock(byte[] prevHash, long height, long timestamp, PublicKey writerPubkey,
                       byte[] payload, byte[] signature) {
        Objects.requireNonNull(prevHash, "prevHash cannot be null");
        if (prevHash.length != 32) {
            throw new IllegalArgumentException("prevHash must be 32 bytes");
        }

        this.prevHash = prevHash.clone();
        this.height = height;
        this.timestamp = timestamp;
        this.writerPubkey = Objects.requireNonNull(writerPubkey, "writerPubkey cannot be null");
        this.payload = Objects.requireNonNull(payload, "payload cannot be null").clone();
        this.payloadHash = computeSHA256(payload);
        this.signature = Objects.requireNonNull(signature, "signature cannot be null").clone();
    }

    public byte[] getPrevHash() {
        return prevHash.clone();
    }

    public long getHeight() {
        return height;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public PublicKey getWriterPubkey() {
        return writerPubkey;
    }

    public byte[] getPayloadHash() {
        return payloadHash.clone();
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    public byte[] getSignature() {
        return signature.clone();
    }

    /**
     * Computes the canonical bytes that are signed.
     * Format: mysterTypeBytes || prevHash || height || timestamp || writerPubkey || payloadHash
     *
     * @param mysterTypeBytes the MysterType shortBytes for this access list (16 bytes, MD5 of type's RSA public key)
     * @return canonical bytes for signature verification
     */
    public byte[] toCanonicalBytes(byte[] mysterTypeBytes) {
        if (mysterTypeBytes == null || mysterTypeBytes.length != 16) {
            throw new IllegalArgumentException("mysterTypeBytes must be 16 bytes");
        }

        byte[] pubkeyEncoded = writerPubkey.getEncoded();

        ByteBuffer buffer = ByteBuffer.allocate(
            16 +  // mysterTypeBytes
            32 +  // prevHash
            8 +   // height
            8 +   // timestamp
            pubkeyEncoded.length + // writerPubkey (variable length)
            32    // payloadHash
        );

        buffer.put(mysterTypeBytes);
        buffer.put(prevHash);
        buffer.putLong(height);
        buffer.putLong(timestamp);
        buffer.put(pubkeyEncoded);
        buffer.put(payloadHash);

        return buffer.array();
    }

    /**
     * Verifies the signature on this block.
     *
     * @param mysterTypeBytes the MysterType shortBytes for this access list (16 bytes)
     * @return true if signature is valid
     */
    public boolean verifySignature(byte[] mysterTypeBytes) {
        try {
            byte[] canonicalBytes = toCanonicalBytes(mysterTypeBytes);
            String algorithm = getSignatureAlgorithm(writerPubkey);

            Signature sig = Signature.getInstance(algorithm);
            sig.initVerify(writerPubkey);
            sig.update(canonicalBytes);

            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Computes the hash of this entire block (for chaining).
     *
     * @return SHA-256 hash of the block bytes (32 bytes)
     */
    public byte[] computeHash() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.write(prevHash);
            dos.writeLong(height);
            dos.writeLong(timestamp);
            byte[] pubkeyEncoded = writerPubkey.getEncoded();
            dos.writeInt(pubkeyEncoded.length);
            dos.write(pubkeyEncoded);
            dos.write(payloadHash);
            dos.writeInt(payload.length);
            dos.write(payload);
            dos.writeInt(signature.length);
            dos.write(signature);

            return computeSHA256(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to compute block hash", e);
        }
    }

    /**
     * Parses the operation from the payload.
     *
     * @return the deserialized operation
     * @throws IOException if parsing fails
     */
    public BlockOperation parsePayload() throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        DataInputStream dis = new DataInputStream(bais);
        return BlockOperation.deserialize(dis);
    }

    private static String getSignatureAlgorithm(PublicKey publicKey) {
        String algorithm = publicKey.getAlgorithm();
        return switch (algorithm) {
            case "Ed25519", "EdDSA" -> "Ed25519";
            case "EC" -> "SHA256withECDSA";
            case "RSA" -> "SHA256withRSA";
            default -> "Ed25519";
        };
    }

    private static byte[] computeSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 must be available", e);
        }
    }

    @Override
    public String toString() {
        return "AccessBlock{height=" + height +
               ", timestamp=" + timestamp +
               ", writer=" + writerPubkey.getAlgorithm() +
               "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccessBlock)) return false;
        AccessBlock that = (AccessBlock) o;
        return height == that.height &&
               timestamp == that.timestamp &&
               Arrays.equals(prevHash, that.prevHash) &&
               Arrays.equals(payloadHash, that.payloadHash);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(prevHash);
        result = 31 * result + Long.hashCode(height);
        result = 31 * result + Long.hashCode(timestamp);
        result = 31 * result + Arrays.hashCode(payloadHash);
        return result;
    }
}

