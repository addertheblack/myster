package com.myster.link.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.link.MysterLinkUri;

class TestFileLinkCopyAction {
    private void flush() throws Exception { for(int i=0;i<3;i++) SwingUtilities.invokeAndWait(() -> {}); }

    @Test void selectingDoesNotCopyAndLateSelectionChangesPreserveClipboard() throws Exception {
        var selection = new AtomicReference<>(Optional.of("first"));
        var pending = new AtomicReference<com.general.thread.AsyncContext<MysterLinkUri>>();
        var future = PromiseFuture.<MysterLinkUri>newPromiseFuture(pending::set);
        List<String> clipboard = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> requests = new ArrayList<>();
        var menu = FileLinkCopyActionUtils.item(() -> null, selection::get, file -> {
            assertEquals("first",file);
            requests.add(file);
            return future;
        }, clipboard::add, errors::add);
        assertTrue(requests.isEmpty());
        SwingUtilities.invokeAndWait(() -> menu.doClick());
        selection.set(Optional.of("second"));
        pending.get().setResult(MysterLinkUri.create(new ServerCid(new byte[16]),Optional.empty(),Optional.empty()));
        flush();
        assertEquals(List.of("first"),requests);
        assertTrue(clipboard.isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test void copyRequiresARealSelectionAndFailurePreservesClipboard() throws Exception {
        var selection = new AtomicReference<>(Optional.<String>empty());
        List<String> clipboard = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        var menu = FileLinkCopyActionUtils.item(() -> null, selection::get,
                _ -> PromiseFuture.newPromiseFutureException(new IOException("server unavailable")),
                                                clipboard::add, errors::add);
        SwingUtilities.invokeAndWait(() -> menu.doClick());
        flush();
        assertTrue(errors.isEmpty());
        selection.set(Optional.of("file"));
        SwingUtilities.invokeAndWait(() -> menu.doClick());
        flush();
        assertEquals(1,errors.size());
        assertTrue(clipboard.isEmpty());
    }
}
