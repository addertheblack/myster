package com.myster.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class TestClientFilePreviewPane {
    @Test
    void layoutHonorsInsetsAndOnlyReservesSpaceForAnAvailableImage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ClientFilePreviewPane pane = spy(new ClientFilePreviewPane(new JTextArea()));
            doReturn(true).when(pane).isShowing();
            pane.setSize(200, 300);
            AtomicInteger geometryChanges = new AtomicInteger();
            pane.setGeometryListener(geometryChanges::incrementAndGet);
            pane.doLayout();
            assertEquals(new Rectangle(5, 5, 190, 290), pane.getComponent(1).getBounds());

            pane.setThumbnail(new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB));
            pane.doLayout();
            assertEquals(new Rectangle(5, 5, 190, 190), pane.getComponent(0).getBounds());
            assertEquals(new Rectangle(5, 195, 190, 100), pane.getComponent(1).getBounds());

            pane.clearThumbnail();
            pane.doLayout();
            assertEquals(new Rectangle(5, 5, 190, 290), pane.getComponent(1).getBounds());
            assertEquals(1, geometryChanges.get(), "image publication must not restart acquisition");
        });
    }
}
