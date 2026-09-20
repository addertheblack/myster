package com.myster.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.general.thread.PromiseFutures;
import com.myster.net.MysterAddress;
import com.myster.thumbnail.RemoteThumbnailCache;
import com.myster.thumbnail.RemoteThumbnailCache.Request;
import com.myster.type.MysterType;

@Timeout(10)
class TestClientPreviewController {
    private final ClientFilePreviewPane pane = mock(ClientFilePreviewPane.class);
    private final RemoteThumbnailCache thumbnailCache = mock(RemoteThumbnailCache.class);
    private final MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1:6669");
    private final MysterType type = new MysterType(new byte[16]);
    private Optional<Request> selection = Optional.of(request("image", 128));
    private boolean active;
    private Runnable geometryChanged;
    private ClientPreviewController controller;

    TestClientPreviewController() throws Exception {}

    private Request request(String file, int size) {
        return new Request(address, type, file, size);
    }

    @BeforeEach
    void setup() throws Exception {
        doAnswer(call -> { geometryChanged = call.getArgument(0); return null; })
                .when(pane).setGeometryListener(any());
        SwingUtilities.invokeAndWait(() -> controller = new ClientPreviewController(
                pane, thumbnailCache, () -> selection, () -> active));
    }

    @AfterEach
    void close() throws Exception {
        SwingUtilities.invokeAndWait(controller::close);
    }

    @Test
    void hiddenStartupAndReopeningUseCurrentSelection() throws Exception {
        SwingUtilities.invokeAndWait(controller::reconcile);
        verify(thumbnailCache).replace(Optional.empty());
        verify(thumbnailCache, never()).replace(selection);
        SwingUtilities.invokeAndWait(() -> { active = true; controller.reconcile(); });
        verify(thumbnailCache).replace(selection);
    }

    @Test
    void resizingKeepsSameFilePixelsAndOnlyStartsLatestSize() throws Exception {
        CountDownLatch latestRequested = new CountDownLatch(1);
        Optional<Request> latest = Optional.of(request("image", 256));
        doAnswer(_ -> { latestRequested.countDown(); return null; }).when(thumbnailCache).replace(latest);
        SwingUtilities.invokeAndWait(() -> {
            active = true;
            controller.reconcile();
            clearInvocations(pane, thumbnailCache);
            selection = Optional.of(request("image", 200));
            geometryChanged.run();
            selection = latest;
            geometryChanged.run();
            verify(thumbnailCache, never()).replace(latest);
        });
        assertTrue(latestRequested.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {});
        verify(thumbnailCache, never()).replace(Optional.of(request("image", 200)));
        verify(thumbnailCache, times(1)).replace(latest);
        verify(pane, never()).clearThumbnail();
    }

    @Test
    void unchangedDeviceSizeDoesNotCancelTransfer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            active = true;
            controller.reconcile();
            clearInvocations(thumbnailCache);
            geometryChanged.run();
        });
        verifyNoInteractions(thumbnailCache);
    }

    @Test
    void hidingCancelsDelayAndWithdrawsImmediately() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            active = true;
            controller.reconcile();
            selection = Optional.of(request("image", 256));
            geometryChanged.run();
            clearInvocations(thumbnailCache, pane);
            active = false;
            controller.reconcile();
            verify(thumbnailCache).replace(Optional.empty());
            verify(pane).clearThumbnail();
        });
        PromiseFutures.delay(Duration.ofMillis(350)).get(5, TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> {});
        verify(thumbnailCache, never()).replace(selection);
    }

    @Test
    void newSelectionClearsPixelsImmediatelyAndCancelsOldResize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            active = true;
            controller.reconcile();
            selection = Optional.of(request("image", 256));
            geometryChanged.run();
            clearInvocations(thumbnailCache, pane);
            selection = Optional.of(request("other", 128));
            controller.reconcile();
            verify(pane).clearThumbnail();
            verify(thumbnailCache).replace(selection);
        });
        PromiseFutures.delay(Duration.ofMillis(350)).get(5, TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> {});
        verify(thumbnailCache, times(1)).replace(selection);
    }

    @Test
    void closingCancelsDelayAndDetachesGeometryListener() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            active = true;
            geometryChanged.run();
            controller.close();
            clearInvocations(thumbnailCache);
            geometryChanged.run();
            controller.reconcile();
        });
        PromiseFutures.delay(Duration.ofMillis(350)).get(5, TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> {});
        verifyNoInteractions(thumbnailCache);
    }
}
