package com.myster.tracker.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterIdentity;
import com.myster.tracker.MysterServer;

class TestServerPickerModel {
    @Test
    void snapshotAppliesCallerEligibilityAndPreservesServer() throws Exception {
        MysterServer included = server("Included", "127.0.0.1");
        MysterServer excluded = server("Excluded", "127.0.0.2");
        KnownServerSource source = source(List.of(included, excluded));

        List<ServerPickerModel.Row> rows = ServerPickerModel.snapshot(source,
                server -> server != excluded);

        assertEquals(1, rows.size());
        assertSame(included, rows.getFirst().server());
    }

    @Test
    void filtersCaseInsensitivelyByNameAndAddress() throws Exception {
        MysterServer first = server("Friendly Server", "127.0.0.1");
        MysterServer second = server("Another", "192.168.1.2");
        List<ServerPickerModel.Row> rows = List.of(
                ServerPickerModel.row(first), ServerPickerModel.row(second));

        assertEquals(List.of(first), ServerPickerModel.filter(rows, "FRIENDLY").stream()
                .map(ServerPickerModel.Row::server).toList());
        assertEquals(List.of(second), ServerPickerModel.filter(rows, "168.1").stream()
                .map(ServerPickerModel.Row::server).toList());
        assertTrue(ServerPickerModel.filter(rows, "missing").isEmpty());
    }

    @Test
    void nullNameFallsBackToAddress() throws Exception {
        MysterServer unnamed = server(null, "127.0.0.1");

        ServerPickerModel.Row row = ServerPickerModel.row(unnamed);

        assertEquals("127.0.0.1", row.displayName());
        assertEquals(List.of(row), ServerPickerModel.filter(List.of(row), "127.0").stream().toList());
    }

    @Test
    void directResultRemainsVisibleWhenHostnameDoesNotMatchResolvedRow() throws Exception {
        MysterServer resolved = server("Remote Server", "127.0.0.1");

        List<ServerPickerModel.Row> rows = ServerPickerModel.filterIncluding(
                List.of(ServerPickerModel.row(resolved)), "typed.example", resolved);

        assertEquals(1, rows.size());
        assertSame(resolved, rows.getFirst().server());
    }

    @Test
    void directResultIsFirstWhenOtherRowsMatchTheTypedAddress() throws Exception {
        MysterServer matchingKnownServer = server("typed.example mirror", "127.0.0.2");
        MysterServer resolved = server("Remote Server", "127.0.0.1");

        List<ServerPickerModel.Row> rows = ServerPickerModel.filterIncluding(
                List.of(ServerPickerModel.row(matchingKnownServer)), "typed.example", resolved);

        assertEquals(2, rows.size());
        assertSame(resolved, rows.getFirst().server());
        assertSame(matchingKnownServer, rows.get(1).server());
    }

    private static KnownServerSource source(List<MysterServer> servers) {
        return new KnownServerSource() {
            @Override
            public void forEachServer(Consumer<MysterServer> consumer) {
                new ArrayList<>(servers).forEach(consumer);
            }

            @Override
            public Optional<String> resolveDisplayName(ServerCid cid) {
                return Optional.empty();
            }

            @Override
            public PromiseFuture<MysterServer> resolveServer(MysterAddress address) {
                return PromiseFuture.newPromiseFutureException(
                        new UnsupportedOperationException());
            }
        };
    }

    private static MysterServer server(String name, String addressText) throws Exception {
        MysterServer server = Mockito.mock(MysterServer.class);
        Mockito.when(server.getServerName()).thenReturn(name);
        Mockito.when(server.getBestAddress()).thenReturn(Optional.of(
                MysterAddress.createMysterAddress(addressText)));
        Mockito.when(server.getIdentity()).thenReturn(Mockito.mock(MysterIdentity.class));
        Mockito.when(server.isUntried()).thenReturn(true);
        return server;
    }
}
