package com.myster.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TestCustomTypeDefinition {
    private static KeyPair keyPair;

    @BeforeAll
    static void generateKey() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    @Test
    void legacyConstructorDefaultsToGeneric() {
        CustomTypeDefinition definition = definition(MetadataTypeId.GENERIC, false);

        assertEquals(MetadataTypeId.GENERIC, definition.getMetadataTypeId());
    }

    @Test
    void metadataTypeParticipatesInValueEquality() {
        CustomTypeDefinition audio = definition(MetadataTypeId.AUDIO, true);
        CustomTypeDefinition sameAudio = definition(MetadataTypeId.AUDIO, true);
        CustomTypeDefinition image = definition(MetadataTypeId.IMAGE, true);

        assertEquals(audio, sameAudio);
        assertEquals(audio.hashCode(), sameAudio.hashCode());
        assertNotEquals(audio, image);
    }

    private static CustomTypeDefinition definition(MetadataTypeId id, boolean explicit) {
        if (!explicit) {
            return new CustomTypeDefinition(keyPair.getPublic(), "Test", "Description",
                    new String[] {"dat"}, false, true);
        }
        return new CustomTypeDefinition(keyPair.getPublic(), "Test", "Description",
                new String[] {"dat"}, false, true, id);
    }
}
