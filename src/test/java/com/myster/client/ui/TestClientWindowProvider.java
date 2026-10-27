package com.myster.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.event.WindowAdapter;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.general.thread.AsyncContext;
import com.general.thread.PromiseFuture;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterAddressIdentity;
import com.myster.tracker.MysterServer;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.tracker.ui.KnownServerSource;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

class TestClientWindowProvider {
    private Map<String, MysterAddress> addresses;
    private IdentityResolver resolver;
    private KnownServerSource servers;
    private Function<ClientWindow.ClientWindowData, ClientWindow> factory;
    private ClientWindowProvider provider;
    private ClientWindow window;
    private Runnable closeProgress;
    private AtomicReference<Runnable> cancel;

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        addresses = Map.of(
                "192.0.2.1", MysterAddress.createMysterAddress("192.0.2.1"),
                "192.0.2.2", MysterAddress.createMysterAddress("192.0.2.2"),
                "192.0.2.3", MysterAddress.createMysterAddress("192.0.2.3"));
        resolver = mock(IdentityResolver.class);
        servers = mock(KnownServerSource.class);
        factory = mock(Function.class);
        window = mock(ClientWindow.class);
        when(factory.apply(any())).thenReturn(window);
        provider = spy(new ClientWindowProvider(resolver, factory));
        closeProgress = mock(Runnable.class);
        cancel = new AtomicReference<>();
        doAnswer(invocation -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            cancel.set(invocation.getArgument(1));
            return closeProgress;
        }).when(provider).showLookupProgress(any(), any());
        doNothing().when(provider).showLookupFailure(any(), any());
        var context = mock(MysterFrameContext.class);
        when(context.knownServerSource()).thenReturn(servers);
        SwingUtilities.invokeAndWait(() -> provider.setContext(context));
    }

    private MysterAddress address(String text) { return addresses.get(text); }

    private PublicKeyIdentity identity() throws Exception {
        return new PublicKeyIdentity(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic());
    }

    private ClientWindow.ClientWindowData data(String address, PublicKeyIdentity identity, boolean file) {
        return new ClientWindow.ClientWindowData(address(address), identity,
                Optional.of(new MysterType(new byte[16])), file ? Optional.of("file") : Optional.empty());
    }

    private MysterServer server(PublicKeyIdentity identity) {
        var server = mock(MysterServer.class);
        when(server.getIdentity()).thenReturn(identity);
        when(server.getBestAddress()).thenReturn(Optional.of(address("192.0.2.1")));
        return server;
    }

    private PromiseFuture<ClientWindow> open() throws Exception {
        var opening = new AtomicReference<PromiseFuture<ClientWindow>>();
        SwingUtilities.invokeAndWait(() -> opening.set(provider.openWindow(address("192.0.2.1"),
                Optional.empty(), Optional.empty(), Optional.of("file"))));
        return opening.get();
    }

    private void flush() throws Exception {
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> {});
    }

    @Test void identityReusesWindowAcrossEndpointsAndAppliesInitialData() throws Exception {
        var id = identity();
        SwingUtilities.invokeAndWait(() -> {
            assertSame(window, provider.getOrCreateWindow(data("192.0.2.1", id, false)));
            assertSame(window, provider.getOrCreateWindow(data("192.0.2.2", id, true)));
        });
        verify(factory, times(1)).apply(any());
        verify(window).setInitialData(data("192.0.2.2", id, true));
    }

    @Test void lateCloseNotificationDoesNotRemoveTheReplacementWindow() throws Exception {
        var id = identity();
        var second = mock(ClientWindow.class);
        when(factory.apply(any())).thenReturn(window, second);
        SwingUtilities.invokeAndWait(() -> provider.getOrCreateWindow(data("192.0.2.1", id, false)));
        var cleanup = ArgumentCaptor.forClass(WindowAdapter.class);
        verify(window).addWindowListener(cleanup.capture());
        SwingUtilities.invokeAndWait(() -> {
            cleanup.getValue().windowClosing(null);
            assertSame(second, provider.getOrCreateWindow(data("192.0.2.2", id, true)));
            cleanup.getValue().windowClosed(null);
            assertSame(second, provider.getOrCreateWindow(data("192.0.2.3", id, true)));
        });
        verify(factory, times(2)).apply(any());
    }

    @Test void differentServerKeysAtTheSameAddressUseSeparateWindows() throws Exception {
        var firstId = identity();
        var secondId = identity();
        var second = mock(ClientWindow.class);
        when(factory.apply(any())).thenReturn(window, second);
        SwingUtilities.invokeAndWait(() -> {
            assertSame(window, provider.getOrCreateWindow(data("192.0.2.1", firstId, false)));
            assertSame(second, provider.getOrCreateWindow(data("192.0.2.1", secondId, false)));
            assertSame(window, provider.getOrCreateWindow(data("192.0.2.2", firstId, true)));
        });
        verify(window, never()).setInitialData(data("192.0.2.1", secondId, false));
        verify(factory, times(2)).apply(any());
    }

    @Test void cachedIdentityOpensWithoutNetworkOrProgress() throws Exception {
        var id = identity();
        when(resolver.getCached(address("192.0.2.1"))).thenReturn(Optional.of(id));
        assertSame(window, open().get(5, TimeUnit.SECONDS));
        verifyNoInteractions(servers);
        verify(provider, never()).showLookupProgress(any(), any());
        verify(window).setInitialData(new ClientWindow.ClientWindowData(address("192.0.2.1"), id,
                Optional.empty(), Optional.of("file")));
    }

    @Test void suppliedIdentityIsNeverReplacedByAnAddressCacheEntry() throws Exception {
        var supplied = identity();
        SwingUtilities.invokeAndWait(() -> provider.openWindow(address("192.0.2.1"), Optional.of(supplied),
                Optional.empty(), Optional.empty()));
        verifyNoInteractions(resolver, servers);
        verify(window).setInitialData(new ClientWindow.ClientWindowData(address("192.0.2.1"), supplied,
                Optional.empty(), Optional.empty()));
    }

    @Test void discoveryCreatesNoWindowUntilStatsSupplyAPublicKey() throws Exception {
        var id = identity();
        var pending = new AtomicReference<AsyncContext<MysterServer>>();
        when(servers.resolveServer(any())).thenReturn(PromiseFuture.newPromiseFuture(pending::set));
        var opening = open();
        verifyNoInteractions(factory);
        assertNotNull(cancel.get());
        pending.get().setResult(server(id));
        assertSame(window, opening.get(5, TimeUnit.SECONDS));
        flush();
        verify(closeProgress).run();
        verify(window).setInitialData(new ClientWindow.ClientWindowData(address("192.0.2.1"), id,
                Optional.empty(), Optional.of("file")));
        verify(provider, never()).showLookupFailure(any(), any());
    }

    @Test void unreachableServerReportsFailureAndCanBeRetriedWithoutAProvisionalWindow() throws Exception {
        var failure = new IOException("No response");
        when(servers.resolveServer(any())).thenReturn(PromiseFuture.newPromiseFutureException(failure));
        assertThrows(java.util.concurrent.ExecutionException.class, () -> open().get(5, TimeUnit.SECONDS));
        flush();
        verifyNoInteractions(factory);
        verify(closeProgress).run();
        verify(provider).showLookupFailure(address("192.0.2.1"), failure);
        var reachable = server(identity());
        when(servers.resolveServer(any())).thenReturn(PromiseFuture.newPromiseFuture(reachable));
        assertSame(window, open().get(5, TimeUnit.SECONDS));
        verify(factory, times(1)).apply(any());
    }

    @Test void cancellingProgressCancelsTheApiRequestAndSuppressesLateCreation() throws Exception {
        var pending = new AtomicReference<AsyncContext<MysterServer>>();
        PromiseFuture<MysterServer> request = PromiseFuture.newPromiseFuture(pending::set);
        when(servers.resolveServer(any())).thenReturn(request);
        var opening = open();
        SwingUtilities.invokeAndWait(() -> cancel.get().run());
        pending.get().setResult(server(identity()));
        flush();
        assertTrue(opening.isCancelled());
        assertTrue(request.isCancelled());
        verifyNoInteractions(factory);
        verify(closeProgress).run();
        verify(provider, never()).showLookupFailure(any(), any());
    }

    @Test void statsWithoutAPublicKeyCannotCreateAWindow() throws Exception {
        var server = mock(MysterServer.class);
        when(server.getIdentity()).thenReturn(new MysterAddressIdentity(address("192.0.2.1")));
        when(servers.resolveServer(any())).thenReturn(PromiseFuture.newPromiseFuture(server));
        assertThrows(java.util.concurrent.ExecutionException.class, () -> open().get(5, TimeUnit.SECONDS));
        flush();
        verifyNoInteractions(factory);
        verify(provider).showLookupFailure(eq(address("192.0.2.1")), isA(IOException.class));
    }

    @Test void apiRequestCancellationClosesProgressWithoutCreatingAWindowOrShowingAnError() throws Exception {
        PromiseFuture<MysterServer> request = PromiseFuture.newPromiseFuture(_ -> {});
        when(servers.resolveServer(any())).thenReturn(request);
        var opening = open();
        request.cancel();
        flush();
        assertTrue(opening.isCancelled());
        verifyNoInteractions(factory);
        verify(closeProgress).run();
        verify(provider, never()).showLookupFailure(any(), any());
    }

    @Test void cancellationAfterDiscoveryButBeforeEdtDispatchSkipsWindowCreation() throws Exception {
        PromiseFuture<MysterServer> request = PromiseFuture.newPromiseFuture(server(identity()));
        when(servers.resolveServer(any())).thenReturn(request);
        SwingUtilities.invokeAndWait(() -> {
            var opening = provider.openWindow(address("192.0.2.1"), Optional.empty(),
                    Optional.empty(), Optional.empty());
            opening.cancel();
        });
        flush();
        assertTrue(request.isCancelled());
        verifyNoInteractions(factory);
        verify(closeProgress).run();
        verify(provider, never()).showLookupFailure(any(), any());
    }

    @Test void windowCreationFailureCompletesTheOpeningAndClosesProgress() throws Exception {
        var reachable = server(identity());
        var failure = new IllegalStateException("Could not create window");
        when(servers.resolveServer(any())).thenReturn(PromiseFuture.newPromiseFuture(reachable));
        when(factory.apply(any())).thenThrow(failure);
        var opening = open();
        var thrown = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> opening.get(5, TimeUnit.SECONDS));
        assertSame(failure, thrown.getCause());
        flush();
        verify(closeProgress).run();
        verify(provider).showLookupFailure(address("192.0.2.1"), failure);
    }
}
