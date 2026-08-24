package com.myster.filemanager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.myster.mml.MessagePak;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Extracts best-effort video browsing metadata using the parsers available to Apache Tika.
 * Files with {@code .avi} and {@code .mkv} extensions are intentionally returned without metadata
 * before their contents are opened because the configured Tika parsers do not support them and
 * attempting extraction can cause substantial disk activity. Other unsupported containers and
 * unavailable fields are left absent from the Myster file metadata.
 */
public class TikaVideoMetadataExtractor implements TypedMetadataExtractor {
    private static final Logger log = Logger.getLogger(TikaVideoMetadataExtractor.class.getName());
    private static final Set<String> SKIPPED_EXTENSIONS = Set.of("avi", "mkv");
    private static final Pattern DATA_RATE = Pattern.compile(
            "^([+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*(?:(kbps|kbit/s|mbps|mbit/s))?$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public void enrich(MessagePak messagePack, Path path) {
        if (shouldSkipExtraction(path)) {
            return;
        }

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());

        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            new AutoDetectParser().parse(in, new DefaultHandler(), metadata, new ParseContext());
        } catch (IOException | SAXException | TikaException ex) {
            log.warning("Could not read video metadata for: " + path + " - " + ex.getMessage());
        }

        addMetadata(messagePack, metadata);
    }

    private static boolean shouldSkipExtraction(Path path) {
        String fileName = path.getFileName().toString();
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 0) {
            return false;
        }
        String extension = fileName.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        return SKIPPED_EXTENSIONS.contains(extension);
    }

    static void addMetadata(MessagePak messagePack, Metadata metadata) {
        OptionalDouble duration = parseDurationSeconds(metadata.get(XMPDM.DURATION));
        if (duration.isPresent()) {
            long roundedDuration = Math.round(duration.getAsDouble());
            if (roundedDuration > 0) {
                messagePack.putLong("/VideoLengthSec", roundedDuration);
            }
        }

        parsePositiveLong(metadata.get(Metadata.IMAGE_WIDTH))
                .ifPresent(value -> messagePack.putLong("/VideoWidth", value));
        parsePositiveLong(metadata.get(Metadata.IMAGE_LENGTH))
                .ifPresent(value -> messagePack.putLong("/VideoHeight", value));
        putIfNotBlank(messagePack, "/VideoCodec", metadata.get(XMPDM.VIDEO_COMPRESSOR));

        OptionalLong parserBitRate = parseBitRateBps(metadata.get(XMPDM.FILE_DATA_RATE));
        if (parserBitRate.isPresent()) {
            messagePack.putLong("/VideoBitRate", parserBitRate.getAsLong());
        } else if (duration.isPresent()) {
            Optional<Long> size = messagePack.getLong("/size");
            if (size.isPresent()) {
                estimateAverageBitRateBps(size.get(), duration.getAsDouble())
                        .ifPresent(value -> messagePack.putLong("/VideoBitRate", value));
            }
        }
    }

    static OptionalDouble parseDurationSeconds(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalDouble.empty();
        }
        try {
            double value = Double.parseDouble(raw.trim());
            return value > 0 && Double.isFinite(value)
                    ? OptionalDouble.of(value)
                    : OptionalDouble.empty();
        } catch (NumberFormatException ex) {
            return OptionalDouble.empty();
        }
    }

    static OptionalLong parsePositiveLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException ex) {
            return OptionalLong.empty();
        }
    }

    static OptionalLong parseBitRateBps(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalLong.empty();
        }

        Matcher matcher = DATA_RATE.matcher(raw.trim());
        if (!matcher.matches()) {
            return OptionalLong.empty();
        }

        try {
            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2);
            double multiplier = 1d;
            if (unit != null) {
                String normalizedUnit = unit.toLowerCase(Locale.ROOT);
                multiplier = normalizedUnit.startsWith("k") ? 1_000d : 1_000_000d;
            }
            double bitsPerSecond = value * multiplier;
            if (bitsPerSecond <= 0 || !Double.isFinite(bitsPerSecond)
                    || bitsPerSecond > Long.MAX_VALUE) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Math.round(bitsPerSecond));
        } catch (NumberFormatException ex) {
            return OptionalLong.empty();
        }
    }

    static OptionalLong estimateAverageBitRateBps(long fileSizeBytes, double durationSeconds) {
        if (fileSizeBytes <= 0 || durationSeconds <= 0 || !Double.isFinite(durationSeconds)) {
            return OptionalLong.empty();
        }

        double bitsPerSecond = (fileSizeBytes * 8d) / durationSeconds;
        if (bitsPerSecond <= 0 || !Double.isFinite(bitsPerSecond)
                || bitsPerSecond > Long.MAX_VALUE) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.round(bitsPerSecond));
    }

    private static void putIfNotBlank(MessagePak messagePack, String key, String value) {
        if (value != null && !value.isBlank()) {
            messagePack.putString(key, value.trim());
        }
    }
}
