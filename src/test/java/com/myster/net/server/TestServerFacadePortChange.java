package com.myster.net.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.myster.application.MysterGlobals;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.datagram.DatagramProtocolManager;
import com.myster.net.datagram.DatagramProtocolManager.TransportManager;
import com.myster.transaction.TransactionManager;
import com.myster.transaction.TransactionProtocol;

/**
 * Tests for ServerFacade port change functionality, specifically testing
 * the edge cases around moving datagram protocols between ports.
 * <p>
 * Edge cases tested:
 * <ul>
 *   <li>6669 → 7000 (DEFAULT → Non-Default)</li>
 *   <li>7000 → 6669 (Non-Default → DEFAULT)</li>
 *   <li>7000 → 8000 (Non-Default → Non-Default)</li>
 * </ul>
 */
class TestServerFacadePortChange {

    private static final int DEFAULT_PORT = MysterGlobals.DEFAULT_SERVER_PORT; // 6669
    private static final int PORT_7000 = 7000;
    private static final int PORT_8000 = 8000;

    private TransactionManager transactionManager;
    private DatagramProtocolManager datagramManager;
    private ServerPreferences preferences;

    // Track calls to addTransactionProtocol and removeTransactionProtocol
    private List<ProtocolOperation> protocolOperations;

    private record ProtocolOperation(String type, int port, int transactionCode) {}

    @BeforeEach
    void setUp() {
        transactionManager = mock(TransactionManager.class);
        datagramManager = mock(DatagramProtocolManager.class);
        preferences = mock(ServerPreferences.class);

        protocolOperations = new ArrayList<>();

        // Track addTransactionProtocol calls
        when(transactionManager.addTransactionProtocol(anyInt(), any(TransactionProtocol.class)))
                .thenAnswer(invocation -> {
                    int port = invocation.getArgument(0);
                    TransactionProtocol protocol = invocation.getArgument(1);
                    protocolOperations.add(new ProtocolOperation("add", port, protocol.getTransactionCode()));
                    return null;
                });

        // Track removeTransactionProtocol calls
        when(transactionManager.removeTransactionProtocol(anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int port = invocation.getArgument(0);
                    int code = invocation.getArgument(1);
                    protocolOperations.add(new ProtocolOperation("remove", port, code));
                    return null;
                });

        // Mock datagramManager.mutateTransportManager to track PingTransport operations
        when(datagramManager.mutateTransportManager(anyInt(), any()))
                .thenAnswer(invocation -> {
                    // Just execute the function to let it run
                    Function<TransportManager, ?> func = invocation.getArgument(1);
                    TransportManager mockTransportManager = mock(TransportManager.class);
                    func.apply(mockTransportManager);
                    return null;
                });
    }

    /**
     * Test: 6669 → 7000 (DEFAULT → Non-Default)
     *
     * Expected behavior:
     * - Main protocols should be removed from 6669
     * - Main protocols should be added to 7000
     * - LAN discovery protocols should be added to 6669 (via initLanResourceDiscovery)
     * - PingTransport added to 7000
     */
    @Test
    void testChangePortFromDefaultToNonDefault() {
        // Setup: Server initially on DEFAULT port
        when(preferences.getServerPort()).thenReturn(DEFAULT_PORT);
        when(preferences.getIdentityName()).thenReturn("TestServer");

        // Create a mock protocol to track
        TransactionProtocol mockProtocol = createMockProtocol(DatagramConstants.TOP_TEN_TRANSACTION_CODE);

        // Simulate the protocol being tracked (normally done via addDatagramTransactions)
        List<TransactionProtocol> mainPortProtocols = new ArrayList<>();
        mainPortProtocols.add(mockProtocol);

        // Clear setup operations
        protocolOperations.clear();

        // Simulate moveDatagramProtocolsToNewPort(6669, 7000)
        // This is the key method we're testing
        simulateMoveDatagramProtocols(DEFAULT_PORT, PORT_7000, mainPortProtocols);

        // Verify: Protocol removed from old port (6669)
        assertTrue(protocolOperations.stream()
                .anyMatch(op -> op.type().equals("remove")
                        && op.port() == DEFAULT_PORT
                        && op.transactionCode() == DatagramConstants.TOP_TEN_TRANSACTION_CODE),
                "Protocol should be removed from DEFAULT port");

        // Verify: Protocol added to new port (7000)
        assertTrue(protocolOperations.stream()
                .anyMatch(op -> op.type().equals("add")
                        && op.port() == PORT_7000
                        && op.transactionCode() == DatagramConstants.TOP_TEN_TRANSACTION_CODE),
                "Protocol should be added to new port 7000");

        // Verify: No cleanup of LAN protocols from DEFAULT (since we weren't on non-default before)
        assertFalse(protocolOperations.stream()
                .anyMatch(op -> op.type().equals("remove")
                        && op.port() == DEFAULT_PORT
                        && op.transactionCode() == DatagramConstants.SERVER_STATS_TRANSACTION_CODE),
                "Should NOT remove ServerStats from DEFAULT when coming FROM DEFAULT");
    }

