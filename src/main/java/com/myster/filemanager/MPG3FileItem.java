package com.myster.filemanager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.logging.Logger;

import com.drew.imaging.mp3.Mp3MetadataReader;
import com.drew.metadata.mp3.Mp3Directory;
import com.myster.mml.MessagePak;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Enriches the base {@link FileItem} metadata with audio-specific and ID3 tag
 * fields for files managed under the {@code MPG3} Myster type.
 *
 * <p>Metadata is extracted using Apache Tika's {@code Mp3Parser} (ID3 tags,
 * sample rate, and duration) and the {@code metadata-extractor} library
 * (bitrate), which is a transitive dependency of the Tika audiovideo parser
 * module.
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
    private static final Logger log = Logger.getLogger(MPG3FileItem.class.getName());

    // Common user-facing MP3 bitrates used for clamping computed VBR averages.
    private static final long[] LIKELY_MP3_KBPS = {
            32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320
    };

    private MessagePak messagePackRepresentation;

    public MPG3FileItem(Path root, Path path) {
        super(root, path);
    }

    public synchronized MessagePak getMessagePackRepresentation() {
        if (messagePackRepresentation != null)
            return messagePackRepresentation;

        messagePackRepresentation = super.getMessagePackRepresentation();

        patchFunction2(messagePackRepresentation, getPath());
        return messagePackRepresentation;
    }

    /**
     * Enriches {@code messagePack} with audio and ID3 metadata extracted from the
     * file at {@code path}. If the file cannot be parsed, enrichment is skipped and
     * the generic metadata already present in {@code messagePack} is left unchanged.
     *
     * <p>{@code /BitRate} and {@code /Hz} are only written for files whose name ends
     * with {@code ".mp3"}, preserving the existing protocol convention for this type.
     *
     * @param messagePack the {@link MessagePak} to enrich in-place
     * @param path        path of the audio file to read metadata from
     */
    public static void patchFunction2(MessagePak messagePack, Path path) {
        Metadata tikaMetadata = new Metadata();
        try (InputStream in = Files.newInputStream(path)) {
            new Mp3Parser().parse(in, new DefaultHandler(), tikaMetadata, new ParseContext());
        } catch (Throwable ex) {
            log.warning("Could not read ID3 tag info for: " + path + " - " + ex.getMessage());
            return;
        }

        OptionalDouble durationSeconds = parseDurationSeconds(tikaMetadata.get(XMPDM.DURATION));
        if (durationSeconds.isPresent()) {
            messagePack.putLong("/LengthSec", Math.round(durationSeconds.getAsDouble()));
        }

        if (path.getFileName().toString().endsWith(".mp3")) {
            addMp3SpecificInformation(messagePack, path, durationSeconds, tikaMetadata);
        }

        putIfNotBlank(messagePack, "/ID3Name", tikaMetadata.get(TikaCoreProperties.TITLE));
        putIfNotBlank(messagePack, "/Artist", tikaMetadata.get(XMPDM.ARTIST));
        putIfNotBlank(messagePack, "/Album", tikaMetadata.get(XMPDM.ALBUM));
        // /OriginalArtist intentionally not emitted — ID3v2 TOPE frame not surfaced by Tika
    }

    private static void addMp3SpecificInformation(MessagePak messagePack, Path path, OptionalDouble durationSeconds, Metadata tikaMetadata) {
        boolean wroteBitRate = false;

        // Bitrate via metadata-extractor (returns kbps; multiply to bps for protocol).
        // For VBR files this may be missing or represent only the first frame bitrate.
        try {
            com.drew.metadata.Metadata drewMetadata =
                    Mp3MetadataReader.readMetadata(path.toFile());
            Mp3Directory mp3Dir = drewMetadata.getFirstDirectoryOfType(Mp3Directory.class);
            if (mp3Dir != null && mp3Dir.containsTag(Mp3Directory.TAG_BITRATE)) {
                OptionalLong bitRate = parseBitrateKbpsToBps(mp3Dir.getString(Mp3Directory.TAG_BITRATE));
                if (bitRate.isPresent()) {
                    messagePack.putLong("/BitRate", bitRate.getAsLong());
                    wroteBitRate = true;
                }
            }
        } catch (IOException ex) {
            log.fine("Could not read bitrate for: " + path + " - " + ex.getMessage());
        }

        // Fallback for VBR files without explicit bitrate tag: compute average and clamp.
        if (!wroteBitRate && durationSeconds.isPresent()) {
            try {
                estimateAverageBitrateBps(Files.size(path), durationSeconds.getAsDouble())
                        .ifPresent(bps -> messagePack.putLong("/BitRate", bps));
            } catch (IOException ex) {
                log.fine("Could not estimate bitrate for: " + path + " - " + ex.getMessage());
            }
        }

        // Sample rate from Tika's XMPDM property (Hz as a string, e.g. "44100").
        parseSampleRateHz(tikaMetadata.get(XMPDM.AUDIO_SAMPLE_RATE))
                .ifPresent(hz -> messagePack.putLong("/Hz", hz));
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