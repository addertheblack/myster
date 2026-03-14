package com.myster.type;

import javax.swing.SwingUtilities;
import java.io.File;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import com.myster.access.AccessList;
import com.myster.access.AccessListManager;
import com.myster.access.Policy;
import com.myster.application.MysterGlobals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * Tests for {@link DefaultTypeDescriptionList#importType(AccessList)}.
 */
class TestDefaultTypeDescriptionListImport {

    @TempDir
    File tempDir;

    private static KeyPair edKeyPair;
    private static KeyPair rsaKeyPair;

    private Preferences testPrefs;
    private AccessListManager accessListManager;

    @BeforeAll
    static void generateKeys() throws Exception {
        edKeyPair  = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        testPrefs = Preferences.userRoot().node("MysterTest/ImportType/" + System.nanoTime());
        accessListManager = new AccessListManager();
    }

    private AccessList makeAccessList(String name) throws Exception {
        return AccessList.createGenesis(
                rsaKeyPair.getPublic(),
                edKeyPair,
                Collections.emptyList(),
                List.of("onramp.example.com:6669"),
                Policy.defaultRestrictive(),
                name,
                "description",
                new String[]{"ext"},
                false);
    }

    @Test
    void importType_addsTypeEnabledAndFiresEvent() throws Exception {
        AccessList al = makeAccessList("Movies");
        MysterType type = al.getMysterType();

        try (MockedStatic<MysterGlobals> globals = mockStatic(MysterGlobals.class)) {
            globals.when(MysterGlobals::getAccessListPath).thenReturn(tempDir);
            globals.when(MysterGlobals::getPrivateDataPath).thenReturn(tempDir);

            DefaultTypeDescriptionList tdl =
                    new DefaultTypeDescriptionList(testPrefs, accessListManager);

            AtomicReference<TypeDescriptionEvent> fired = new AtomicReference<>();
            tdl.addTypeListener(new TypeListener() {
                public void typeEnabled(TypeDescriptionEvent e)  { fired.set(e); }
                public void typeDisabled(TypeDescriptionEvent e) {}
            });

            tdl.importType(al);
            SwingUtilities.invokeAndWait(() -> {}); // flush EDT so typeEnabled event fires

            assertTrue(tdl.isTypeEnabled(type), "Imported type should be enabled");
            assertTrue(tdl.get(type).isPresent(), "Imported type should be in the list");
            assertEquals("Movies", tdl.get(type).get().getDescription());
            assertFalse(tdl.get(type).get().isPublic(), "Restrictive-policy type should report isPublic() == false");
            assertEquals(type, fired.get().getType(),
                    "typeEnabled event should fire with correct type");
        }
    }

    @Test
    void importType_duplicate_throwsIllegalArgument() throws Exception {
        AccessList al = makeAccessList("Movies");

        try (MockedStatic<MysterGlobals> globals = mockStatic(MysterGlobals.class)) {
            globals.when(MysterGlobals::getAccessListPath).thenReturn(tempDir);
            globals.when(MysterGlobals::getPrivateDataPath).thenReturn(tempDir);

            DefaultTypeDescriptionList tdl =
                    new DefaultTypeDescriptionList(testPrefs, accessListManager);
            tdl.importType(al);

            assertThrows(IllegalArgumentException.class, () -> {
                try {
                    tdl.importType(al);
                } catch (Exception e) {
                    throw e;
                }
            });
        }
    }

    @Test
    void setEnabledType_customType_persistsViaCustomTypeManager() throws Exception {
        AccessList al = makeAccessList("Persist Test");
        MysterType type = al.getMysterType();

        try (MockedStatic<MysterGlobals> globals = mockStatic(MysterGlobals.class)) {
            globals.when(MysterGlobals::getAccessListPath).thenReturn(tempDir);
            globals.when(MysterGlobals::getPrivateDataPath).thenReturn(tempDir);

            DefaultTypeDescriptionList tdl =
                    new DefaultTypeDescriptionList(testPrefs, accessListManager);

            tdl.importType(al); // imported as enabled

            // Now disable it
            tdl.setEnabledType(type, false);
            assertFalse(tdl.isTypeEnabled(type));

            // Simulate restart: build a fresh instance from the same prefs + disk
            DefaultTypeDescriptionList tdl2 =
                    new DefaultTypeDescriptionList(testPrefs, accessListManager);

            assertFalse(tdl2.isTypeEnabled(type),
                    "Disabled state must survive restart (regression: setEnabledType was not persisting custom types)");
        }
    }
}

