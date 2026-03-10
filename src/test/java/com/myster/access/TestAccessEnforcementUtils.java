package com.myster.access;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.myster.identity.Cid128;
import com.myster.type.MysterType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the five allow/deny cases in {@link AccessEnforcementUtils#isAllowed}.
 *
 * <ol>
 *   <li>No access list → allow (public type).</li>
 *   <li>Access list with public policy → allow.</li>
 *   <li>Private type, no caller identity → deny.</li>
 *   <li>Private type, known member → allow.</li>
 *   <li>Private type, unknown caller → deny.</li>
 * </ol>
 */
class TestAccessEnforcementUtils {
    private static KeyPair ed25519KeyPair;
    private static KeyPair rsaKeyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        ed25519KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    /** Builds a restrictive access list (listFilesPublic = false, no members). */
    private AccessList buildPrivateList() throws IOException {
        return AccessList.createGenesis(
                rsaKeyPair.getPublic(),
                ed25519KeyPair,
                Collections.emptyList(),
                List.of("onramp1.example.com:6669"),
                Policy.defaultRestrictive(),
                "Private Type",
                null,
                null,
                false);
    }

    /** Builds a public access list (listFilesPublic = true). */
    private AccessList buildPublicList() throws IOException {
        return AccessList.createGenesis(
                rsaKeyPair.getPublic(),
                ed25519KeyPair,
                Collections.emptyList(),
                Collections.emptyList(),
                Policy.defaultPermissive(),
                "Public Type",
                null,
                null,
                false);
    }

    // Case 1 — no access list → allow
    @Test
    void noAccessListAllowsEveryone() {
        MysterType type = new MysterType(rsaKeyPair.getPublic());
        AccessListReader emptyReader = _ -> Optional.empty();

        assertTrue(AccessEnforcementUtils.isAllowed(type, Optional.empty(), emptyReader),
                   "No access list should allow anonymous caller");
        Cid128 cid = com.myster.identity.Util.generateCid(ed25519KeyPair.getPublic());
        assertTrue(AccessEnforcementUtils.isAllowed(type, Optional.of(cid), emptyReader),
                   "No access list should allow identified caller");
    }

    // Case 2 — public policy → allow regardless of caller identity
    @Test
    void publicPolicyAllowsEveryone() throws IOException {
        MysterType type = new MysterType(rsaKeyPair.getPublic());
        AccessList publicList = buildPublicList();
        AccessListReader reader = _ -> Optional.of(publicList);

        assertTrue(AccessEnforcementUtils.isAllowed(type, Optional.empty(), reader),
                   "Public policy should allow anonymous caller");
        Cid128 cid = com.myster.identity.Util.generateCid(ed25519KeyPair.getPublic());
        assertTrue(AccessEnforcementUtils.isAllowed(type, Optional.of(cid), reader),
                   "Public policy should allow identified caller");
    }

    // Case 3 — private type, no caller identity → deny
    @Test
    void privateTypeWithNoCallerDenies() throws IOException {
        MysterType type = new MysterType(rsaKeyPair.getPublic());
        AccessList privateList = buildPrivateList();
        AccessListReader reader = _ -> Optional.of(privateList);

        assertFalse(AccessEnforcementUtils.isAllowed(type, Optional.empty(), reader),
                    "Private type with no caller identity must deny");
    }

    // Case 4 — private type, member caller → allow
    @Test
    void privateTypeAllowsKnownMember() throws Exception {
        KeyPair memberKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Cid128 memberCid = com.myster.identity.Util.generateCid(memberKeyPair.getPublic());

        AccessList privateList = buildPrivateList();
        privateList.appendBlock(new AddMemberOp(memberCid, Role.MEMBER), ed25519KeyPair);

        MysterType type = privateList.getMysterType();
        AccessListReader reader = _ -> Optional.of(privateList);

        assertTrue(AccessEnforcementUtils.isAllowed(type, Optional.of(memberCid), reader),
                   "Known member must be allowed on a private type");
    }

    // Case 5 — private type, unknown caller → deny
    @Test
    void privateTypeDeniesUnknownCaller() throws Exception {
        KeyPair unknownKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Cid128 unknownCid = com.myster.identity.Util.generateCid(unknownKeyPair.getPublic());

        AccessList privateList = buildPrivateList();
        MysterType type = privateList.getMysterType();
        AccessListReader reader = _ -> Optional.of(privateList);

        assertFalse(AccessEnforcementUtils.isAllowed(type, Optional.of(unknownCid), reader),
                    "Unknown caller must be denied on a private type");
    }
}





