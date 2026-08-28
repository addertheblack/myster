package com.myster.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestMetadataTypeId {
    @Test
    void knownIdentifiersReturnCanonicalSingletons() {
        assertSame(MetadataTypeId.GENERIC, MetadataTypeId.fromString(" GENERIC "));
        assertSame(MetadataTypeId.AUDIO, MetadataTypeId.fromString("Audio"));
        assertSame(MetadataTypeId.IMAGE, MetadataTypeId.fromString("image"));
        assertSame(MetadataTypeId.VIDEO, MetadataTypeId.fromString("video"));
        assertTrue(MetadataTypeId.AUDIO.isCanonical());
        assertEquals("Audio", MetadataTypeId.AUDIO.getDisplayName());
    }

    @Test
    void futureIdentifiersRemainDistinctNonCanonicalValues() {
        MetadataTypeId spatial = MetadataTypeId.fromString(" SPATIAL_AUDIO ");
        MetadataTypeId repeated = MetadataTypeId.fromString("spatial_audio");
        MetadataTypeId documents = MetadataTypeId.fromString("document_text");

        assertFalse(spatial.isCanonical());
        assertEquals("spatial_audio", spatial.getIdentifier());
        assertEquals(spatial, repeated);
        assertEquals(spatial.hashCode(), repeated.hashCode());
        assertNotEquals(spatial, documents);
        assertNotEquals(spatial, MetadataTypeId.GENERIC);
    }

    @Test
    void futureIdentifierGetsFriendlySafeDisplayName() {
        MetadataTypeId future = MetadataTypeId.fromString("spatial_audio-v2");

        assertEquals("Unknown metadata type — Spatial Audio V2", future.getDisplayName());
        assertFalse(future.getDisplayName().startsWith("<html>"));
        assertNotEquals(future.getIdentifier(), future.getDisplayName());
    }

    @Test
    void malformedIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MetadataTypeId.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> MetadataTypeId.fromString(" "));
        assertThrows(IllegalArgumentException.class,
                () -> MetadataTypeId.fromString("9starts_with_digit"));
        assertThrows(IllegalArgumentException.class,
                () -> MetadataTypeId.fromString("<html>surprise"));
        assertThrows(IllegalArgumentException.class,
                () -> MetadataTypeId.fromString("a".repeat(65)));
    }
}