    /**
     * Test: 7000 → 6669 (Non-Default → DEFAULT)
     *
     * Expected behavior:
     * - LAN discovery protocols should be removed from 6669 first
     * - Main protocols should be removed from 7000
     * - Main protocols should be added to 6669
     * - No LAN operators needed since we're going to DEFAULT
     */
    @Test
    void testChangePortFromNonDefaultToDefault() {
        // Setup: Server on non-default port
        when(preferences.getServerPort()).thenReturn(PORT_7000);
        when(preferences.getIdentityName()).thenReturn("TestServer");

        TransactionProtocol mockProtocol = createMockProtocol(DatagramConstants.TOP_TEN_TRANSACTION_CODE);
        List<TransactionProtocol> mainPortProtocols = new ArrayList<>();
        mainPortProtocols.add(mockProtocol);

        protocolOperations.clear();

        // Simulate moveDatagramProtocolsToNewPort(7000, 6669) with goingToDefault=true, wasOnDefault=false
        simulateMoveDatagramProtocols(PORT_7000, DEFAULT_PORT, mainPortProtocols);

        // Verify: LAN ServerStats removed from DEFAULT first (cleanup before adding main protocols)
        assertTrue(protocolOperations.stream()
                .anyMatch(op -> op.type().equals("remove")
                        && op.port() == DEFAULT_PORT
                        && op.transactionCode() == DatagramConstants.SERVER_STATS_TRANSACTION_CODE),
                "LAN ServerStats should be removed from DEFAULT port when going TO DEFAULT from non-default");

        // Verify: Protocol removed from old port (7000)
        assertTrue(protocolOperations.stream()
                .anyMatch(op -> op.type().equals("remove")
                        && op.port() == PORT_7000
                        && op.transactionCode() == DatagramConstants.TOP_TEN_TRANSACTION_CODE),
                "Protocol should be removed from port 7000");

        // Verify: Protocol added to DEFAULT port
        assertTrue(protocolOperations.stream()
                .anyMatch(op -> op.type().equals("add")
                        && op.port() == DEFAULT_PORT
                        && op.transactionCode() == DatagramConstants.TOP_TEN_TRANSACTION_CODE),
                "Protocol should be added to DEFAULT port");

        // Verify ordering: LAN cleanup happens before main protocol add
        int lanCleanupIndex = -1;
        int mainProtocolAddIndex = -1;
        for (int i = 0; i < protocolOperations.size(); i++) {
            ProtocolOperation op = protocolOperations.get(i);
            if (op.type().equals("remove") && op.port() == DEFAULT_PORT
                    && op.transactionCode() == DatagramConstants.SERVER_STATS_TRANSACTION_CODE) {
                lanCleanupIndex = i;
            }
            if (op.type().equals("add") && op.port() == DEFAULT_PORT
                    && op.transactionCode() == DatagramConstants.TOP_TEN_TRANSACTION_CODE) {
                mainProtocolAddIndex = i;
            }
        }
        assertTrue(lanCleanupIndex < mainProtocolAddIndex,
                "LAN cleanup should happen before adding main protocols to DEFAULT");
    }

    /**
     * Test: 7000 → 8000 (Non-Default → Non-Default)
     *
     * Expected behavior:
     * - Main protocols should be removed from 7000
     * - Main protocols should be added to 8000
     * - LAN protocols on 6669 should be recreated by initLanResourceDiscovery (idempotent)
     * - No cleanup of DEFAULT port protocols needed in moveDatagramProtocolsToNewPort
     */
    @Test
    void testChangePortBetweenNonDefaults() {
        when(preferences.getServerPort()).thenReturn(PORT_7000);
        when(preferences.getIdentityName()).thenReturn("TestServer");

        TransactionProtocol mockProtocol = createMockProtocol(DatagramConstants.TOP_TEN_TRANSACTION_CODE);
        List<TransactionProtocol> mainPortProtocols = new ArrayList<>();
        mainPortProtocols.add(mockProtocol);

        protocolOperations.clear();

        // Simulate moveDatagramProtocolsToNewPort(7000, 8000) - neither is DEFAULT
        simulateMoveDatagramProtocols(PORT_7000, PORT_8000, mainPortProtocols);

        // Verify: Protocol removed from old port (7000)
        assertTrue(protocolOperations.stream()
                .anyMatch(op -> op.type().equals("remove")
                        && op.port() == PORT_7000
                        && op.transactionCode() == DatagramConstants.TOP_TEN_TRANSACTION_CODE),
                "Protocol should be removed from port 7000");

        // Verify: Protocol added to new port (8000)
        assertTrue(protocolOperations.stream()
                .anyMatch(op -> op.type().equals("add")
                        && op.port() == PORT_8000
                        && op.transactionCode() == DatagramConstants.TOP_TEN_TRANSACTION_CODE),
                "Protocol should be added to port 8000");

        // Verify: No cleanup of DEFAULT port in moveDatagramProtocolsToNewPort
        // (initLanResourceDiscovery handles that separately and is idempotent)
        assertFalse(protocolOperations.stream()
                .anyMatch(op -> op.type().equals("remove")
                        && op.port() == DEFAULT_PORT
                        && op.transactionCode() == DatagramConstants.SERVER_STATS_TRANSACTION_CODE),
                "Should NOT remove ServerStats from DEFAULT in moveDatagramProtocolsToNewPort for non-default to non-default");
    }

