package com.myster.thumbnail.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Objects;

import javax.swing.Icon;

/** Passive logical-size icon that aspect-fits completed thumbnail pixels when painted. */
public final class ThumbnailIcon implements Icon {
    private final BufferedImage image;
    private final int logicalSize;

    public ThumbnailIcon(BufferedImage image, int logicalSize) {
        this.image = Objects.requireNonNull(image);
        if (logicalSize < 1) {
            throw new IllegalArgumentException("Logical icon size must be positive");
        }
        this.logicalSize = logicalSize;
    }

    @Override
    public int getIconWidth() {
        return logicalSize;
    }

    @Override
    public int getIconHeight() {
        return logicalSize;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Rectangle target = ThumbnailUiUtils.aspectFit(image,
                new Rectangle(x, y, logicalSize, logicalSize));
        if (!target.isEmpty()) {
            graphics.drawImage(image, target.x, target.y, target.width, target.height, component);
        }
    }
}
