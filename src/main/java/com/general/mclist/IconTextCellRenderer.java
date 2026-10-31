package com.general.mclist;

import java.awt.Component;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Reusable icon/text cell renderer. Both callbacks are passive: empty icon output keeps the cell
 * text-only. Geometry uses Swing's own label layout so viewport observers see the actual icon
 * slot, including insets, clipping and component orientation. No model values are modified.
 */
public final class IconTextCellRenderer<E> extends DefaultTableCellRenderer {
    private final BiFunction<JTable, Integer, E> itemAtViewRow;
    private final BiFunction<E, Integer, Optional<Icon>> icons;

    public IconTextCellRenderer(BiFunction<JTable, Integer, E> itemAtViewRow,
                               BiFunction<E, Integer, Optional<Icon>> icons) {
        this.itemAtViewRow = itemAtViewRow;
        this.icons = icons;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
            boolean focus, int row, int column) {
        super.getTableCellRendererComponent(table, value, selected, focus, row, column);
        setComponentOrientation(table.getComponentOrientation());
        setHorizontalAlignment(SwingConstants.LEADING);
        setHorizontalTextPosition(SwingConstants.TRAILING);
        setVerticalAlignment(SwingConstants.CENTER);
        setVerticalTextPosition(SwingConstants.CENTER);
        setToolTipText(null);
        Icon icon = icons.apply(itemAtViewRow.apply(table, row), table.getRowHeight(row)).orElse(null);
        setIcon(icon);
        setIconTextGap(icon == null ? 0 : 4);
        return this;
    }

    /** Returns the clipped icon bounds in table coordinates; empty when the cell has no icon. */
    public Rectangle getIconBounds(JTable table, int viewRow, int modelColumn) {
        int column = table.convertColumnIndexToView(modelColumn);
        if (column < 0 || viewRow < 0 || viewRow >= table.getRowCount()) {
            return new Rectangle();
        }
        Rectangle cell = table.getCellRect(viewRow, column, false);
        getTableCellRendererComponent(table, table.getValueAt(viewRow, column),
                table.isCellSelected(viewRow, column), table.hasFocus()
                        && table.getSelectionModel().getLeadSelectionIndex() == viewRow, viewRow, column);
        if (getIcon() == null) {
            return new Rectangle();
        }
        Insets insets = getInsets();
        Rectangle content = new Rectangle(cell.x + insets.left, cell.y + insets.top,
                Math.max(0, cell.width - insets.left - insets.right),
                Math.max(0, cell.height - insets.top - insets.bottom));
        Rectangle icon = new Rectangle();
        SwingUtilities.layoutCompoundLabel(this, table.getFontMetrics(getFont()), getText(), getIcon(),
                getVerticalAlignment(), getHorizontalAlignment(), getVerticalTextPosition(),
                getHorizontalTextPosition(), content, icon, new Rectangle(), getIconTextGap());
        return icon.intersection(cell);
    }
}
