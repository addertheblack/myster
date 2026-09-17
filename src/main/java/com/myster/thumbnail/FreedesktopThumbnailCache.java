package com.myster.thumbnail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.Node;

/** Reads the standard shared PNG cache, validating its URI and file modification metadata. */
final class FreedesktopThumbnailCache {
    private static final Logger log = Logger.getLogger(FreedesktopThumbnailCache.class.getName());
    private static final String[] FLAVORS = { "normal", "large", "x-large", "xx-large" };
    private static final int NORMAL_FLAVOR_INDEX = 0;
    private static final int LARGE_FLAVOR_INDEX = 1;
    private static final int X_LARGE_FLAVOR_INDEX = 2;
    private static final int XX_LARGE_FLAVOR_INDEX = 3;
    private static final String PNG_METADATA_FORMAT = "javax_imageio_png_1.0";
    private static final String PNG_UNCOMPRESSED_LATIN1_TEXT_ENTRY = "tEXtEntry";
    private static final String PNG_COMPRESSED_LATIN1_TEXT_ENTRY = "zTXtEntry";
    private static final String PNG_UTF8_TEXT_ENTRY = "iTXtEntry";
    private static final String PNG_TEXT_KEYWORD_ATTRIBUTE = "keyword";
    private static final String PNG_TEXT_VALUE_ATTRIBUTE = "value";
    private static final String PNG_TEXT_CONTENT_ATTRIBUTE = "text";
    private static final String SOURCE_FILE_URI_KEY = "Thumb::URI";
    private static final String SOURCE_FILE_MODIFIED_TIME_KEY = "Thumb::MTime";
    private static final String SOURCE_FILE_SIZE_BYTES_KEY = "Thumb::Size";
    private final Path root;

    FreedesktopThumbnailCache(Path root) {
        this.root = root;
    }

    static String flavor(int size) {
        return FLAVORS[index(size)];
    }

    private static int index(int size) {
        return size <= 128 ? NORMAL_FLAVOR_INDEX
                : size <= 256 ? LARGE_FLAVOR_INDEX
                : size <= 512 ? X_LARGE_FLAVOR_INDEX
                : XX_LARGE_FLAVOR_INDEX;
    }

    BufferedImage load(Path file, int size) throws IOException {
        String uri = file.toUri().toASCIIString();
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(uri.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Java requires MD5 support", e);
        }
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        for (int i = index(size); i < FLAVORS.length; i++) {
            Path png = root.resolve(FLAVORS[i]).resolve(hash + ".png");
            if (!Files.isRegularFile(png)) {
                continue;
            }
            try {
                BufferedImage image = read(png, uri, attributes);
                if (image != null) {
                    log.info(() -> "Thumbnail source=freedesktop cache cache=" + png + " file=" + file);
                    return image;
                }
            } catch (IOException e) {
                log.log(Level.FINE, "Ignoring unreadable desktop thumbnail " + png, e);
            }
        }
        return null;
    }

    private BufferedImage read(Path png, String uri, BasicFileAttributes attributes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(png.toFile())) {
            if (input == null) {
                return null;
            }
            var readers = ImageIO.getImageReadersByFormatName("png");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                Map<String, String> metadata =
                        collectText(reader.getImageMetadata(0).getAsTree(PNG_METADATA_FORMAT));
                if (!uri.equals(metadata.get(SOURCE_FILE_URI_KEY))
                        || !matchesModifiedTime(metadata.get(SOURCE_FILE_MODIFIED_TIME_KEY),
                                                attributes.lastModifiedTime())
                        || (metadata.containsKey(SOURCE_FILE_SIZE_BYTES_KEY)
                            && !Long.toString(attributes.size()).equals(metadata.get(SOURCE_FILE_SIZE_BYTES_KEY)))) {
                    log.fine(() -> "Ignoring stale/mismatched desktop thumbnail " + png);
                    return null;
                }
                if (reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) {
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static boolean matchesModifiedTime(String cached, FileTime modified) {
        if (cached == null || !cached.matches("-?\\d{1,19}(\\.\\d{1,9})?")) {
            return false;
        }
        // The standard uses seconds; Tumbler also writes fractional seconds with microsecond precision.
        BigDecimal timestamp = new BigDecimal(cached);
        var instant = modified.toInstant();
        BigDecimal actual = BigDecimal.valueOf(instant.getEpochSecond())
                .add(BigDecimal.valueOf(instant.getNano(), 9));
        return actual.setScale(timestamp.scale(), RoundingMode.FLOOR).compareTo(timestamp) == 0;
    }

    private static Map<String, String> collectText(Node node) {
        Map<String, String> text = new HashMap<>();
        String valueAttribute = switch (node.getNodeName()) {
            case PNG_UNCOMPRESSED_LATIN1_TEXT_ENTRY -> PNG_TEXT_VALUE_ATTRIBUTE;
            case PNG_UTF8_TEXT_ENTRY, PNG_COMPRESSED_LATIN1_TEXT_ENTRY -> PNG_TEXT_CONTENT_ATTRIBUTE;
            default -> null;
        };
        if (valueAttribute != null) {
            text.put(node.getAttributes().getNamedItem(PNG_TEXT_KEYWORD_ATTRIBUTE).getNodeValue(),
                     node.getAttributes().getNamedItem(valueAttribute).getNodeValue());
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            text.putAll(collectText(child));
        }
        return text;
    }
}
