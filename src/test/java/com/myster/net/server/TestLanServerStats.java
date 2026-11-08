package com.myster.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.myster.application.MysterGlobals;
import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Identity;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.datagram.DatagramProtocolManager;
import com.myster.net.mdns.MysterMdnsAnnouncer;
import com.myster.net.server.datagram.BidirectionalServerStatsDatagramServer;
import com.myster.net.server.datagram.ServerStatsDatagramServer;
import com.myster.net.stream.StreamConstants;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.net.stream.server.ServerStats;
import com.myster.server.event.ServerEventDispatcher;
import com.myster.tracker.MysterServerPool;
import com.myster.tracker.Tracker;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionManager;
import com.myster.transaction.TransactionProtocol;
import com.myster.type.MysterType;

/** Exercises the real facade and connection dispatcher with in-memory sockets. */
class TestLanServerStats {
    private static final int DEFAULT_PORT = MysterGlobals.DEFAULT_SERVER_PORT;

    private final ServerPreferences preferences = mock(ServerPreferences.class);
    private final FileTypeListManager files = mock(FileTypeListManager.class);
    private final Identity identity = mock(Identity.class);
    private final MysterServerPool pool = mock(MysterServerPool.class);
    private final Map<Integer, Consumer<Socket>> listeners = new HashMap<>();
    private final Map<Integer, Map<Integer, TransactionProtocol>> protocols = new HashMap<>();
    private MockedStatic<ServerUtils> serverUtils;
    private MockedConstruction<Operator> operators;
    private MockedConstruction<MysterMdnsAnnouncer> mdns;
    private ServerFacade facade;

    @BeforeEach
    void setUp() throws Exception {
        when(preferences.getIdentityName()).thenReturn("LAN stats server");
        when(preferences.getDescription()).thenReturn("Discovery test");
        when(files.getFileTypeListing()).thenReturn(new MysterType[0]);
        when(identity.getMainIdentity()).thenReturn(Optional.empty());
        serverUtils = mockStatic(ServerUtils.class);
        serverUtils.when(ServerUtils::findMyLanAddress)
                .thenReturn(List.of(InetAddress.getLoopbackAddress()));
        operators = mockConstruction(Operator.class, (_, context) -> {
            @SuppressWarnings("unchecked")
            Consumer<Socket> consumer = (Consumer<Socket>) context.arguments().get(0);
            listeners.put((Integer) context.arguments().get(1), consumer);
            if (context.arguments().get(2).equals(Optional.of(InetAddress.getLoopbackAddress()))) {
                assertEquals(DEFAULT_PORT, context.arguments().get(1));
            } else {
                assertEquals(Optional.empty(), context.arguments().get(2));
            }
        });
        mdns = mockConstruction(MysterMdnsAnnouncer.class);
    }

    @AfterEach
    void tearDown() {
        mdns.close();
        operators.close();
        serverUtils.close();
    }

    @Test
    void customPortStartupServesTcpStatsOnLanDiscoveryPort() throws Exception {
        startFacade(7000);

        assertTcpStats(DEFAULT_PORT, 7000);
        assertTcpStats(7000, 7000);
        assertDiscoveryRejectsSearch();
    }

    @Test
    void customPortStartupServesBothUdpStatsOnDiscoveryPort() throws Exception {
        startFacade(7000);

        assertUdpStats(DEFAULT_PORT, 7000);
        assertUdpStats(7000, 7000);
    }

    @ParameterizedTest
    @CsvSource({ "6669,7000", "7000,6669", "7000,8000" })
    void statsSurvivePortChanges(int oldPort, int newPort) throws Exception {
        startFacade(oldPort);
        when(preferences.getServerPort()).thenReturn(newPort);

        facade.changePort(oldPort, newPort);

        assertTcpStats(DEFAULT_PORT, newPort);
        assertUdpStats(DEFAULT_PORT, newPort);
        assertTcpStats(newPort, newPort);
        assertUdpStats(newPort, newPort);
        if (newPort != DEFAULT_PORT) {
            assertDiscoveryRejectsSearch();
            assertFalse(protocols.get(DEFAULT_PORT).containsKey(DatagramConstants.TOP_TEN_TRANSACTION_CODE));
        }
        if (oldPort != DEFAULT_PORT) {
            assertTrue(protocols.get(oldPort).isEmpty(), "Old main-port handlers must be removed");
        }
    }

