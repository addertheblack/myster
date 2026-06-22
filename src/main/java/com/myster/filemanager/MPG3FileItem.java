package com.myster.filemanager;

import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import com.myster.mml.MessagePak;

/**
 * Enriches the base {@link FileItem} metadata with audio-specific and ID3 tag
 * fields for files managed under the {@code MPG3} Myster type.
 *
 * <p>Metadata enrichment is delegated to an injected {@link MetadataProvider}
 * so production indexing can use a persistent disk cache while tests can supply
 * a fake provider.
 *
 * <p>The following {@link MessagePak} keys are part of the Myster file-metadata
 * protocol and must not change without a corresponding protocol version bump:
 * <ul>
 *   <li>{@code /BitRate} - bitrate in bits per second ({@code long}); {@code .mp3} files only</li>
 *   <li>{@code /Hz} - sample rate in Hz ({@code long}); {@code .mp3} files only</li>
 *   <li>{@code /LengthSec} - track duration in seconds ({@code long}) when Tika provides duration</li>
 *   <li>{@code /ID3Name} - track title ({@code String})</li>
 *   <li>{@code /Artist} - artist ({@code String})</li>
 *   <li>{@code /Album} - album ({@code String})</li>
 * </ul>
 */
public class MPG3FileItem extends FileItem {
    // Common user-facing MP3 bitrates used for clamping computed VBR averages.
    private static final long[] LIKELY_MP3_KBPS = {
            32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320
    };

    private final MetadataProvider metadataProvider;
    private MessagePak messagePackRepresentation;

    public MPG3FileItem(Path root, Path path, MetadataProvider metadataProvider) {
        super(root, path);
        this.metadataProvider = Objects.requireNonNull(metadataProvider);
    }

    public synchronized MessagePak getMessagePackRepresentation() {
        // If then we didn't need the date modified then we could just rely on the cache but meh.
        if (messagePackRepresentation != null)
            return messagePackRepresentation;

        messagePackRepresentation = super.getMessagePackRepresentation();

        metadataProvider.enrich(MetadataType.AUDIO, messagePackRepresentation, getPath());
        return messagePackRepresentation;
    }

    /**
     * Parses a bitrate string expressed in kbps and converts it to bits per second.
     *
     * @param raw kbps value as a string (e.g. {@code "128"})
     * @return bits per second, or empty if {@code raw} is null, blank, or unparseable
     */
    static OptionalLong parseBitrateKbpsToBps(String raw) {
        if (raw == null || raw.isBlank()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(raw.trim()) * 1000L);
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Parses a sample-rate string expressed in Hz.
     *
     * @param raw Hz value as a string (e.g. {@code "44100"})
     * @return sample rate in Hz, or empty if {@code raw} is null, blank, or unparseable
     */
    static OptionalLong parseSampleRateHz(String raw) {
        if (raw == null || raw.isBlank()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Parses Tika's duration value (seconds as a decimal string) into seconds.
     *
     * @param raw duration in seconds as provided by Tika (e.g. {@code "193.563"})
     * @return duration in seconds, or empty if missing, non-positive, or unparseable
     */
    static OptionalDouble parseDurationSeconds(String raw) {
        if (raw == null || raw.isBlank()) return OptionalDouble.empty();
        try {
            double seconds = Double.parseDouble(raw.trim());
            return seconds > 0 ? OptionalDouble.of(seconds) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Estimates average bitrate from file size and duration and clamps it to a
     * likely MP3 bitrate bucket for cleaner UI values.
     *
     * @param fileSizeBytes   file size in bytes
     * @param durationSeconds duration in seconds
     * @return clamped bitrate in bits per second, or empty if inputs are invalid
     */
    static OptionalLong estimateAverageBitrateBps(long fileSizeBytes, double durationSeconds) {
        if (fileSizeBytes <= 0 || durationSeconds <= 0) {
            return OptionalLong.empty();
        }

        double avgBps = (fileSizeBytes * 8d) / durationSeconds;
        long clampedKbps = clampToLikelyBitrateKbps(avgBps / 1000d);
        if (clampedKbps <= 0) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(clampedKbps * 1000L);
    }

    /**
     * Clamps an arbitrary kbps value to the nearest common MP3 bitrate bucket.
     */
    static long clampToLikelyBitrateKbps(double kbps) {
        if (kbps <= 0) {
            return -1;
        }

        long best = LIKELY_MP3_KBPS[0];
        double bestDelta = Math.abs(kbps - best);

        for (long candidate : LIKELY_MP3_KBPS) {
            double delta = Math.abs(kbps - candidate);
            if (delta < bestDelta) {
                best = candidate;
                bestDelta = delta;
            }
        }

        return best;
    }

    /**
     * Writes {@code value} into {@code messagePack} at {@code key} only when
     * {@code value} is non-null and non-empty, preventing blank strings from
     * entering the protocol payload.
     */
    static void putIfNotBlank(MessagePak messagePack, String key, String value) {
        if (value != null && !value.isEmpty()) {
            messagePack.putString(key, value);
        }
    }
}
