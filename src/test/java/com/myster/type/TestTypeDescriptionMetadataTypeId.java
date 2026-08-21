package com.myster.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestTypeDescriptionMetadataTypeId {
    @Test
    void constructorWithoutMetadataTypeIdReturnsEmpty() {
        TypeDescription description = new TypeDescription(type(1),
                "TEST",
                "Test",
                new String[] {},
                false,
                true);

        assertTrue(description.getMetadataTypeId().isEmpty());
    }

    @Test
    void constructorWithMetadataTypeIdNormalizes() {
        TypeDescription description = new TypeDescription(type(1),
                "TEST",
                "Test",
                new String[] {},
                false,
                true,
                TypeSource.DEFAULT,
                true,
                " Audio ");

        assertEquals("audio", description.getMetadataTypeId().orElseThrow());
    }

    private static MysterType type(int value) {
        byte[] bytes = new byte[16];
        bytes[15] = (byte) value;
        return new MysterType(bytes);
    }
}
