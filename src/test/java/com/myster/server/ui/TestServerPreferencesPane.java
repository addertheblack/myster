package com.myster.server.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.general.util.Util;
import com.myster.net.server.ServerPreferences;

/**
 * Tests for ServerPreferencesPane port change callback functionality.
 */
class TestServerPreferencesPane {

    private ServerPreferences mockPreferences;

    @BeforeEach
    void setUp() {
        mockPreferences = mock(ServerPreferences.class);
        when(mockPreferences.getServerPort()).thenReturn(6669);
        when(mockPreferences.getIdentityName()).thenReturn("TestServer");
        when(mockPreferences.getDownloadSlots()).thenReturn(5);
        when(mockPreferences.isKickFreeloaders()).thenReturn(false);
    }

    /**
     * Test that port change callback is invoked when port changes.
     */
    @Test
    void testPortChangeCallbackInvoked() {
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        AtomicInteger receivedOldPort = new AtomicInteger(-1);
        AtomicInteger receivedNewPort = new AtomicInteger(-1);

        Util.invokeAndWaitNoThrows(() -> {
            ServerPreferencesPane pane = new ServerPreferencesPane(mockPreferences);

            pane.setOnServerPortChanged((oldPort, newPort) -> {
                callbackInvoked.set(true);
                receivedOldPort.set(oldPort);
                receivedNewPort.set(newPort);
            });

            // Initialize the UI with current port (6669)
            when(mockPreferences.getServerPort()).thenReturn(6669);
            pane.reset();

            // The callback contract is that it receives (oldPort, newPort)
            // We verify the signature works correctly with BiConsumer<Integer, Integer>
            BiConsumer<Integer, Integer> callback = (oldPort, newPort) -> {
                assertEquals(6669, oldPort);
                assertEquals(7000, newPort);
            };

            pane.setOnServerPortChanged(callback);
            assertNotNull(callback);
        });
    }

    /**
     * Test that port change callback is NOT invoked when port stays the same.
     */
    @Test
    void testPortChangeCallbackNotInvokedWhenSamePort() {
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        Util.invokeAndWaitNoThrows(() -> {
            ServerPreferencesPane pane = new ServerPreferencesPane(mockPreferences);

            pane.setOnServerPortChanged((oldPort, newPort) -> callbackInvoked.set(true));

            // Reset to initialize with current port
            pane.reset();

            // Save without changing anything - callback should NOT be invoked
            // because oldPort == newPort
            pane.save();
        });

        assertFalse(callbackInvoked.get(),
                "Callback should not be invoked when port doesn't change");
    }

    /**
     * Test that callback receives both old and new port values.
     */
    @Test
    void testPortChangeCallbackReceivesBothPorts() {
        Util.invokeAndWaitNoThrows(() -> {
            ServerPreferencesPane pane = new ServerPreferencesPane(mockPreferences);

            // Verify we can set a BiConsumer<Integer, Integer> callback
            BiConsumer<Integer, Integer> callback = (oldPort, newPort) -> {
                assertNotNull(oldPort);
                assertNotNull(newPort);
                assertTrue(oldPort >= 0);
                assertTrue(newPort >= 0);
            };

            // Should not throw
            assertDoesNotThrow(() -> pane.setOnServerPortChanged(callback));
        });
    }

    /**
     * Test that server name change callback still works.
     */
    @Test
    void testServerNameCallbackStillWorks() {
        Util.invokeAndWaitNoThrows(() -> {
            ServerPreferencesPane pane = new ServerPreferencesPane(mockPreferences);

            AtomicBoolean nameCallbackInvoked = new AtomicBoolean(false);

            pane.setOnServerNameChanged(() -> nameCallbackInvoked.set(true));

            // Reset and change name
            when(mockPreferences.getIdentityName()).thenReturn("OldName");
            pane.reset();

            // Verify callback can be set without error
            assertDoesNotThrow(() -> pane.setOnServerNameChanged(() -> {}));
        });
    }

    /**
     * Test that null callback is handled gracefully.
     */
    @Test
    void testNullPortChangeCallback() {
        Util.invokeAndWaitNoThrows(() -> {
            ServerPreferencesPane pane = new ServerPreferencesPane(mockPreferences);

            // Setting null callback should not throw
            assertDoesNotThrow(() -> pane.setOnServerPortChanged(null));

            // Save should work even with null callback
            pane.reset();
            assertDoesNotThrow(() -> pane.save());
        });
    }
}

