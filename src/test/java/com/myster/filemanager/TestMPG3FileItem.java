package com.myster.filemanager;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import com.myster.mml.MessagePak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class TestMPG3FileItem {
    @TempDir
    Path tempDir;

    // ── putIfNotBlank ──────────────────────────────────────────────────────────

    @Test
    void putIfNotBlank_writesNonBlankValue() {
        MessagePak mp = MessagePak.newEmpty();
        MPG3FileItem.putIfNotBlank(mp, "/key", "value");
        assertEquals("value", mp.getString("/key").orElse(null));
    }

    @Test
    void putIfNotBlank_omitsNull() {
        MessagePak mp = MessagePak.newEmpty();
        MPG3FileItem.putIfNotBlank(mp, "/key", null);
        assertTrue(mp.getString("/key").isEmpty());
    }

    @Test
    void putIfNotBlank_omitsEmptyString() {
        MessagePak mp = MessagePak.newEmpty();
        MPG3FileItem.putIfNotBlank(mp, "/key", "");
        assertTrue(mp.getString("/key").isEmpty());
    }

    // ── parseBitrateKbpsToBps ─────────────────────────────────────────────────

    @Test
    void parseBitrateKbpsToBps_convertsTypicalValues() {
        assertEquals(OptionalLong.of(128_000L), MPG3FileItem.parseBitrateKbpsToBps("128"));
        assertEquals(OptionalLong.of(320_000L), MPG3FileItem.parseBitrateKbpsToBps("320"));
        assertEquals(OptionalLong.of(64_000L), MPG3FileItem.parseBitrateKbpsToBps("64"));
    }

    @Test
    void parseBitrateKbpsToBps_trimsWhitespace() {
        assertEquals(OptionalLong.of(192_000L), MPG3FileItem.parseBitrateKbpsToBps("  192  "));
    }

    @Test
    void parseBitrateKbpsToBps_emptyForNull() {
        assertTrue(MPG3FileItem.parseBitrateKbpsToBps(null).isEmpty());
    }

    @Test
    void parseBitrateKbpsToBps_emptyForBlank() {
        assertTrue(MPG3FileItem.parseBitrateKbpsToBps("   ").isEmpty());
    }

    @Test
    void parseBitrateKbpsToBps_emptyForNonNumeric() {
        assertTrue(MPG3FileItem.parseBitrateKbpsToBps("vbr").isEmpty());
        assertTrue(MPG3FileItem.parseBitrateKbpsToBps("128kbps").isEmpty());
    }

    // ── parseSampleRateHz ─────────────────────────────────────────────────────

    @Test
    void parseSampleRateHz_parsesCommonRates() {
        assertEquals(OptionalLong.of(44100L), MPG3FileItem.parseSampleRateHz("44100"));
        assertEquals(OptionalLong.of(48000L), MPG3FileItem.parseSampleRateHz("48000"));
        assertEquals(OptionalLong.of(22050L), MPG3FileItem.parseSampleRateHz("22050"));
    }

    @Test
    void parseSampleRateHz_trimsWhitespace() {
        assertEquals(OptionalLong.of(44100L), MPG3FileItem.parseSampleRateHz(" 44100 "));
    }

    @Test
    void parseSampleRateHz_emptyForNull() {
        assertTrue(MPG3FileItem.parseSampleRateHz(null).isEmpty());
    }

    @Test
    void parseSampleRateHz_emptyForBlank() {
        assertTrue(MPG3FileItem.parseSampleRateHz("").isEmpty());
    }

    @Test
    void parseSampleRateHz_emptyForNonNumeric() {
        assertTrue(MPG3FileItem.parseSampleRateHz("unknown").isEmpty());
    }

    // ── parseDurationSeconds ──────────────────────────────────────────────────

    @Test
    void parseDurationSeconds_parsesDecimalSeconds() {
        assertEquals(OptionalDouble.of(193.563), MPG3FileItem.parseDurationSeconds("193.563"));
    }

    @Test
    void parseDurationSeconds_emptyForNullBlankInvalidOrNonPositive() {
        assertTrue(MPG3FileItem.parseDurationSeconds(null).isEmpty());
        assertTrue(MPG3FileItem.parseDurationSeconds(" ").isEmpty());
        assertTrue(MPG3FileItem.parseDurationSeconds("NaN").isEmpty());
        assertTrue(MPG3FileItem.parseDurationSeconds("0").isEmpty());
        assertTrue(MPG3FileItem.parseDurationSeconds("-5.2").isEmpty());
    }

    // ── bitrate estimation and clamping ───────────────────────────────────────

    @Test
    void clampToLikelyBitrateKbps_picksNearestCommonRate() {
        assertEquals(160L, MPG3FileItem.clampToLikelyBitrateKbps(168.5));
        assertEquals(128L, MPG3FileItem.clampToLikelyBitrateKbps(133.0));
        assertEquals(192L, MPG3FileItem.clampToLikelyBitrateKbps(187.0));
    }

    @Test
    void estimateAverageBitrateBps_computesAndClamps() {
        // 4,017,150 bytes over 190.0s ~= 169,143 bps => 169.1 kbps, clamped to 160 kbps.
        OptionalLong avg = MPG3FileItem.estimateAverageBitrateBps(4_017_150L, 190.0);
        assertEquals(OptionalLong.of(160_000L), avg);
    }

    @Test
    void estimateAverageBitrateBps_emptyForInvalidInputs() {
        assertTrue(MPG3FileItem.estimateAverageBitrateBps(0, 100).isEmpty());
        assertTrue(MPG3FileItem.estimateAverageBitrateBps(10_000, 0).isEmpty());
        assertTrue(MPG3FileItem.estimateAverageBitrateBps(10_000, -1).isEmpty());
    }

    // ── TikaAudioMetadataProvider — error handling & protocol constraints ────

    @Test
    void tikaAudioMetadataProvider_doesNotThrowForNonExistentFile() {
        MessagePak mp = MessagePak.newEmpty();
        assertDoesNotThrow(() ->
                new TikaAudioMetadataProvider().enrich(mp,
                        Path.of("/nonexistent/completely/fake.mp3")));
    }

    @Test
    void tikaAudioMetadataProvider_leavesMessagePakEmptyOnParseFailure() {
        MessagePak mp = MessagePak.newEmpty();
        new TikaAudioMetadataProvider().enrich(mp, Path.of("/nonexistent/completely/fake.mp3"));
        assertTrue(mp.getLong("/BitRate").isEmpty());
        assertTrue(mp.getLong("/Hz").isEmpty());
        assertTrue(mp.getLong("/LengthSec").isEmpty());
        assertTrue(mp.getString("/ID3Name").isEmpty());
        assertTrue(mp.getString("/Artist").isEmpty());
        assertTrue(mp.getString("/Album").isEmpty());
    }

    @Test
    void tikaAudioMetadataProvider_neverEmitsVbr() {
        // /Vbr must never be emitted after the mp3agic migration — consumers rely on
        // missing-key tolerance, and Tika does not provide this as part of the current payload.
        MessagePak mp = MessagePak.newEmpty();
        new TikaAudioMetadataProvider().enrich(mp, Path.of("/nonexistent/completely/fake.mp3"));
        assertTrue(mp.getBoolean("/Vbr").isEmpty(), "/Vbr must never be emitted");
    }

    @Test
    void tikaAudioMetadataProvider_neverEmitsOriginalArtist() {
        // /OriginalArtist (ID3v2 TOPE frame) is not surfaced by Tika's Mp3Parser.
        MessagePak mp = MessagePak.newEmpty();
        new TikaAudioMetadataProvider().enrich(mp, Path.of("/nonexistent/completely/fake.mp3"));
        assertTrue(mp.getString("/OriginalArtist").isEmpty(),
                "/OriginalArtist must never be emitted");
    }

    @Test
    void bitRateStoredAsLong() {
        MessagePak mp = MessagePak.newEmpty();
        mp.putLong("/BitRate", 128_000L);
        assertEquals(128_000L, mp.getLong("/BitRate").orElseThrow());
    }

    @Test
    void sampleRateStoredAsLong() {
        MessagePak mp = MessagePak.newEmpty();
        mp.putLong("/Hz", 44100L);
        assertEquals(44100L, mp.getLong("/Hz").orElseThrow());
    }

    @Test
    void lengthStoredAsLongSeconds() {
        MessagePak mp = MessagePak.newEmpty();
        mp.putLong("/LengthSec", 194L);
        assertEquals(194L, mp.getLong("/LengthSec").orElseThrow());
    }

    @Test
    void getMessagePackRepresentation_usesInjectedProvider() throws Exception {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        MetadataProvider provider = (metadataType, messagePack, path) -> {
            assertEquals(MetadataType.AUDIO, metadataType);
            assertEquals(file, path);
            assertTrue(messagePack.getLong("/size").isPresent());
            messagePack.putLong("/LengthSec", 10);
        };

        MessagePak mp = new MPG3FileItem(tempDir, file, provider).getMessagePackRepresentation();

        assertEquals(Files.size(file), mp.getLong("/size").orElseThrow());
        assertEquals(10L, mp.getLong("/LengthSec").orElseThrow());
    }

    @Test
    void getMessagePackRepresentation_keepsMessagePakRamCache() throws Exception {
        Path file = Files.writeString(tempDir.resolve("song.mp3"), "content");
        AtomicInteger calls = new AtomicInteger();
        MetadataProvider provider = (metadataType, messagePack, path) -> {
            calls.incrementAndGet();
            messagePack.putLong("/LengthSec", 10);
        };
        MPG3FileItem item = new MPG3FileItem(tempDir, file, provider);

        MessagePak first = item.getMessagePackRepresentation();
        MessagePak second = item.getMessagePackRepresentation();

        assertSame(first, second);
        assertEquals(1, calls.get());
    }
}