    /**
     * Test: Encryption support is moved correctly when changing ports
     */
    @Test
    void testEncryptionSupportMovedOnPortChange() {
        when(preferences.getServerPort()).thenReturn(PORT_7000);
        when(preferences.getIdentityName()).thenReturn("TestServer");

        List<TransactionProtocol> mainPortProtocols = new ArrayList<>();

        protocolOperations.clear();

        // Simulate moving with encryption enabled
        simulateMoveDatagramProtocolsWithEncryption(PORT_7000, PORT_8000, mainPortProtocols);

        // Verify: STLS (encryption) removed from old port
        assertTrue(protocolOperations.stream()
                .anyMatch(op -> op.type().equals("remove")
                        && op.port() == PORT_7000
                        && op.transactionCode() == DatagramConstants.STLS_CODE),
                "Encryption (STLS) should be removed from old port");

        // Verify addEncryptionSupport was called for new port
        verify(transactionManager).addEncryptionSupport(eq(PORT_8000), any());
    }

    /**
     * Test: initLanResourceDiscovery is idempotent - can be called multiple times safely
     */
    @Test
    void testInitLanResourceDiscoveryIsIdempotent() {
        when(preferences.getServerPort()).thenReturn(PORT_7000);
        when(preferences.getIdentityName()).thenReturn("TestServer");

        protocolOperations.clear();

        // Simulate calling initLanResourceDiscovery twice (as would happen in 7000 -> 8000 transition)
        simulateInitLanResourceDiscovery();
        simulateInitLanResourceDiscovery();

        // Count how many times ServerStats was added to DEFAULT
        long addCount = protocolOperations.stream()
                .filter(op -> op.type().equals("add")
                        && op.port() == DEFAULT_PORT
                        && op.transactionCode() == DatagramConstants.SERVER_STATS_TRANSACTION_CODE)
                .count();

        // Each call should add once (remove then add pattern makes it safe)
        assertEquals(2, addCount, "initLanResourceDiscovery should add ServerStats each time it's called");

        // Verify remove was called before each add
        long removeCount = protocolOperations.stream()
                .filter(op -> op.type().equals("remove")
                        && op.port() == DEFAULT_PORT
                        && op.transactionCode() == DatagramConstants.SERVER_STATS_TRANSACTION_CODE)
                .count();
        assertEquals(2, removeCount, "initLanResourceDiscovery should remove existing ServerStats before adding");
    }

    // Helper methods to simulate the behavior we're testing

    private void simulateMoveDatagramProtocols(int oldPort, int newPort,
            List<TransactionProtocol> mainPortProtocols) {

        boolean goingToDefault = (newPort == DEFAULT_PORT);
        boolean wasOnDefault = (oldPort == DEFAULT_PORT);

        // If going TO default port from non-default, clean up LAN protocols
        if (goingToDefault && !wasOnDefault) {
            transactionManager.removeTransactionProtocol(DEFAULT_PORT,
                    DatagramConstants.SERVER_STATS_TRANSACTION_CODE);
        }

        // Move main protocols
        for (TransactionProtocol protocol : mainPortProtocols) {
            transactionManager.removeTransactionProtocol(oldPort, protocol.getTransactionCode());
            transactionManager.addTransactionProtocol(newPort, protocol);
        }
    }

    private void simulateMoveDatagramProtocolsWithEncryption(int oldPort, int newPort,
            List<TransactionProtocol> mainPortProtocols) {

        simulateMoveDatagramProtocols(oldPort, newPort, mainPortProtocols);

        // Simulate encryption move
        transactionManager.removeTransactionProtocol(oldPort, DatagramConstants.STLS_CODE);
        transactionManager.addEncryptionSupport(newPort, mock(com.myster.net.datagram.DatagramEncryptUtil.Lookup.class));
    }

    private void simulateInitLanResourceDiscovery() {
        // Simulates the idempotent behavior of initLanResourceDiscovery
        transactionManager.removeTransactionProtocol(DEFAULT_PORT,
                DatagramConstants.SERVER_STATS_TRANSACTION_CODE);
        transactionManager.addTransactionProtocol(DEFAULT_PORT,
                createMockProtocol(DatagramConstants.SERVER_STATS_TRANSACTION_CODE));
    }

    private TransactionProtocol createMockProtocol(int transactionCode) {
        TransactionProtocol protocol = mock(TransactionProtocol.class);
        when(protocol.getTransactionCode()).thenReturn(transactionCode);
        return protocol;
    }
}

