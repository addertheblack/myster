package com.myster.type.join;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/** Creates and checks the slow, salted verifier stored for an invitation password. */
public final class InvitationPasswordVerifier {
    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int SALT_BYTES = 16;
    public static final int VERIFIER_BYTES = 32;
    public static final int PRODUCTION_ITERATIONS = 210_000;
    public static final int MAX_ACCEPTED_ITERATIONS = 10_000_000;

    /** KDF policy is injectable so tests need not pay the production PBKDF2 cost. */
    record Policy(int iterations) {
        Policy {
            if (iterations <= 0 || iterations > MAX_ACCEPTED_ITERATIONS) {
                throw new IllegalArgumentException("PBKDF2 iterations outside accepted bounds");
            }
        }
    }

    /** Immutable salt, algorithm parameters, and verifier suitable for persistence. */
    record Material(String algorithm, int iterations, byte[] salt, byte[] verifier) {
        Material {
            salt = salt.clone();
            verifier = verifier.clone();
        }

        @Override
        public byte[] salt() {
            return salt.clone();
        }

        @Override
        public byte[] verifier() {
            return verifier.clone();
        }
    }

    private final SecureRandom random;
    private final Policy policy;

    InvitationPasswordVerifier() {
        this(new SecureRandom(), new Policy(PRODUCTION_ITERATIONS));
    }

    InvitationPasswordVerifier(SecureRandom random, Policy policy) {
        this.random = Objects.requireNonNull(random, "random");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Derives a new verifier. The supplied password is borrowed only for this call and is not
     * retained or cleared by this object.
     */
    Material create(char[] password) throws GeneralSecurityException {
        validatePassword(password);
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] verifier = derive(password, ALGORITHM, policy.iterations(), salt);
        try {
            return new Material(ALGORITHM, policy.iterations(), salt, verifier);
        } finally {
            Arrays.fill(verifier, (byte) 0);
        }
    }

    /** Checks a password in constant time after performing the record's bounded KDF. */
    boolean matches(char[] password, TypeInvitation invitation)
            throws GeneralSecurityException {
        validatePassword(password);
        validateParameters(invitation.algorithm(), invitation.iterations(), invitation.salt(),
                invitation.verifier());
        byte[] candidate = derive(password, invitation.algorithm(), invitation.iterations(),
                invitation.salt());
        try {
            return MessageDigest.isEqual(candidate, invitation.verifier());
        } finally {
            Arrays.fill(candidate, (byte) 0);
        }
    }

    static void validateParameters(String algorithm, int iterations, byte[] salt, byte[] verifier) {
        if (!ALGORITHM.equals(algorithm)
                || iterations <= 0 || iterations > MAX_ACCEPTED_ITERATIONS
                || salt.length != SALT_BYTES || verifier.length != VERIFIER_BYTES) {
            throw new IllegalArgumentException("Invalid invitation verifier parameters");
        }
    }

    static void validatePassword(char[] password) {
        Objects.requireNonNull(password, "password");
        if (password.length == 0
                || Character.codePointCount(password, 0, password.length)
                        > TypeJoinUri.MAX_CODE_POINTS) {
            throw new IllegalArgumentException("Invitation password must contain 1 to 256 characters");
        }
    }

    private static byte[] derive(char[] password, String algorithm, int iterations, byte[] salt)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, VERIFIER_BYTES * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
