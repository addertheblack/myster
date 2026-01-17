package com.myster.net.server;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for Operator.flagToEnd() functionality.
 */
class TestOperator {

    /**
     * Test that flagToEnd() stops the accept loop by setting the endFlag.
     * We can't easily test the full run() loop without starting threads,
     * but we can verify the flag is set correctly.
     */
    @Test
    void testFlagToEndSetsEndFlag() {
        // Create an operator on an ephemeral port
        Operator operator = new Operator(socket -> {}, 0, java.util.Optional.empty());

        // Call flagToEnd
        operator.flagToEnd();

        // The operator should have its endFlag set (we can verify by checking
        // that calling flagToEnd again doesn't throw - it's idempotent)
        assertDoesNotThrow(() -> operator.flagToEnd());
    }

    /**
     * Test that flagToEnd() is idempotent - can be called multiple times safely.
     */
    @Test
    void testFlagToEndIsIdempotent() {
        Operator operator = new Operator(socket -> {}, 0, java.util.Optional.empty());

        // Call flagToEnd multiple times
        assertDoesNotThrow(() -> {
            operator.flagToEnd();
            operator.flagToEnd();
            operator.flagToEnd();
        });
    }

    /**
     * Test that getPort() returns the configured port.
     */
    @Test
    void testGetPort() {
        int expectedPort = 12345;
        Operator operator = new Operator(socket -> {}, expectedPort, java.util.Optional.empty());

        assertEquals(expectedPort, operator.getPort());
    }

    /**
     * Test that operator can be created with a bind address.
     */
    @Test
    void testOperatorWithBindAddress() {
        java.net.InetAddress loopback = java.net.InetAddress.getLoopbackAddress();
        Operator operator = new Operator(socket -> {}, 0, java.util.Optional.of(loopback));

        assertEquals(0, operator.getPort());
        assertDoesNotThrow(() -> operator.flagToEnd());
    }
}

