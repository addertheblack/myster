package com.myster.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.general.mclist.JMCList;
import com.general.mclist.TreeMCList;
import com.general.mclist.TreeMCListTableModel;
import com.general.mclist.TreeMCListTableModel.TreePathString;
import com.general.thread.AsyncContext;
import com.general.thread.PromiseFuture;
import com.general.util.MessageField;
import com.myster.client.ui.ClientWindow.ClientWindowData;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterDatagram;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.MysterStream;
import com.myster.net.client.ParamBuilder;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.tracker.Tracker;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowPrefDataKeeper;
import com.myster.ui.WindowPrefDataKeeper.PrefData;
import com.myster.ui.WindowPrefDataKeeper.WindowLocation;

class TestClientWindow {
    private ClientWindow window;
    private MysterStream stream;
    private MysterDatagram datagram;
    private JMCList<MysterType> types;
    private JMCList<String> files;
    private JButton connect;
    private final MysterType type = new MysterType(new byte[16]);
    private final MysterType otherType = new MysterType(java.util.HexFormat.of().parseHex("11111111111111111111111111111111"));
    private AtomicReference<AsyncContext<MysterType[]>> pendingTypes;
    private PublicKey key;
    private MysterAddress address;

    @BeforeEach void setup() throws Exception {
        address = MysterAddress.createMysterAddress("127.0.0.1:7777");
        // Only bypass native JFrame construction. Navigation, list models, listeners, workers,
        // and their completion callbacks below are the real production code.
        window = mock(ClientWindow.class, CALLS_REAL_METHODS);
        var protocol = mock(MysterProtocol.class);
        stream = mock(MysterStream.class);
        datagram = mock(MysterDatagram.class);
        when(protocol.getStream()).thenReturn(stream);
        when(protocol.getDatagram()).thenReturn(datagram);
        pendingTypes = new AtomicReference<>();
        when(datagram.getTypes(any())).thenReturn(PromiseFuture.newPromiseFuture(pendingTypes::set));
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenAnswer(_ -> listing());
        var descriptions = mock(TypeDescriptionList.class);
        for (var selectedType : java.util.List.of(type, otherType)) {
            when(descriptions.get(selectedType)).thenReturn(Optional.of(new TypeDescription(
                    selectedType, "test", selectedType.toHexString(), new String[]{}, false, true)));
        }
        key = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        doNothing().when(window).setTitle(anyString());
        doNothing().when(window).startStats();
        doReturn(mock(MysterFrameContext.class)).when(window).getMysterFrameContext();
        SwingUtilities.invokeAndWait(() -> {
            try {
                types = new JMCList<>(2, true);
                files = TreeMCList.create(new String[]{"Name", "Size"}, new TreePathString(new String[0]));
                connect = new JButton();
                field("protocol", protocol);
                field("tracker", mock(Tracker.class));
                field("serverAddress", address);
                field("serverIdentity", new PublicKeyIdentity(key));
                field("typeDescriptionList", descriptions);
                field("initialData", Optional.empty());
                field("typeDisplayNames", new HashMap<>());
                field("containers", new HashMap<>());
                field("serverAddressField", new JTextField("127.0.0.1:7777"));
                field("refreshButton", connect);
                field("fileTypeList", types);
                field("fileList", files);
                field("statsPanel", new JTextArea());
                field("msg", new MessageField(""));
                field("savePrefs", (Runnable) () -> {});
                window.installSelectionListeners();
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    private void field(String name, Object value) throws Exception {
        Field field = ClientWindow.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(window, value);
    }

    @SuppressWarnings("unchecked")
    private Optional<ClientWindowData> initialData() throws Exception {
        var field = ClientWindow.class.getDeclaredField("initialData");
        field.setAccessible(true);
        return (Optional<ClientWindowData>) field.get(window);
    }

    private ClientWindowData destination(Optional<String> file) {
        return new ClientWindowData(address, new PublicKeyIdentity(key), Optional.of(type), file);
    }

    private MysterSocket listing() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = new MysterDataOutputStream(bytes);
        output.writeByte(1);
        output.writeInt(2);
        for (String name : java.util.List.of("unrelated", "folder/target")) {
            output.writeUTF(name);
            var metadata = MessagePak.newEmpty();
            metadata.putLong("/size", 12);
            if (name.startsWith("folder/")) metadata.putStringArray("/path", new String[]{"folder", "target"});
            output.writeMessagePack(metadata);
        }
        return mock(MysterSocket.class, withSettings().useConstructor(
                new MysterDataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
                new MysterDataOutputStream(new ByteArrayOutputStream())));
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        var satisfied = new java.util.concurrent.atomic.AtomicBoolean();
        do {
            SwingUtilities.invokeAndWait(() -> satisfied.set(condition.getAsBoolean()));
            if (satisfied.get()) return;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        fail("Navigation did not reach the expected state");
    }

    private void deliverTypes() { pendingTypes.get().setResult(new MysterType[]{otherType, type}); }

    @Test void windowRestorationSkipsSavedDestinationsWithoutValidAddresses() throws Exception {
        var keeper = mock(WindowPrefDataKeeper.class);
        var provider = mock(ClientWindowProvider.class);
        var restoredWindow = mock(ClientWindow.class);
        var context = mock(MysterFrameContext.class);
        when(context.keeper()).thenReturn(keeper);
        when(context.clientWindowProvider()).thenReturn(provider);
        when(provider.openWindow(any(), any(), any(), any())).thenAnswer(_ -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            return PromiseFuture.newPromiseFuture(restoredWindow);
        });
        var missingAddress = mock(Preferences.class);
        var blankAddress = mock(Preferences.class);
        var malformedAddress = mock(Preferences.class);
        var validAddress = mock(Preferences.class);
        when(blankAddress.get("Ip Key", null)).thenReturn(" ");
        when(malformedAddress.get("Ip Key", null)).thenReturn("127.0.0.1:not-a-port");
        when(validAddress.get("Ip Key", null)).thenReturn("127.0.0.1:7777");
        var location = new WindowLocation(new Rectangle(10, 20, 300, 400), true);
        when(keeper.getLastLocs(anyString(), any())).thenAnswer(invocation -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            Function<Preferences, ?> build = invocation.getArgument(1);
            return List.of(new PrefData<>(location, build.apply(missingAddress)),
                    new PrefData<>(location, build.apply(blankAddress)),
                    new PrefData<>(location, build.apply(malformedAddress)),
                    new PrefData<>(location, build.apply(validAddress)));
        });

        var restoration = new AtomicReference<PromiseFuture<Integer>>();
        SwingUtilities.invokeAndWait(() -> restoration.set(ClientWindow.initWindowLocations(context)));
        assertEquals(1, restoration.get().get(5, TimeUnit.SECONDS));

        verify(provider).openWindow(address, Optional.empty(), Optional.empty(), Optional.empty());
        verify(restoredWindow).setBounds(location.bounds());
    }

    @Test void restorationPreservesSavedIdentityAndCountsOnlySuccessfulOpenings() throws Exception {
        var saved = mock(Preferences.class);
        SwingUtilities.invokeAndWait(() -> window.savePreferences(saved));
        var encoded = ArgumentCaptor.forClass(byte[].class);
        verify(saved).putByteArray(eq("Server Public Key"), encoded.capture());
        assertArrayEquals(key.getEncoded(), encoded.getValue());
        verify(saved).put("Ip Key", address.toString());
        when(saved.get("Ip Key", null)).thenReturn(address.toString());
        when(saved.get("Server Public Key", null)).thenReturn(
                java.util.Base64.getEncoder().encodeToString(encoded.getValue()));

        var legacy = mock(Preferences.class);
        when(legacy.get("Ip Key", null)).thenReturn(address.toString());
        var corrupt = mock(Preferences.class);
        when(corrupt.get("Ip Key", null)).thenReturn(address.toString());
        when(corrupt.get("Server Public Key", null)).thenReturn("invalid base64!");

        var keeper = mock(WindowPrefDataKeeper.class);
        var provider = mock(ClientWindowProvider.class);
        var restoredWindow = mock(ClientWindow.class);
        var context = mock(MysterFrameContext.class);
        when(context.keeper()).thenReturn(keeper);
        when(context.clientWindowProvider()).thenReturn(provider);
        when(provider.openWindow(any(), any(), any(), any())).thenAnswer(invocation -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            Optional<PublicKeyIdentity> identity = invocation.getArgument(1);
            return identity.isPresent() ? PromiseFuture.newPromiseFuture(restoredWindow)
                    : PromiseFuture.newPromiseFutureException(new IOException("Offline legacy server"));
        });
        var location = new WindowLocation(new Rectangle(10, 20, 300, 400), true);
        when(keeper.getLastLocs(anyString(), any())).thenAnswer(invocation -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            Function<Preferences, ?> build = invocation.getArgument(1);
            return List.of(new PrefData<>(location, build.apply(saved)),
                    new PrefData<>(location, build.apply(legacy)),
                    new PrefData<>(location, build.apply(corrupt)));
        });
        var restoration = new AtomicReference<PromiseFuture<Integer>>();
        SwingUtilities.invokeAndWait(() -> restoration.set(ClientWindow.initWindowLocations(context)));
        assertEquals(1, restoration.get().get(5, TimeUnit.SECONDS));
        verify(provider).openWindow(address, Optional.of(new PublicKeyIdentity(key)),
                Optional.empty(), Optional.empty());
        verify(provider).openWindow(address, Optional.empty(), Optional.empty(), Optional.empty());
        verify(provider, times(2)).openWindow(any(), any(), any(), any());
        verify(restoredWindow).setBounds(location.bounds());
    }

