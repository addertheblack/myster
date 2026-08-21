package com.myster.filemanager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.logging.Logger;

import com.myster.mml.MessagePak;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.image.JpegParser;
import org.apache.tika.parser.image.TiffParser;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Extracts picture metadata using Apache Tika's image parser.
 *
 * <p>This provider emits compact browsing metadata only. It intentionally does
 * not copy GPS/location properties into Myster file stats.
 */
public class TikaImageMetadataProvider implements TypedMetadataProvider {
    private static final Logger log = Logger.getLogger(TikaImageMetadataProvider.class.getName());

    @Override
    public void enrich(MessagePak messagePack, Path path) {
        Metadata metadata = new Metadata();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            detectContentType(path).ifPresent(contentType -> metadata.set(Metadata.CONTENT_TYPE,
                    contentType));
            parserFor(path).parse(in, new DefaultHandler(), metadata, new ParseContext());
        } catch (IOException | SAXException | TikaException ex) {
            log.warning("Could not read image metadata for: " + path + " - " + ex.getMessage());
            return;
        }

        putPositiveLong(messagePack, "/ImageWidth", metadata.getInt(TIFF.IMAGE_WIDTH));
        putPositiveLong(messagePack, "/ImageHeight", metadata.getInt(TIFF.IMAGE_LENGTH));
        deriveBitDepth(metadata).ifPresent(value -> messagePack.putLong("/ImageBitDepth", value));
        firstDate(metadata, TIFF.ORIGINAL_DATE, TikaCoreProperties.CREATED,
                TikaCoreProperties.MODIFIED)
                        .ifPresent(date -> messagePack.putLong("/ImageTakenAtMillis",
                                date.getTime()));
        parsePositiveLong(metadata.get(TIFF.ORIENTATION))
                .ifPresent(value -> messagePack.putLong("/ImageOrientation", value));
        putIfNotBlank(messagePack, "/CameraMake", metadata.get(TIFF.EQUIPMENT_MAKE));
        putIfNotBlank(messagePack, "/CameraModel", metadata.get(TIFF.EQUIPMENT_MODEL));
        putIfNotBlank(messagePack, "/ImageSoftware",
                firstNonBlank(metadata.get(TIFF.SOFTWARE),
                        metadata.get(TikaCoreProperties.CREATOR_TOOL)).orElse(null));
    }

    static OptionalLong deriveBitDepth(Metadata metadata) {
        Optional<int[]> values = positiveIntValues(metadata, TIFF.BITS_PER_SAMPLE);
        if (values.isEmpty()) {
            return OptionalLong.empty();
        }

        int[] bitsPerSample = values.get();
        if (bitsPerSample.length > 1) {
            long total = 0;
            for (int bits : bitsPerSample) {
                total += bits;
            }
            return OptionalLong.of(total);
        }

        if (bitsPerSample.length == 1) {
            int bits = bitsPerSample[0];
            if (bits <= 0) {
                return OptionalLong.empty();
            }

            Integer samples = firstPositiveInt(metadata, TIFF.SAMPLES_PER_PIXEL).orElse(null);
            if (samples != null && samples > 0) {
                return OptionalLong.of((long) bits * samples);
            }
            return OptionalLong.of(bits);
        }

        return OptionalLong.empty();
    }

    private static Optional<int[]> positiveIntValues(Metadata metadata, Property property) {
        java.util.List<Integer> values = new java.util.ArrayList<>();
        for (String raw : metadata.getValues(property)) {
            for (String token : raw.trim().split("[\\s,]+")) {
                if (token.isBlank()) {
                    continue;
                }
                try {
                    int parsed = Integer.parseInt(token);
                    if (parsed <= 0) {
                        return Optional.empty();
                    }
                    values.add(parsed);
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            }
        }

        if (values.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(values.stream().mapToInt(Integer::intValue).toArray());
    }

    private static Optional<Integer> firstPositiveInt(Metadata metadata, Property property) {
        Optional<int[]> optionalValues = positiveIntValues(metadata, property);
        if (optionalValues.isEmpty()) {
            return Optional.empty();
        }
        int[] values = optionalValues.get();
        return values.length == 0 ? Optional.empty() : Optional.of(values[0]);
    }

    static OptionalLong parsePositiveLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static Parser parserFor(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".jpe")) {
            return new JpegParser();
        }
        if (filename.endsWith(".tif") || filename.endsWith(".tiff")) {
            return new TiffParser();
        }
        return new ImageParser();
    }

    private static Optional<String> detectContentType(Path path) throws IOException {
        String probed = Files.probeContentType(path);
        if (probed != null && !probed.isBlank()) {
            return Optional.of(probed);
        }

        String filename = path.getFileName().toString().toLowerCase();
        if (filename.endsWith(".png")) return Optional.of("image/png");
        if (filename.endsWith(".gif")) return Optional.of("image/gif");
        if (filename.endsWith(".bmp")) return Optional.of("image/bmp");
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".jpe")) {
            return Optional.of("image/jpeg");
        }
        if (filename.endsWith(".tif") || filename.endsWith(".tiff")) return Optional.of("image/tiff");
        return Optional.empty();
    }

    private static void putPositiveLong(MessagePak messagePack, String key, Integer value) {
        if (value != null && value > 0) {
            messagePack.putLong(key, value);
        }
    }

    private static Optional<Date> firstDate(Metadata metadata, Property... properties) {
        for (Property property : properties) {
            Date date = metadata.getDate(property);
            if (date != null) {
                return Optional.of(date);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    static void putIfNotBlank(MessagePak messagePack, String key, String value) {
        if (value != null && !value.isBlank()) {
            messagePack.putString(key, value);
        }
    }
}
