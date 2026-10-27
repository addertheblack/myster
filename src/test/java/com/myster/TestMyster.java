package com.myster;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.myster.link.MysterUriRouter;
import com.myster.ui.MysterFrameContext;

class TestMyster {
    @Test void coldStartAndRelaunchArgumentsKeepBoundsAndDispatchOnEdt() throws Exception {
        String link="myster://link/v1?server="+"0".repeat(32);
        List<String> received=new ArrayList<>();
        var router=new MysterUriRouter(received::add, uri -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            received.add(uri.toString());
        },error -> fail(error));
        var handler=new Myster.SingleInstanceLaunchHandler(new String[]{link});
        String[] arguments=new String[40]; Arrays.fill(arguments,link);
        for(int i=0;i<20;i++) handler.requestLaunch(arguments);
        assertTrue(received.isEmpty());
        SwingUtilities.invokeAndWait(() -> handler.setReady(mock(MysterFrameContext.class),router));
        assertEquals(1+7*32,received.size());
        handler.requestLaunch(new String[]{"myster://join-type/v1?invitation=owned-by-original-parser"});
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(1+7*32+1,received.size());
    }
}
