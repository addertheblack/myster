package com.myster.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TestClientFilePreviewPane {
    @ParameterizedTest
    @CsvSource({"100, 50, 95", "50, 100, 190", "100, 100, 190"})
    void layoutHonorsInsetsAndOnlyReservesFittedImageHeight(int imageWidth,
                                                          int imageHeight,
                                                          int previewHeight) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ClientFilePreviewPane pane = spy(new ClientFilePreviewPane(new JTextArea()));
            doReturn(true).when(pane).isShowing();
            pane.setSize(200, 300);
            AtomicInteger geometryChanges = new AtomicInteger();
            pane.setGeometryListener(geometryChanges::incrementAndGet);
            pane.doLayout();
            assertEquals(new Rectangle(5, 5, 190, 290), pane.getComponent(1).getBounds());

            pane.setThumbnail(new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB));
            pane.doLayout();
            assertEquals(new Rectangle(5, 5, 190, previewHeight), pane.getComponent(0).getBounds());
            assertEquals(new Rectangle(5, 5 + previewHeight, 190, 290 - previewHeight),
                    pane.getComponent(1).getBounds());
            assertEquals(190, pane.getThumbnailLogicalSide(), "image shape must not change request size");

            pane.clearThumbnail();
            pane.doLayout();
            assertEquals(new Rectangle(5, 5, 190, 290), pane.getComponent(1).getBounds());
            assertEquals(1, geometryChanges.get(), "image publication must not restart acquisition");
        });
    }
}
