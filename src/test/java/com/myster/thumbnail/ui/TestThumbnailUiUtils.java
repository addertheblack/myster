package com.myster.thumbnail.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import com.myster.type.MetadataTypeId;
import org.junit.jupiter.api.Test;

class TestThumbnailUiUtils {
    @Test
    void eligibilityOnlyAllowsImageAndVideo() {
        assertTrue(ThumbnailUiUtils.isEligible(MetadataTypeId.IMAGE));
        assertTrue(ThumbnailUiUtils.isEligible(MetadataTypeId.VIDEO));
        assertTrue(!ThumbnailUiUtils.isEligible(MetadataTypeId.AUDIO));
        assertTrue(!ThumbnailUiUtils.isEligible(MetadataTypeId.GENERIC));
    }

    @Test
    void devicePixelSizeRoundsAndCapsAfterScaling() {
        assertEquals(100, ThumbnailUiUtils.devicePixelSize(100, AffineTransform.getScaleInstance(1, 1)));
        assertEquals(125, ThumbnailUiUtils.devicePixelSize(100, AffineTransform.getScaleInstance(1.25, 1.25)));
        assertEquals(150, ThumbnailUiUtils.devicePixelSize(100, AffineTransform.getScaleInstance(1.5, 1.5)));
        assertEquals(200, ThumbnailUiUtils.devicePixelSize(100, AffineTransform.getScaleInstance(2, 2)));
        assertEquals(22, ThumbnailUiUtils.devicePixelSize(17, AffineTransform.getScaleInstance(1.25, 1.25)));
        assertEquals(30, ThumbnailUiUtils.devicePixelSize(17, AffineTransform.getScaleInstance(1.75, 1.75)));
        assertEquals(43, ThumbnailUiUtils.devicePixelSize(17, AffineTransform.getScaleInstance(2.5, 2.5)));
        assertEquals(60, ThumbnailUiUtils.devicePixelSize(20, AffineTransform.getScaleInstance(3, 3)));
        assertEquals(256, ThumbnailUiUtils.devicePixelSize(200, AffineTransform.getScaleInstance(2, 2)));
        assertEquals(0, ThumbnailUiUtils.devicePixelSize(0, AffineTransform.getScaleInstance(2, 2)));
    }

    @Test
    void aspectFitPreservesAspectAndCentersImage() {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);

        Rectangle fit = ThumbnailUiUtils.aspectFit(image, new Rectangle(0, 0, 100, 100));

        assertEquals(new Rectangle(0, 25, 100, 50), fit);
    }
}
