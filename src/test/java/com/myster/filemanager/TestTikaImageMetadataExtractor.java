package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.OptionalLong;

import javax.imageio.ImageIO;

import com.myster.mml.MessagePak;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestTikaImageMetadataExtractor {
    @TempDir
    Path tempDir;

    @Test
    void deriveBitDepth_sumsMultiChannelBits() {
        Metadata metadata = new Metadata();
        metadata.set(TIFF.BITS_PER_SAMPLE, new String[] { "8", "8", "8" });

        assertEquals(OptionalLong.of(24), TikaImageMetadataExtractor.deriveBitDepth(metadata));
    }

    @Test
    void deriveBitDepth_multipliesSingleBitsBySamplesWhenAvailable() {
        Metadata metadata = new Metadata();
        metadata.set(TIFF.BITS_PER_SAMPLE, new String[] { "8" });
        metadata.set(TIFF.SAMPLES_PER_PIXEL, 3);

        assertEquals(OptionalLong.of(24), TikaImageMetadataExtractor.deriveBitDepth(metadata));
    }

    @Test
    void deriveBitDepth_usesSingleBitsWhenSamplesMissing() {
        Metadata metadata = new Metadata();
        metadata.set(TIFF.BITS_PER_SAMPLE, new String[] { "16" });

        assertEquals(OptionalLong.of(16), TikaImageMetadataExtractor.deriveBitDepth(metadata));
    }

    @Test
    void deriveBitDepth_emptyForInvalidValues() {
        Metadata metadata = new Metadata();
        metadata.set(TIFF.BITS_PER_SAMPLE, new String[] { "8", "0", "8" });

        assertTrue(TikaImageMetadataExtractor.deriveBitDepth(metadata).isEmpty());
    }

    @Test
    void parsePositiveLong_omitsNullBlankInvalidAndNonPositive() {
        assertTrue(TikaImageMetadataExtractor.parsePositiveLong(null).isEmpty());
        assertTrue(TikaImageMetadataExtractor.parsePositiveLong(" ").isEmpty());
        assertTrue(TikaImageMetadataExtractor.parsePositiveLong("abc").isEmpty());
        assertTrue(TikaImageMetadataExtractor.parsePositiveLong("0").isEmpty());
        assertTrue(TikaImageMetadataExtractor.parsePositiveLong("-1").isEmpty());
    }

    @Test
    void parsePositiveLong_parsesPositiveLong() {
        assertEquals(OptionalLong.of(6), TikaImageMetadataExtractor.parsePositiveLong(" 6 "));
    }

    @Test
    void doesNotThrowForNonExistentFile() {
        MessagePak mp = MessagePak.newEmpty();
        assertDoesNotThrow(() -> new TikaImageMetadataExtractor().enrich(mp,
                tempDir.resolve("missing.jpg")));
    }

    @Test
    void leavesMessagePakWithoutImageKeysOnParseFailure() {
        MessagePak mp = MessagePak.newEmpty();
        new TikaImageMetadataExtractor().enrich(mp, tempDir.resolve("missing.jpg"));

        assertTrue(mp.getLong("/ImageWidth").isEmpty());
        assertTrue(mp.getLong("/ImageHeight").isEmpty());
        assertTrue(mp.getLong("/ImageBitDepth").isEmpty());
        assertTrue(mp.getLong("/ImageTakenAtMillis").isEmpty());
        assertTrue(mp.getLong("/ImageOrientation").isEmpty());
        assertTrue(mp.getString("/CameraMake").isEmpty());
        assertTrue(mp.getString("/CameraModel").isEmpty());
        assertTrue(mp.getString("/ImageSoftware").isEmpty());
    }

    @Test
    void extractsDimensionsFromPng() throws Exception {
        Path image = tempDir.resolve("image.png");
        BufferedImage bufferedImage = new BufferedImage(17, 9, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(bufferedImage, "png", image.toFile());
        MessagePak mp = MessagePak.newEmpty();

        new TikaImageMetadataExtractor().enrich(mp, image);

        assertEquals(17L, mp.getLong("/ImageWidth").orElseThrow());
        assertEquals(9L, mp.getLong("/ImageHeight").orElseThrow());
    }
}
