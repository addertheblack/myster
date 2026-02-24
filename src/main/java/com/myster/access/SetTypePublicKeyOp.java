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
 * Operation to set the type's RSA public key. Required in the genesis block.
 *
 * <p>This key's MD5 hash produces the {@link com.myster.type.MysterType} shortBytes.
 * Remote nodes download the access list to resolve shortBytes back to the full public key.
 */
public class SetTypePublicKeyOp implements BlockOperation {
    private final PublicKey typePublicKey;

    public SetTypePublicKeyOp(PublicKey typePublicKey) {
        this.typePublicKey = Objects.requireNonNull(typePublicKey, "Type public key cannot be null");
    }

    public PublicKey getTypePublicKey() {
        return typePublicKey;
    }

    @Override
    public OpType getType() {
        return OpType.SET_TYPE_PUBLIC_KEY;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        byte[] encoded = typePublicKey.getEncoded();
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    static SetTypePublicKeyOp deserializePayload(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] encoded = new byte[length];
        in.readFully(encoded);
        try {
            return new SetTypePublicKeyOp(decodePublicKey(encoded));
        } catch (Exception e) {
            throw new IOException("Failed to deserialize type public key", e);
        }
    }

    private static PublicKey decodePublicKey(byte[] encoded) throws NoSuchAlgorithmException, InvalidKeySpecException {
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        String[] algorithms = {"RSA", "Ed25519", "EdDSA", "EC"};
        for (String alg : algorithms) {
            try {
                return KeyFactory.getInstance(alg).generatePublic(keySpec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                // try next
            }
        }
        throw new InvalidKeySpecException("Could not decode public key");
    }

    @Override
    public String toString() {
        return "SetTypePublicKeyOp{algorithm=" + typePublicKey.getAlgorithm() + "}";
    }
}

