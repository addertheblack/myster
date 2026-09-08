package com.myster.type.join;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.myster.type.MysterType;

class TestInvitationPasswordVerifier {
    private final InvitationPasswordVerifier verifier = new InvitationPasswordVerifier(
            new SecureRandom(), new InvitationPasswordVerifier.Policy(100));

    @Test
    void correctPasswordMatchesAndCaseChangeDoesNot() throws Exception {
        char[] password = "Turtle Battery".toCharArray();
        InvitationPasswordVerifier.Material material = verifier.create(password);
        TypeInvitation invitation = invitation(material);

        assertTrue(verifier.matches(password, invitation));
        assertFalse(verifier.matches("turtle battery".toCharArray(), invitation));
        assertEquals(InvitationPasswordVerifier.SALT_BYTES, material.salt().length);
        assertEquals(InvitationPasswordVerifier.VERIFIER_BYTES, material.verifier().length);
    }

    @Test
    void independentRecordsUseIndependentSalts() throws Exception {
        var first = verifier.create("same".toCharArray());
        var second = verifier.create("same".toCharArray());
        assertNotEquals(java.util.HexFormat.of().formatHex(first.salt()),
                java.util.HexFormat.of().formatHex(second.salt()));
    }

    @Test
    void rejectsEmptyAndOversizedBeforeKdf() {
        assertThrows(IllegalArgumentException.class, () -> verifier.create(new char[0]));
        assertThrows(IllegalArgumentException.class, () -> verifier.create("x".repeat(257).toCharArray()));
    }

    private static TypeInvitation invitation(InvitationPasswordVerifier.Material material) {
        return new TypeInvitation(new MysterType(new byte[16]), new byte[16], material.algorithm(),
                material.iterations(), material.salt(), material.verifier(), 100, 200,
                Optional.empty(), false);
    }
}
