package com.general.mclist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class TestTreeMCListIcons {
    @Test
    void compositionUsesCurrentComponentAndScaleWithoutLeakingGraphicsState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel component = new JPanel();
            Icon first = mock(Icon.class);
            Icon second = mock(Icon.class);
            when(first.getIconWidth()).thenReturn(8);
            when(first.getIconHeight()).thenReturn(8);
            when(second.getIconWidth()).thenReturn(12);
            when(second.getIconHeight()).thenReturn(16);

            BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                doAnswer(call -> {
                    assertSame(component, call.getArgument(0));
                    Graphics2D child = call.getArgument(1);
                    assertEquals(graphics.getTransform(), child.getTransform());
                    assertEquals(graphics.getClipBounds(), child.getClipBounds());
                    child.translate(100, 100);
                    child.setClip(0, 0, 1, 1);
                    return null;
                }).when(first).paintIcon(any(), any(), anyInt(), anyInt());
                doAnswer(call -> {
                    assertSame(component, call.getArgument(0));
                    Graphics2D child = call.getArgument(1);
                    assertEquals(graphics.getTransform(), child.getTransform());
                    assertEquals(graphics.getClipBounds(), child.getClipBounds());
                    child.scale(5, 5);
                    child.setClip(0, 0, 2, 2);
                    return null;
                }).when(second).paintIcon(any(), any(), anyInt(), anyInt());

                Icon combined = TreeMCList.mergeIcons(first, second, 3);
                assertEquals(23, combined.getIconWidth());
                assertEquals(16, combined.getIconHeight());
                verify(first, never()).paintIcon(any(), any(), anyInt(), anyInt());
                verify(second, never()).paintIcon(any(), any(), anyInt(), anyInt());

                double[] scales = { 1, 1.25, 1.5, 1.75, 2, 2.5, 3 };
                for (double scale : scales) {
                    graphics.setTransform(AffineTransform.getScaleInstance(scale, scale));
                    graphics.translate(3, 5);
                    graphics.setClip(0, 0, 100, 100);
                    AffineTransform transform = graphics.getTransform();
                    var clip = graphics.getClipBounds();

                    combined.paintIcon(component, graphics, 4, 8);

                    assertEquals(transform, graphics.getTransform());
                    assertEquals(clip, graphics.getClipBounds());
                    assertEquals(23, combined.getIconWidth());
                    assertEquals(16, combined.getIconHeight());
                }
                verify(first, times(scales.length)).paintIcon(eq(component), any(Graphics.class), eq(4), eq(12));
                verify(second, times(scales.length)).paintIcon(eq(component), any(Graphics.class), eq(15), eq(8));
            } finally {
                graphics.dispose();
            }
        });
    }

    @Test
    void absentIconsPassThroughWithoutAddingSpacing() {
        Icon icon = mock(Icon.class);

        assertSame(icon, TreeMCList.mergeIcons(icon, null, 3));
        assertSame(icon, TreeMCList.mergeIcons(null, icon, 3));
        assertNull(TreeMCList.mergeIcons(null, null, 3));
    }
}