    @Test void initialDataSelectsTypeThenExpandsSelectsAndScrollsToFile() throws Exception {
        SwingUtilities.invokeAndWait(() -> window.setInitialData(destination(Optional.of("folder/target"))));
        SwingUtilities.invokeAndWait(() -> assertEquals(-1, types.getSelectedIndex()));
        deliverTypes();
        await(() -> "folder/target".equals(window.getCurrentFile()));
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(type, window.getCurrentType());
            assertTrue(files.getSelectedRow() >= 0);
            var model = (TreeMCListTableModel<String>) files.getModel();
            var target = model.findElement(item -> "folder/target".equals(item.getObject())).orElseThrow();
            assertTrue(target.isSelected());
            assertTrue(model.indexOf(target) >= 0);
        });
        assertTrue(initialData().isEmpty());
        var udp = ArgumentCaptor.forClass(ParamBuilder.class);
        verify(datagram).getTypes(udp.capture());
        assertEquals(Optional.of(key), udp.getValue().getExpectedServerPublicKey());
        var tcp = ArgumentCaptor.forClass(ParamBuilder.class);
        verify(stream).makeStreamConnection(tcp.capture());
        assertEquals(Optional.of(key), tcp.getValue().getExpectedServerPublicKey());
        verify(stream, never()).downloadFile(any());
    }

    @Test void reusedWindowSelectsAnotherCachedFileWithoutReloading() throws Exception {
        SwingUtilities.invokeAndWait(() -> window.setInitialData(destination(Optional.of("folder/target"))));
        deliverTypes();
        await(() -> "folder/target".equals(window.getCurrentFile()));
        SwingUtilities.invokeAndWait(() -> {
            window.setInitialData(destination(Optional.of("unrelated")));
            assertEquals("unrelated", window.getCurrentFile());
        });
        assertTrue(initialData().isEmpty());
        verify(stream, times(1)).makeStreamConnection(any(ParamBuilder.class));
    }

    @Test void alreadySelectedTypeKeepsFileDestinationUntilItsListingFinishes() throws Exception {
        var release = new CountDownLatch(1);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenAnswer(_ -> {
            if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test timed out");
            return listing();
        });
        try {
            SwingUtilities.invokeAndWait(() -> window.setInitialData(destination(Optional.empty())));
            deliverTypes();
            await(() -> type.equals(window.getCurrentType()));
            SwingUtilities.invokeAndWait(() -> window.setInitialData(destination(Optional.of("folder/target"))));
            assertTrue(initialData().isPresent());
            release.countDown();
            await(() -> "folder/target".equals(window.getCurrentFile()));
            assertTrue(initialData().isEmpty());
        } finally { release.countDown(); }
    }

    @Test void selectingAnotherTypeCancelsThePendingFileDestination() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            window.setInitialData(destination(Optional.of("missing")));
            window.addItemToTypeList(otherType);
            types.select(0);
        });
        assertTrue(initialData().isEmpty());
        await(() -> files.length() > 0);
        SwingUtilities.invokeAndWait(() -> assertEquals("", window.getCurrentFile()));
    }

    @Test void manualRefreshCancelsPendingInitialDataAndRetainsTheServerIdentity() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            window.setInitialData(destination(Optional.of("folder/target")));
            connect.doClick();
        });
        assertTrue(initialData().isEmpty());
        assertEquals(new PublicKeyIdentity(key), window.getServerIdentity());
        deliverTypes();
        await(() -> types.length() == 2);
        SwingUtilities.invokeAndWait(() -> assertEquals(-1, types.getSelectedIndex()));
    }

    @Test void navigationCannotReplaceTheServerIdentity() throws Exception {
        PublicKey otherKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        SwingUtilities.invokeAndWait(() -> {
            window.setInitialData(destination(Optional.of("folder/target")));
            assertThrows(IllegalArgumentException.class, () -> window.setInitialData(new ClientWindowData(
                    destination(Optional.empty()).ip(), new PublicKeyIdentity(otherKey), Optional.of(type), Optional.empty())));
            assertEquals(new PublicKeyIdentity(key), window.getServerIdentity());
        });
        assertEquals(Optional.of(destination(Optional.of("folder/target"))), initialData());
        verify(datagram, timeout(5000).times(1)).getTypes(any());
        deliverTypes();
        await(() -> "folder/target".equals(window.getCurrentFile()));
    }

    @Test void refreshUsesTheCurrentEndpointRatherThanTheDisplayField() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                var field = ClientWindow.class.getDeclaredField("serverAddressField");
                field.setAccessible(true);
                ((JTextField) field.get(window)).setText("192.0.2.1:7777");
            } catch (Exception error) { throw new AssertionError(error); }
            connect.doClick();
            assertEquals(address, window.getCurrentIP());
        });
        var params = ArgumentCaptor.forClass(ParamBuilder.class);
        verify(datagram, timeout(5000)).getTypes(params.capture());
        assertEquals("127.0.0.1:7777", params.getValue().getAddress().orElseThrow().toString());
        assertEquals(Optional.of(key), params.getValue().getExpectedServerPublicKey());
        pendingTypes.get().setResult(new MysterType[0]);
    }

    @Test void missingFileClearsTheDestinationAtListingCompletion() throws Exception {
        SwingUtilities.invokeAndWait(() -> window.setInitialData(destination(Optional.of("missing"))));
        deliverTypes();
        await(() -> files.length() > 0);
        await(() -> {
            try { return initialData().isEmpty(); }
            catch (Exception error) { throw new AssertionError(error); }
        });
        SwingUtilities.invokeAndWait(() -> assertEquals("", window.getCurrentFile()));
    }
    @Test void selectingAnotherLoadedTypeCompletesATypeOnlyDestination() throws Exception {
        SwingUtilities.invokeAndWait(() -> window.setInitialData(destination(Optional.of("folder/target"))));
        deliverTypes();
        await(() -> "folder/target".equals(window.getCurrentFile()));
        SwingUtilities.invokeAndWait(() -> {
            window.setInitialData(new ClientWindowData(destination(Optional.empty()).ip(),
                                                       new PublicKeyIdentity(key), Optional.of(otherType), Optional.empty()));
            assertEquals(otherType, window.getCurrentType());
        });
        assertTrue(initialData().isEmpty());
        await(() -> files.length() > 0);
        verify(stream, times(2)).makeStreamConnection(any(ParamBuilder.class));
    }

    @Test void serverOnlyNavigationCompletesEvenWhenTheTypeListIsEmpty() throws Exception {
        SwingUtilities.invokeAndWait(() -> window.setInitialData(new ClientWindowData(
                destination(Optional.empty()).ip(), new PublicKeyIdentity(key), Optional.empty(), Optional.empty())));
        pendingTypes.get().setResult(new MysterType[0]);
        await(() -> {
            try { return initialData().isEmpty(); }
            catch (Exception error) { throw new AssertionError(error); }
        });
        verifyNoInteractions(stream);
    }

    @Test void absentTypeCompletesWithoutSelectingAnUnrelatedType() throws Exception {
        SwingUtilities.invokeAndWait(() -> window.setInitialData(destination(Optional.of("folder/target"))));
        pendingTypes.get().setResult(new MysterType[]{otherType});
        await(() -> types.length() == 1);
        await(() -> {
            try { return initialData().isEmpty(); }
            catch (Exception error) { throw new AssertionError(error); }
        });
        SwingUtilities.invokeAndWait(() -> assertEquals(-1, types.getSelectedIndex()));
        verifyNoInteractions(stream);
    }

    @Test void aNewDestinationSupersedesThePendingFilename() throws Exception {
        var release = new CountDownLatch(1);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenAnswer(_ -> {
            if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test timed out");
            return listing();
        });
        try {
            SwingUtilities.invokeAndWait(() -> window.setInitialData(destination(Optional.of("folder/target"))));
            deliverTypes();
            await(() -> type.equals(window.getCurrentType()));
            SwingUtilities.invokeAndWait(() -> window.setInitialData(destination(Optional.of("unrelated"))));
            release.countDown();
            await(() -> "unrelated".equals(window.getCurrentFile()));
            assertTrue(initialData().isEmpty());
        } finally { release.countDown(); }
    }

    @Test void sameIdentityCanMoveToAnotherEndpointAndReloadsItsRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> window.setInitialData(destination(Optional.of("folder/target"))));
        deliverTypes();
        await(() -> "folder/target".equals(window.getCurrentFile()));
        var refreshed = new AtomicReference<AsyncContext<MysterType[]>>();
        when(datagram.getTypes(any())).thenReturn(PromiseFuture.newPromiseFuture(refreshed::set));
        MysterAddress nextAddress = MysterAddress.createMysterAddress("192.0.2.1:7777");
        SwingUtilities.invokeAndWait(() -> {
            window.setInitialData(new ClientWindowData(nextAddress, new PublicKeyIdentity(key),
                    Optional.of(type), Optional.of("unrelated")));
            assertEquals(0, types.length());
            assertEquals(0, files.length());
            assertEquals(nextAddress, window.getCurrentIP());
        });
        await(() -> refreshed.get() != null);
        refreshed.get().setResult(new MysterType[]{type});
        await(() -> "unrelated".equals(window.getCurrentFile()));
        assertEquals(new PublicKeyIdentity(key), window.getServerIdentity());
        var params = ArgumentCaptor.forClass(ParamBuilder.class);
        verify(stream, times(2)).makeStreamConnection(params.capture());
        assertEquals(Optional.of(key), params.getAllValues().getFirst().getExpectedServerPublicKey());
        assertEquals(Optional.of(key), params.getAllValues().getLast().getExpectedServerPublicKey());
        assertEquals(Optional.of(nextAddress), params.getAllValues().getLast().getAddress());
    }

}
