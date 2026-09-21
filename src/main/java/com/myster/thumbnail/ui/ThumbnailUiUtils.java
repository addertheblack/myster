package com.myster.thumbnail.ui;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;

import com.myster.net.stream.ThumbnailProtocolUtils;
import com.myster.type.MetadataTypeId;

/** Shared eligibility, device-size, and aspect-fit calculations for remote thumbnails. */
public final class ThumbnailUiUtils {
    private ThumbnailUiUtils() {}

    public static boolean isEligible(MetadataTypeId metadataTypeId) {
        return MetadataTypeId.IMAGE.equals(metadataTypeId)
                || MetadataTypeId.VIDEO.equals(metadataTypeId);
    }

    /**
     * Converts a logical square side to a bounded device-pixel request.
     *
     * @return zero when the logical side is not positive or no graphics configuration exists
     */
    public static int devicePixelSize(double logicalSide, GraphicsConfiguration configuration) {
        if (logicalSide <= 0 || configuration == null) {
            return 0;
        }
        AffineTransform transform = configuration.getDefaultTransform();
        return devicePixelSize(logicalSide, transform);
    }

    static int devicePixelSize(double logicalSide, AffineTransform transform) {
        if (logicalSide <= 0 || transform == null) {
            return 0;
        }
        Point2D x = transform.deltaTransform(new Point2D.Double(1, 0), null);
        Point2D y = transform.deltaTransform(new Point2D.Double(0, 1), null);
        double scaleX = Math.hypot(x.getX(), x.getY());
        double scaleY = Math.hypot(y.getX(), y.getY());
        int pixels = (int) Math.ceil(logicalSide * Math.max(scaleX, scaleY));
        return Math.min(ThumbnailProtocolUtils.MAX_SIZE, Math.max(1, pixels));
    }

    /** Returns the centered aspect-preserving destination rectangle. */
    public static Rectangle aspectFit(BufferedImage image, Rectangle square) {
        if (image == null || square.width <= 0 || square.height <= 0
                || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return new Rectangle();
        }
        double scale = Math.min((double) square.width / image.getWidth(),
                                (double) square.height / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return new Rectangle(square.x + (square.width - width) / 2,
                             square.y + (square.height - height) / 2,
                             width,
                             height);
    }

    /** Returns the centered aspect-MISS_TTLpreserving destination rectangle for a Swing icon. */
    public static Rectangle aspectFit(Icon icon, Rectangle square) {
        if (icon == null || square.width <= 0 || square.height <= 0
                || icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
            return new Rectangle();
        }
        double scale = Math.min((double) square.width / icon.getIconWidth(),
                                (double) square.height / icon.getIconHeight());
        int width = Math.max(1, (int) Math.round(icon.getIconWidth() * scale));
        int height = Math.max(1, (int) Math.round(icon.getIconHeight() * scale));
        return new Rectangle(square.x + (square.width - width) / 2,
                             square.y + (square.height - height) / 2,
                             width,
                             height);
    }
}
