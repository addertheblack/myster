package com.general.mclist;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.ComponentOrientation;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

class TestIconTextCellRenderer {
    @Test
    void resetsBetweenIconAndTextOnlyCellsWithoutChangingValues() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(new Object[][] {{"photo"}, {"audio"}}, new String[] {"Name"});
            var icon = new ImageIcon(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB));
            var renderer = new IconTextCellRenderer<String>((t, row) -> (String) t.getValueAt(row, 0),
                    (name, _) -> name.equals("photo") ? Optional.of(icon) : Optional.empty());
            JLabel photo = (JLabel) renderer.getTableCellRendererComponent(table, "photo", true, true, 0, 0);
            assertSame(icon, photo.getIcon());
            assertEquals(table.getSelectionBackground(), photo.getBackground());
            JLabel audio = (JLabel) renderer.getTableCellRendererComponent(table, "audio", false, false, 1, 0);
            assertNull(audio.getIcon());
            assertEquals(0, audio.getIconTextGap());
            assertEquals("audio", audio.getText());
            assertNull(audio.getToolTipText());
            assertEquals("photo", table.getValueAt(0, 0));
        });
    }

    @Test
    void geometryTracksOrientationAndClipsNarrowColumns() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(new DefaultTableModel(new Object[][] {{"photo"}}, new String[] {"Name"}));
            table.setRowHeight(20);
            table.setSize(200, 100);
            var icon = new ImageIcon(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB));
            var renderer = new IconTextCellRenderer<>((t, row) -> row, (_, _) -> Optional.of(icon));
            Rectangle left = renderer.getIconBounds(table, 0, 0);
            assertTrue(left.width > 0);
            table.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            Rectangle right = renderer.getIconBounds(table, 0, 0);
            assertTrue(right.x > left.x);
            table.getColumnModel().getColumn(0).setMinWidth(0);
            table.getColumnModel().getColumn(0).setWidth(8);
            Rectangle clipped = renderer.getIconBounds(table, 0, 0);
            assertTrue(clipped.width <= 8);
            assertTrue(renderer.getIconBounds(table, -1, 0).isEmpty());
        });
    }
}
