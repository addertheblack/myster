package com.myster.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.prefs.Preferences;

import com.myster.access.AccessListManager;
import org.junit.jupiter.api.Test;

class TestDefaultTypeDescriptionListMetadataTypeId {
    @Test
    void builtInTypesCarryMetadataTypeIds() {
        DefaultTypeDescriptionList tdList = new DefaultTypeDescriptionList(
                Preferences.userRoot().node("MysterTest/MetadataTypeId/" + System.nanoTime()),
                new AccessListManager());

        assertEquals("audio", builtIn(tdList, "MPG3").getMetadataTypeId().orElseThrow());
        assertEquals("image", builtIn(tdList, "PICT").getMetadataTypeId().orElseThrow());
        assertEquals("video", builtIn(tdList, "MOOV").getMetadataTypeId().orElseThrow());
        assertTrue(builtIn(tdList, "TEXT").getMetadataTypeId().isEmpty());
    }

    private static TypeDescription builtIn(DefaultTypeDescriptionList tdList, String internalName) {
        return Arrays.stream(tdList.getAllTypes())
                .filter(typeDescription -> internalName.equals(typeDescription.getInternalName()))
                .findFirst()
                .orElseThrow();
    }
}
