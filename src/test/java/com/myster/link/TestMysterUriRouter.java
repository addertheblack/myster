package com.myster.link;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TestMysterUriRouter {
    @Test void invitationAndResourcesStaySeparateAndBadBatchesAreCoalesced() throws Exception {
        List<String> invitations = new ArrayList<>();
        List<MysterLinkUri> resources = new ArrayList<>();
        List<IOException> errors = new ArrayList<>();
        var router = new MysterUriRouter(invitations::add, resources::add, errors::add);
        SwingUtilities.invokeAndWait(() -> {
            router.dispatch("myster://join-type/v1?unchanged=invitation-parser-owned");
            router.dispatch("myster://link/v1?server=" + "0".repeat(32));
            for (int i = 0; i < 256; i++) router.dispatch("myster://file/v1?server=bad");
        });
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(1, invitations.size());
        assertEquals(1, resources.size());
        assertEquals(MysterLinkUri.TargetKind.SERVER, resources.getFirst().kind());
        assertEquals(1, errors.size());
    }

    @Test void modalReentrantErrorsHaveOnlyOneAggregatePendingAlert() throws Exception {
        List<IOException> errors = new ArrayList<>();
        MysterUriRouter[] reference = new MysterUriRouter[1];
        reference[0] = new MysterUriRouter(_ -> fail(), _ -> fail(), error -> {
            errors.add(error);
            if (errors.size() == 1) {
                for (int i = 0; i < 100; i++) reference[0].dispatch("myster://bad/v1");
            }
        });
        reference[0].dispatch("myster://bad/v1");
        for (int i = 0; i < 4; i++) SwingUtilities.invokeAndWait(() -> {});
        assertEquals(2, errors.size());
    }
}
