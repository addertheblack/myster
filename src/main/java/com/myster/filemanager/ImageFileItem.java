package com.myster.filemanager;

import java.nio.file.Path;
import java.util.Objects;

import com.myster.mml.MessagePak;

/**
 * Enriches the base {@link FileItem} metadata with image-specific fields for
 * files managed under the {@code PICT} Myster type.
 *
 * <p>The following optional {@link MessagePak} keys are part of the Myster
 * file-metadata protocol for picture files:
 * <ul>
 *   <li>{@code /ImageWidth} - image width in pixels ({@code long})</li>
 *   <li>{@code /ImageHeight} - image height in pixels ({@code long})</li>
 *   <li>{@code /ImageBitDepth} - total bits per pixel when known ({@code long})</li>
 *   <li>{@code /ImageTakenAtMillis} - capture timestamp as epoch milliseconds ({@code long})</li>
 *   <li>{@code /ImageOrientation} - EXIF orientation value ({@code long})</li>
 *   <li>{@code /CameraMake} - camera manufacturer ({@code String})</li>
 *   <li>{@code /CameraModel} - camera model ({@code String})</li>
 *   <li>{@code /ImageSoftware} - software/firmware that produced the image ({@code String})</li>
 * </ul>
 *
 * <p>All image fields are omitted when unavailable or unparseable. Location/GPS
 * metadata is intentionally not emitted.
 */
public class ImageFileItem extends FileItem {
    private final FileMetadataExtractor metadataExtractor;
    private MessagePak messagePackRepresentation;

    public ImageFileItem(Path root, Path path, FileMetadataExtractor metadataExtractor) {
        super(root, path);
        this.metadataExtractor = Objects.requireNonNull(metadataExtractor);
    }

    @Override
    public synchronized MessagePak getMessagePackRepresentation() {
        if (messagePackRepresentation != null) {
            return messagePackRepresentation;
        }

        messagePackRepresentation = super.getMessagePackRepresentation();
        metadataExtractor.enrich(MetadataType.IMAGE, messagePackRepresentation, getPath());
        return messagePackRepresentation;
    }
}
