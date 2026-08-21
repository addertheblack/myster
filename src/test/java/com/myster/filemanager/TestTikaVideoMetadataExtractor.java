package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import com.myster.mml.MessagePak;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestTikaVideoMetadataExtractor {
    @TempDir
    Path tempDir;

    @Test
    void addMetadata_convertsTikaProperties() {
        Metadata tika = new Metadata();
        tika.set(XMPDM.DURATION, "6138.4");
        tika.set(Metadata.IMAGE_WIDTH, 1920);
        tika.set(Metadata.IMAGE_LENGTH, 1080);
        tika.set(XMPDM.VIDEO_COMPRESSOR, " H.264 ");
        tika.set(XMPDM.FILE_DATA_RATE, "4.5 Mbps");
        MessagePak metadata = MessagePak.newEmpty();

        TikaVideoMetadataExtractor.addMetadata(metadata, tika);

        assertEquals(6138L, metadata.getLong("/VideoLengthSec").orElseThrow());
        assertEquals(1920L, metadata.getLong("/VideoWidth").orElseThrow());
        assertEquals(1080L, metadata.getLong("/VideoHeight").orElseThrow());
        assertEquals("H.264", metadata.getString("/VideoCodec").orElseThrow());
        assertEquals(4_500_000L, metadata.getLong("/VideoBitRate").orElseThrow());
    }

    @Test
    void addMetadata_estimatesAverageBitRateWhenParserRateIsMissing() {
        Metadata tika = new Metadata();
        tika.set(XMPDM.DURATION, "2");
        MessagePak metadata = MessagePak.newEmpty();
        metadata.putLong("/size", 1_000);

        TikaVideoMetadataExtractor.addMetadata(metadata, tika);

        assertEquals(4_000L, metadata.getLong("/VideoBitRate").orElseThrow());
    }

    @Test
    void addMetadata_prefersParserBitRateOverEstimate() {
        Metadata tika = new Metadata();
        tika.set(XMPDM.DURATION, "2");
        tika.set(XMPDM.FILE_DATA_RATE, "12 kbps");
        MessagePak metadata = MessagePak.newEmpty();
        metadata.putLong("/size", 1_000);

        TikaVideoMetadataExtractor.addMetadata(metadata, tika);

        assertEquals(12_000L, metadata.getLong("/VideoBitRate").orElseThrow());
    }

    @Test
    void parseDurationSeconds_acceptsOnlyPositiveFiniteValues() {
        assertEquals(OptionalDouble.of(1.25),
                TikaVideoMetadataExtractor.parseDurationSeconds(" 1.25 "));
        assertTrue(TikaVideoMetadataExtractor.parseDurationSeconds(null).isEmpty());
        assertTrue(TikaVideoMetadataExtractor.parseDurationSeconds(" ").isEmpty());
        assertTrue(TikaVideoMetadataExtractor.parseDurationSeconds("bad").isEmpty());
        assertTrue(TikaVideoMetadataExtractor.parseDurationSeconds("0").isEmpty());
        assertTrue(TikaVideoMetadataExtractor.parseDurationSeconds("-1").isEmpty());
        assertTrue(TikaVideoMetadataExtractor.parseDurationSeconds("NaN").isEmpty());
        assertTrue(TikaVideoMetadataExtractor.parseDurationSeconds("Infinity").isEmpty());
    }

    @Test
    void parseBitRateBps_supportsProtocolUnits() {
        assertEquals(OptionalLong.of(500), TikaVideoMetadataExtractor.parseBitRateBps("500"));
        assertEquals(OptionalLong.of(320_000),
                TikaVideoMetadataExtractor.parseBitRateBps("320 kbps"));
        assertEquals(OptionalLong.of(320_000),
                TikaVideoMetadataExtractor.parseBitRateBps("320 kbit/s"));
        assertEquals(OptionalLong.of(2_500_000),
                TikaVideoMetadataExtractor.parseBitRateBps("2.5 Mbps"));
        assertEquals(OptionalLong.of(2_500_000),
                TikaVideoMetadataExtractor.parseBitRateBps("2.5 Mbit/s"));
    }

    @Test
    void parseBitRateBps_rejectsInvalidAndOverflowValues() {
        assertTrue(TikaVideoMetadataExtractor.parseBitRateBps(null).isEmpty());
        assertTrue(TikaVideoMetadataExtractor.parseBitRateBps("0").isEmpty());
        assertTrue(TikaVideoMetadataExtractor.parseBitRateBps("-1 Mbps").isEmpty());
        assertTrue(TikaVideoMetadataExtractor.parseBitRateBps("12 MB/s").isEmpty());
        assertTrue(TikaVideoMetadataExtractor.parseBitRateBps("1e30 Mbps").isEmpty());
        assertTrue(TikaVideoMetadataExtractor.parseBitRateBps("9999999999999999999 Mbps")
                .isEmpty());
    }

    @Test
    void estimateAverageBitRateBps_validatesInputs() {
        assertEquals(OptionalLong.of(4_000),
                TikaVideoMetadataExtractor.estimateAverageBitRateBps(1_000, 2));
        assertTrue(TikaVideoMetadataExtractor.estimateAverageBitRateBps(0, 2).isEmpty());
        assertTrue(TikaVideoMetadataExtractor.estimateAverageBitRateBps(1_000, 0).isEmpty());
        assertTrue(TikaVideoMetadataExtractor.estimateAverageBitRateBps(1_000, Double.NaN)
                .isEmpty());
    }

    @Test
    void missingFileDoesNotThrowOrEmitVideoFields() {
        MessagePak metadata = MessagePak.newEmpty();

        assertDoesNotThrow(() -> new TikaVideoMetadataExtractor().enrich(metadata,
                tempDir.resolve("missing.mp4")));

        assertTrue(metadata.getLong("/VideoLengthSec").isEmpty());
        assertTrue(metadata.getLong("/VideoWidth").isEmpty());
        assertTrue(metadata.getLong("/VideoHeight").isEmpty());
        assertTrue(metadata.getString("/VideoCodec").isEmpty());
        assertTrue(metadata.getLong("/VideoBitRate").isEmpty());
    }
}
