package com.myster.thumbnail;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class ThumbnailImageUtils {
    private ThumbnailImageUtils() {}

    static BufferedImage fit(BufferedImage image, int size) {
        if (image.getWidth() <= size && image.getHeight() <= size) {
            return image;
        }
        double scale = Math.min((double) size / image.getWidth(),
                                (double) size / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        // Successive halving avoids aliasing when a desktop returns a much larger image.
        BufferedImage current = image;
        do {
            int nextWidth = Math.max(width, current.getWidth() / 2);
            int nextHeight = Math.max(height, current.getHeight() / 2);
            BufferedImage next = new BufferedImage(nextWidth, nextHeight,
                                                  BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = next.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                          RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(current, 0, 0, nextWidth, nextHeight, null);
            } finally {
                graphics.dispose();
            }
            current = next;
        } while (current.getWidth() != width || current.getHeight() != height);
        return current;
    }
}
