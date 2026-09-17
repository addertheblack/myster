package com.myster.thumbnail;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestFreedesktopThumbnailCache {
    @TempDir
    Path directory;

    @Test
    void readsUnicodeAndSpacesUsingStandardUriAndMetadata() throws Exception {
        Path file = Files.writeString(directory.resolve("a picture é #.jpg"), "source");
        Path root = directory.resolve("cache");
        writeThumbnail(root, file, "normal", metadata(file));
        BufferedImage image = new FreedesktopThumbnailCache(root).load(file, 64);
        assertNotNull(image);
        assertEquals(128, image.getWidth());
        assertEquals(64, image.getHeight());
    }

    @Test
    void ignoresMissingStaleMismatchedAndCorruptEntries() throws Exception {
        Path file = Files.writeString(directory.resolve("picture.jpg"), "source");
        Path root = directory.resolve("cache");
        FreedesktopThumbnailCache cache = new FreedesktopThumbnailCache(root);
        assertNull(cache.load(file, 32));
        for (Map<String, String> tags : List.of(
                Map.<String, String>of(),
                Map.of("Thumb::URI", file.toUri().toASCIIString(), "Thumb::MTime", "0"),
                Map.of("Thumb::URI", "file:///different.jpg", "Thumb::MTime", modified(file)),
                Map.of("Thumb::URI", file.toUri().toASCIIString(), "Thumb::MTime", modified(file),
                       "Thumb::Size", "99999"))) {
            writeThumbnail(root, file, "normal", tags);
            assertNull(cache.load(file, 32));
        }
        Path png = writeThumbnail(root, file, "normal", metadata(file));
        Files.writeString(png, "corrupt");
        assertNull(cache.load(file, 32));
    }

    @Test
    void acceptsTumblerFractionalSecondsAndRejectsSubsecondChanges() throws Exception {
        Path file = Files.writeString(directory.resolve("picture.jpg"), "source");
        Files.setLastModifiedTime(file, FileTime.from(Instant.ofEpochSecond(1700000000, 123456789)));
        Path root = directory.resolve("cache");
        writeThumbnail(root, file, "normal", Map.of("Thumb::URI", file.toUri().toASCIIString(),
                                                   "Thumb::MTime", "1700000000.123456"));
        FreedesktopThumbnailCache cache = new FreedesktopThumbnailCache(root);
        assertNotNull(cache.load(file, 32));
        Files.setLastModifiedTime(file, FileTime.from(Instant.ofEpochSecond(1700000000, 987654321)));
        assertNull(cache.load(file, 32));
    }

    @Test
    void ignoresMalformedModificationTimes() throws Exception {
        Path file = Files.writeString(directory.resolve("picture.jpg"), "source");
        Path root = directory.resolve("cache");
        for (String modified : List.of("NaN", "1e1000000000", "", "1700000000.1234567890")) {
            writeThumbnail(root, file, "normal", Map.of("Thumb::URI", file.toUri().toASCIIString(),
                                                       "Thumb::MTime", modified));
            assertNull(new FreedesktopThumbnailCache(root).load(file, 32));
        }
    }

    @Test
    void acceptsLargerCacheFlavorButDoesNotEnlargeSmallCachedThumbnails() throws Exception {
        Path file = Files.writeString(directory.resolve("picture.jpg"), "source");
        Path root = directory.resolve("cache");
        writeThumbnail(root, file, "large", metadata(file));
        FreedesktopThumbnailCache cache = new FreedesktopThumbnailCache(root);
        assertNotNull(cache.load(file, 64));
        assertNull(cache.load(file, 512));
    }

    private static Map<String, String> metadata(Path file) throws Exception {
        return Map.of("Thumb::URI", file.toUri().toASCIIString(), "Thumb::MTime", modified(file),
                      "Thumb::Size", Long.toString(Files.size(file)));
    }

    private static String modified(Path file) throws Exception {
        return Long.toString(Files.getLastModifiedTime(file).to(TimeUnit.SECONDS));
    }

    private static Path writeThumbnail(Path root, Path file, String flavor,
                                       Map<String, String> tags) throws Exception {
        String uri = file.toUri().toASCIIString();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                .digest(uri.getBytes(StandardCharsets.UTF_8)));
        Path png = Files.createDirectories(root.resolve(flavor)).resolve(hash + ".png");
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        try (var output = ImageIO.createImageOutputStream(png.toFile())) {
            BufferedImage image = new BufferedImage(128, 64, BufferedImage.TYPE_INT_RGB);
            var metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), null);
            var tree = new IIOMetadataNode("javax_imageio_png_1.0");
            var text = new IIOMetadataNode("tEXt");
            for (var tag : tags.entrySet()) {
                var entry = new IIOMetadataNode("tEXtEntry");
                entry.setAttribute("keyword", tag.getKey());
                entry.setAttribute("value", tag.getValue());
                text.appendChild(entry);
            }
            tree.appendChild(text);
            metadata.mergeTree("javax_imageio_png_1.0", tree);
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, metadata), null);
        } finally {
            writer.dispose();
        }
        return png;
    }
}
