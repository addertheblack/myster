package com.myster.type;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestTypeDescriptionMetadataTypeId {
    @Test
    void constructorWithoutMetadataTypeIdReturnsGeneric() {
        TypeDescription description = new TypeDescription(type(1),
                "TEST",
                "Test",
                new String[] {},
                false,
                true);

        assertEquals(MetadataTypeId.GENERIC, description.getMetadataTypeId());
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
                MetadataTypeId.fromString(" Audio "));

        assertEquals(MetadataTypeId.AUDIO, description.getMetadataTypeId());
    }

    private static MysterType type(int value) {
        byte[] bytes = new byte[16];
        bytes[15] = (byte) value;
        return new MysterType(bytes);
    }
}