    private void startFacade(int port) {
        when(preferences.getServerPort()).thenReturn(port);
        TransactionManager transactions = mock(TransactionManager.class);
        when(transactions.addTransactionProtocol(anyInt(), any())).thenAnswer(invocation -> {
            int targetPort = invocation.getArgument(0);
            TransactionProtocol protocol = invocation.getArgument(1);
            return protocols.computeIfAbsent(targetPort, _ -> new HashMap<>())
                    .put(protocol.getTransactionCode(), protocol);
        });
        when(transactions.removeTransactionProtocol(anyInt(), anyInt())).thenAnswer(invocation ->
                protocols.computeIfAbsent(invocation.getArgument(0), _ -> new HashMap<>())
                        .remove((Integer) invocation.getArgument(1)));
        DatagramProtocolManager datagrams = mock(DatagramProtocolManager.class);
        when(datagrams.mutateTransportManager(anyInt(), any())).thenAnswer(invocation -> {
            DatagramProtocolManager.AccessTransportManager<?> action = invocation.getArgument(1);
            return action.apply(mock(DatagramProtocolManager.TransportManager.class));
        });
        facade = new ServerFacade(mock(Tracker.class), pool, preferences, datagrams, transactions,
                                  identity, files, null,
                                  mock(ServerEventDispatcher.class, Mockito.RETURNS_DEEP_STUBS));
        facade.addConnectionSection(new ServerStats(preferences::getIdentityName,
                preferences::getDescription, preferences::getServerPort, identity, null));
        TransactionProtocol otherProtocol = mock(TransactionProtocol.class);
        when(otherProtocol.getTransactionCode()).thenReturn(DatagramConstants.TOP_TEN_TRANSACTION_CODE);
        facade.addDatagramTransactions(
                new ServerStatsDatagramServer(preferences::getIdentityName,
                        preferences::getDescription, preferences::getServerPort, identity, files, null),
                new BidirectionalServerStatsDatagramServer(preferences::getIdentityName,
                        preferences::getDescription, preferences::getServerPort, identity, files, pool, null),
                otherProtocol);
    }

    private void assertTcpStats(int port, int advertisedPort) throws Exception {
        byte[] response = tcpRequest(port, StreamConstants.SERVER_STATS_SECTION_NUMBER);
        try (var in = new MysterDataInputStream(new ByteArrayInputStream(response))) {
            assertEquals(1, in.read(), "TCP 101 must be accepted on port " + port);
            assertStats(in.readMessagePack(), advertisedPort);
        }
    }

    private void assertDiscoveryRejectsSearch() throws Exception {
        byte[] response = tcpRequest(DEFAULT_PORT, StreamConstants.SEARCH_SECTION_NUMBER);
        assertEquals(0, response[0], "The discovery listener must remain limited to stats");
    }

    private byte[] tcpRequest(int port, int section) throws Exception {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        CountDownLatch finished = new CountDownLatch(1);
        Socket socket = mock(Socket.class);
        when(socket.getInetAddress()).thenReturn(InetAddress.getLoopbackAddress());
        when(socket.getLocalPort()).thenReturn(port);
        when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(
                ByteBuffer.allocate(4).putInt(section).array()));
        when(socket.getOutputStream()).thenReturn(response);
        doAnswer(_ -> { finished.countDown(); return null; }).when(socket).close();
        listeners.get(port).accept(socket);
        assertTrue(finished.await(5, TimeUnit.SECONDS), "Connection handler must finish");
        return response.toByteArray();
    }

    private void assertUdpStats(int port, int advertisedPort) throws Exception {
        for (int code : new int[] { DatagramConstants.SERVER_STATS_TRANSACTION_CODE,
                                  DatagramConstants.BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE }) {
            TransactionProtocol handler = protocols.get(port).get(code);
            assertNotNull(handler, "UDP " + code + " must be registered on port " + port);
            MessagePak card = MessagePak.newEmpty();
            card.putInt(ServerStats.PORT, 7001);
            ByteArrayOutputStream request = new ByteArrayOutputStream();
            try (var out = new MysterDataOutputStream(request)) {
                out.writeMessagePack(card);
            }
            List<Transaction> replies = new ArrayList<>();
            Transaction transaction = mock(Transaction.class);
            when(transaction.getAddress()).thenReturn(
                    new MysterAddress(InetAddress.getLoopbackAddress(), 54000));
            when(transaction.getTransactionCode()).thenReturn(code);
            when(transaction.getData()).thenReturn(code == DatagramConstants.SERVER_STATS_TRANSACTION_CODE
                    ? new byte[0] : request.toByteArray());
            when(transaction.callerCid()).thenReturn(Optional.empty());
            handler.transactionReceived(replies::add, transaction, null);
            assertEquals(1, replies.size());
            try (var in = new MysterDataInputStream(new ByteArrayInputStream(replies.getFirst().getData()))) {
                assertStats(in.readMessagePack(), advertisedPort);
            }
        }
        verify(pool, Mockito.atLeastOnce()).suggestAddress(
                new MysterAddress(InetAddress.getLoopbackAddress(), 7001));
    }

    private void assertStats(MessagePak stats, int advertisedPort) {
        assertEquals(advertisedPort, stats.getInt(ServerStats.PORT).orElseThrow());
        assertEquals("LAN stats server", stats.getString(ServerStats.SERVER_NAME).orElseThrow());
    }
}
