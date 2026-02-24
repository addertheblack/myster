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
 * Operation to remove a writer from the access list.
 * After removal, that writer can no longer append blocks.
 */
public class RemoveWriterOp implements BlockOperation {
    private final PublicKey writerPubkey;

    public RemoveWriterOp(PublicKey writerPubkey) {
        this.writerPubkey = Objects.requireNonNull(writerPubkey, "Writer public key cannot be null");
    }

    public PublicKey getWriterPubkey() {
        return writerPubkey;
    }

    @Override
    public OpType getType() {
        return OpType.REMOVE_WRITER;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        byte[] encoded = writerPubkey.getEncoded();
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    static RemoveWriterOp deserializePayload(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] encoded = new byte[length];
        in.readFully(encoded);
        try {
            return new RemoveWriterOp(decodePublicKey(encoded));
        } catch (Exception e) {
            throw new IOException("Failed to deserialize writer public key", e);
        }
    }

    private static PublicKey decodePublicKey(byte[] encoded) throws NoSuchAlgorithmException, InvalidKeySpecException {
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        String[] algorithms = {"Ed25519", "EdDSA", "EC", "RSA"};
        for (String alg : algorithms) {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance(alg);
                return keyFactory.generatePublic(keySpec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                // try next
            }
        }
        throw new InvalidKeySpecException("Could not decode public key");
    }

    @Override
    public String toString() {
        return "RemoveWriterOp{pubkey=" + writerPubkey.getAlgorithm() + "}";
    }
}
