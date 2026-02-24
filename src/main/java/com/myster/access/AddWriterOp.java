package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

/**
 * Operation to add a writer (Ed25519 public key) to the access list.
 * Writers can sign and append new blocks.
 */
public class AddWriterOp implements BlockOperation {
    private final PublicKey writerPubkey;

    public AddWriterOp(PublicKey writerPubkey) {
        this.writerPubkey = Objects.requireNonNull(writerPubkey, "Writer public key cannot be null");
    }

    public PublicKey getWriterPubkey() {
        return writerPubkey;
    }

    @Override
    public OpType getType() {
        return OpType.ADD_WRITER;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        byte[] encoded = writerPubkey.getEncoded();
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    static AddWriterOp deserializePayload(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] encoded = new byte[length];
        in.readFully(encoded);
        try {
            return new AddWriterOp(decodePublicKey(encoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IOException("Failed to deserialize writer public key", ex);
        }
    }

    private static PublicKey decodePublicKey(byte[] encoded) throws NoSuchAlgorithmException, InvalidKeySpecException {
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        String[] algorithms = {"Ed25519", "EdDSA", "EC", "RSA"};
        for (String alg : algorithms) {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance(alg);
                return keyFactory.generatePublic(keySpec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
                // try next
            }
        }
        throw new InvalidKeySpecException("Could not decode public key");
    }

    @Override
    public String toString() {
        return "AddWriterOp{pubkey=" + writerPubkey.getAlgorithm() + "}";
    }
}
