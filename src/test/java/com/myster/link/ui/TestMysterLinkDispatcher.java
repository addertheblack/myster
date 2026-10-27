package com.myster.link.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.link.MysterLinkService;
import com.myster.link.MysterLinkUri;
import com.myster.type.MysterType;

class TestMysterLinkDispatcher {
    private MysterLinkUri uri(int seed, Optional<MysterType> type, Optional<String> name) throws IOException {
        byte[] bytes = new byte[16]; bytes[0] = (byte) seed;
        return MysterLinkUri.create(new ServerCid(bytes), type, name);
    }
    private void flush() throws Exception { for (int i=0;i<4;i++) SwingUtilities.invokeAndWait(() -> {}); }

    @Test void ownServerCancelAtEveryDepthPerformsNoLookupOrNavigation() throws Exception {
        for (int depth=0;depth<3;depth++) {
            var service = mock(MysterLinkService.class);
            var ui = mock(MysterLinkDispatcher.Interaction.class);
            when(ui.alive()).thenReturn(true);
            when(service.isLocal(any())).thenReturn(true);
            var link = uri(0, depth==0?Optional.empty():Optional.of(new MysterType(new byte[16])),
                    depth==2?Optional.of("file"):Optional.empty());
            new MysterLinkDispatcher(service, ui).dispatch(link);
            flush();
            var order = inOrder(service, ui);
            order.verify(service).isLocal(link);
            order.verify(ui).confirm(link.kind());
            verify(service, never()).prepare(any());
            verify(ui, never()).navigate(any());
        }
    }

    @Test void confirmationPrecedesLocalResolutionAndAllFormsNavigate() throws Exception {
        var service = mock(MysterLinkService.class);
        var ui = mock(MysterLinkDispatcher.Interaction.class);
        when(ui.alive()).thenReturn(true);
        when(ui.confirm(any())).thenReturn(true);
        when(service.isLocal(any())).thenReturn(true);
        var target = mock(MysterLinkService.Target.class);
        when(service.prepare(any())).thenReturn(PromiseFuture.newPromiseFuture(target));
        var dispatcher = new MysterLinkDispatcher(service, ui);
        var link = uri(0, Optional.of(new MysterType(new byte[16])), Optional.of("file"));
        dispatcher.dispatch(link);
        flush();
        var order = inOrder(ui, service);
        order.verify(ui).confirm(link.kind());
        order.verify(service).prepare(link);
        order.verify(ui).navigate(eq(target));
        verify(ui, never()).error(any());
    }

    @Test void duplicatesAndWaitingLinksAreBoundedAndReopenWorksAfterCompletion() throws Exception {
        var service = mock(MysterLinkService.class);
        var ui = mock(MysterLinkDispatcher.Interaction.class);
        when(ui.alive()).thenReturn(true);
        var target = mock(MysterLinkService.Target.class);
        var pending = new java.util.ArrayList<com.general.thread.AsyncContext<MysterLinkService.Target>>();
        when(service.prepare(any())).thenAnswer(_ -> PromiseFuture.<MysterLinkService.Target>newPromiseFuture(pending::add));
        var dispatcher = new MysterLinkDispatcher(service, ui);
        var first = uri(0, Optional.empty(), Optional.empty());
        SwingUtilities.invokeAndWait(() -> {
            dispatcher.dispatch(first);
            dispatcher.dispatch(first);
            for(int i=1;i<20;i++) {
                try { dispatcher.dispatch(uri(i, Optional.empty(), Optional.empty())); }
                catch(IOException error) { throw new AssertionError(error); }
            }
        });
        flush();
        verify(service, times(1)).prepare(any());
        for(int i=0;i<9;i++) {
            int index = i;
            SwingUtilities.invokeAndWait(() -> pending.get(index).setResult(target));
            flush();
        }
        verify(service, times(9)).prepare(any());
        dispatcher.dispatch(first);
        flush();
        verify(service, times(10)).prepare(any());
    }

    @Test void disposalDuringPreparationSuppressesLateNavigationAndErrors() throws Exception {
        var service = mock(MysterLinkService.class);
        var ui = mock(MysterLinkDispatcher.Interaction.class);
        when(ui.alive()).thenReturn(true);
        var pending = new java.util.concurrent.atomic.AtomicReference<com.general.thread.AsyncContext<MysterLinkService.Target>>();
        var future = PromiseFuture.<MysterLinkService.Target>newPromiseFuture(pending::set);
        when(service.prepare(any())).thenReturn(future);
        var dispatcher = new MysterLinkDispatcher(service, ui);
        dispatcher.dispatch(uri(0,Optional.empty(),Optional.empty()));
        flush();
        when(ui.alive()).thenReturn(false);
        pending.get().setResult(mock(MysterLinkService.Target.class));
        flush();
        verify(ui,never()).navigate(any());
        verify(ui,never()).error(any());
    }
}
