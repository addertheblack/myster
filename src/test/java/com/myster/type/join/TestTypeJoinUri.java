package com.myster.type.join;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import com.myster.cid.ServerCid;
import com.myster.type.MysterType;

class TestTypeJoinUri {
    private static final String TYPE = "00112233445566778899aabbccddeeff";
    private static final String BOOTSTRAP = "102132435465768798a9babcbddcedfe";
    private static final String INVITATION = "ffeeddccbbaa99887766554433221100";
    private static final String BASE = "myster://join-type/v1?type=" + TYPE
            + "&bootstrap=" + BOOTSTRAP + "&invitation=" + INVITATION;

    @Test
    void canonicalRoundTripOmitsPassword() throws Exception {
        TypeJoinUri uri = TypeJoinUri.create(MysterType.fromHexString(TYPE),
                new ServerCid(HexFormat.of().parseHex(BOOTSTRAP)),
                HexFormat.of().parseHex(INVITATION));

        assertEquals(BASE, uri.toString());
        TypeJoinUri parsed = TypeJoinUri.parse(uri.toString());
        assertEquals(TYPE, parsed.type().toHexString());
        assertEquals(BOOTSTRAP, parsed.bootstrap().asHex());
        assertArrayEquals(HexFormat.of().parseHex(INVITATION), parsed.invitationId());
        assertTrue(parsed.code().isEmpty());
    }

    @Test
    void acceptsReorderedFieldsUnknownFieldsAndCaseInsensitiveScheme() throws Exception {
        TypeJoinUri parsed = TypeJoinUri.parse("MYSTER://join-type/v1?future=yes&invitation="
                + INVITATION + "&type=" + TYPE + "&bootstrap=" + BOOTSTRAP);
        assertEquals(BASE, parsed.toString());
    }

    @Test
    void decodesCodeOnceAndRedactsDiagnosticForm() throws Exception {
        TypeJoinUri parsed = TypeJoinUri.parse(BASE + "&code=caf%C3%A9%2520secret");
        assertEquals("café%20secret", parsed.code().orElseThrow());
        assertFalse(parsed.toString().contains("caf"));
        assertFalse(parsed.toString().contains("code"));
    }

    @Test
    void plusIsNotDecodedAsSpace() throws Exception {
        assertEquals("a+b", TypeJoinUri.parse(BASE + "&code=a+b").code().orElseThrow());
    }

    @Test
    void rejectsDuplicateMissingUppercaseAndWrongLengthValues() {
        assertThrows(IOException.class, () -> TypeJoinUri.parse(BASE + "&type=" + TYPE));
        assertThrows(IOException.class, () -> TypeJoinUri.parse(
                "myster://join-type/v1?bootstrap=" + BOOTSTRAP + "&invitation=" + INVITATION));
        assertThrows(IOException.class, () -> TypeJoinUri.parse(BASE.replace("aabb", "AABB")));
        assertThrows(IOException.class, () -> TypeJoinUri.parse(BASE.replace(INVITATION,
                INVITATION.substring(2))));
    }

    @Test
    void rejectsWrongShapeFragmentsAndMalformedPercentEncoding() {
        assertThrows(IOException.class, () -> TypeJoinUri.parse(BASE.replace("/v1", "/v2")));
        assertThrows(IOException.class, () -> TypeJoinUri.parse(BASE.replace("join-type", "join")));
        assertThrows(IOException.class, () -> TypeJoinUri.parse(BASE + "#fragment"));
        assertThrows(IOException.class, () -> TypeJoinUri.parse(BASE + "&code=%zz"));
        assertThrows(IOException.class, () -> TypeJoinUri.parse(BASE + "&code=%c3%28"));
    }

    @Test
    void rejectsOversizedUriAndCode() {
        assertThrows(IOException.class, () -> TypeJoinUri.parse(BASE + "&future=" + "x".repeat(4096)));
        assertThrows(IOException.class, () -> TypeJoinUri.parse(BASE + "&code=" + "x".repeat(257)));
    }
}
