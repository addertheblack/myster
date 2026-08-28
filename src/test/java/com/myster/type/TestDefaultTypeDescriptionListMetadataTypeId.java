package com.myster.type;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        assertEquals(MetadataTypeId.AUDIO, builtIn(tdList, "MPG3").getMetadataTypeId());
        assertEquals(MetadataTypeId.IMAGE, builtIn(tdList, "PICT").getMetadataTypeId());
        assertEquals(MetadataTypeId.VIDEO, builtIn(tdList, "MOOV").getMetadataTypeId());
        assertEquals(MetadataTypeId.GENERIC, builtIn(tdList, "TEXT").getMetadataTypeId());
    }

    private static TypeDescription builtIn(DefaultTypeDescriptionList tdList, String internalName) {
        return Arrays.stream(tdList.getAllTypes())
                .filter(typeDescription -> internalName.equals(typeDescription.getInternalName()))
                .findFirst()
                .orElseThrow();
    }
}
