package com.myster.access;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import com.myster.identity.Cid128;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BlockOperation} serialization round-trip of all operation types,
 * including forward compatibility via {@link UnknownOp}.
 */
class TestBlockOperation {

    private static KeyPair ed25519KeyPair;
    private static KeyPair rsaKeyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        ed25519KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    private BlockOperation roundTrip(BlockOperation op) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        op.serialize(dos);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream dis = new DataInputStream(bais);
        return BlockOperation.deserialize(dis);
    }

    @Test
    void setPolicyRoundTrip() throws IOException {
        Policy policy = new Policy(true, false, true);
        SetPolicyOp original = new SetPolicyOp(policy);
        SetPolicyOp restored = (SetPolicyOp) roundTrip(original);
        assertEquals(policy, restored.getPolicy());
    }

    @Test
    void addWriterRoundTrip() throws IOException {
        AddWriterOp original = new AddWriterOp(ed25519KeyPair.getPublic());
        AddWriterOp restored = (AddWriterOp) roundTrip(original);
        assertArrayEquals(original.getWriterPubkey().getEncoded(),
                          restored.getWriterPubkey().getEncoded());
    }

    @Test
    void removeWriterRoundTrip() throws IOException {
        RemoveWriterOp original = new RemoveWriterOp(ed25519KeyPair.getPublic());
        RemoveWriterOp restored = (RemoveWriterOp) roundTrip(original);
        assertArrayEquals(original.getWriterPubkey().getEncoded(),
                          restored.getWriterPubkey().getEncoded());
    }

    @Test
    void addMemberRoundTrip() throws IOException {
        Cid128 cid = com.myster.identity.Util.generateCid(rsaKeyPair.getPublic());
        AddMemberOp original = new AddMemberOp(cid, Role.ADMIN);
        AddMemberOp restored = (AddMemberOp) roundTrip(original);
        assertEquals(cid, restored.getMemberIdentity());
        assertEquals(Role.ADMIN, restored.getRole());
    }

    @Test
    void addMemberWithNonCanonicalRoleRoundTrip() throws IOException {
        Cid128 cid = com.myster.identity.Util.generateCid(rsaKeyPair.getPublic());
        Role futureRole = Role.fromString("SUPER_ADMIN");
        AddMemberOp original = new AddMemberOp(cid, futureRole);
        AddMemberOp restored = (AddMemberOp) roundTrip(original);
        assertEquals(cid, restored.getMemberIdentity());
        assertEquals("SUPER_ADMIN", restored.getRole().getIdentifier());
        assertFalse(restored.getRole().isCanonical());
    }

    @Test
    void removeMemberRoundTrip() throws IOException {
        Cid128 cid = com.myster.identity.Util.generateCid(rsaKeyPair.getPublic());
        RemoveMemberOp original = new RemoveMemberOp(cid);
        RemoveMemberOp restored = (RemoveMemberOp) roundTrip(original);
        assertEquals(cid, restored.getMemberIdentity());
    }

    @Test
    void addOnrampRoundTrip() throws IOException {
        AddOnrampOp original = new AddOnrampOp("myster.example.com:6669");
        AddOnrampOp restored = (AddOnrampOp) roundTrip(original);
        assertEquals("myster.example.com:6669", restored.getEndpoint());
    }

    @Test
    void removeOnrampRoundTrip() throws IOException {
        RemoveOnrampOp original = new RemoveOnrampOp("old.server.com:1234");
        RemoveOnrampOp restored = (RemoveOnrampOp) roundTrip(original);
        assertEquals("old.server.com:1234", restored.getEndpoint());
    }

    @Test
    void setTypePublicKeyRoundTrip() throws IOException {
        SetTypePublicKeyOp original = new SetTypePublicKeyOp(rsaKeyPair.getPublic());
        SetTypePublicKeyOp restored = (SetTypePublicKeyOp) roundTrip(original);
        assertArrayEquals(rsaKeyPair.getPublic().getEncoded(),
                          restored.getTypePublicKey().getEncoded());
    }

    @Test
    void setNameRoundTrip() throws IOException {
        SetNameOp original = new SetNameOp("My Cool Type");
        SetNameOp restored = (SetNameOp) roundTrip(original);
        assertEquals("My Cool Type", restored.getName());
    }

    @Test
    void setDescriptionRoundTrip() throws IOException {
        SetDescriptionOp original = new SetDescriptionOp("A description with Unicode: 日本語");
        SetDescriptionOp restored = (SetDescriptionOp) roundTrip(original);
        assertEquals("A description with Unicode: 日本語", restored.getDescription());
    }

    @Test
    void setExtensionsRoundTrip() throws IOException {
        SetExtensionsOp original = new SetExtensionsOp(new String[]{"mp3", "flac", "ogg"});
        SetExtensionsOp restored = (SetExtensionsOp) roundTrip(original);
        assertArrayEquals(new String[]{"mp3", "flac", "ogg"}, restored.getExtensions());
    }

    @Test
    void setExtensionsEmptyArrayRoundTrip() throws IOException {
        SetExtensionsOp original = new SetExtensionsOp(new String[]{});
        SetExtensionsOp restored = (SetExtensionsOp) roundTrip(original);
        assertArrayEquals(new String[]{}, restored.getExtensions());
    }

    @Test
    void setSearchInArchivesRoundTrip() throws IOException {
        SetSearchInArchivesOp original = new SetSearchInArchivesOp(true);
        SetSearchInArchivesOp restored = (SetSearchInArchivesOp) roundTrip(original);
        assertTrue(restored.isSearchInArchives());

        SetSearchInArchivesOp original2 = new SetSearchInArchivesOp(false);
        SetSearchInArchivesOp restored2 = (SetSearchInArchivesOp) roundTrip(original2);
        assertFalse(restored2.isSearchInArchives());
    }

    @Test
    void unknownOpTypeDeserializesToUnknownOp() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeUTF("FUTURE_OP_2030");
        byte[] fakePayload = {1, 2, 3, 4, 5};
        dos.writeInt(fakePayload.length);
        dos.write(fakePayload);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream dis = new DataInputStream(bais);
        BlockOperation restored = BlockOperation.deserialize(dis);

        assertInstanceOf(UnknownOp.class, restored);
        UnknownOp unknown = (UnknownOp) restored;
        assertFalse(unknown.getType().isCanonical());
        assertEquals("FUTURE_OP_2030", unknown.getType().getIdentifier());
        assertArrayEquals(fakePayload, unknown.getRawPayload());
    }

    @Test
    void unknownOpRoundTrips() throws IOException {
        OpType futureType = OpType.fromString("TELEPORT_USER");
        byte[] payload = {10, 20, 30};
        UnknownOp original = new UnknownOp(futureType, payload);

        UnknownOp restored = (UnknownOp) roundTrip(original);
        assertEquals("TELEPORT_USER", restored.getType().getIdentifier());
        assertArrayEquals(payload, restored.getRawPayload());
    }
}

