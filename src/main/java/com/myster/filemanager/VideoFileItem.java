package com.myster.filemanager;

import java.nio.file.Path;
import java.util.Objects;

import com.myster.mml.MessagePak;

/**
 * Enriches base file metadata with video fields for files subscribed to the {@code video}
 * metadata profile.
 *
 * <p>The following optional {@link MessagePak} keys are part of the Myster file-metadata
 * protocol for video files:
 * <ul>
 *   <li>{@code /VideoLengthSec} - duration rounded to whole seconds ({@code long})</li>
 *   <li>{@code /VideoWidth} - encoded display width in pixels ({@code long})</li>
 *   <li>{@code /VideoHeight} - encoded display height in pixels ({@code long})</li>
 *   <li>{@code /VideoCodec} - parser-provided video compressor or codec ({@code String})</li>
 *   <li>{@code /VideoBitRate} - bitrate in bits per second ({@code long})</li>
 * </ul>
 *
 * <p>When the parser does not provide a bitrate, the value may be estimated from file size and
 * duration. That estimate represents the complete multiplexed file, including audio and container
 * overhead. Unavailable or invalid fields are omitted independently.
 */
public class VideoFileItem extends FileItem {
    private final FileMetadataExtractor metadataExtractor;
    private MessagePak messagePackRepresentation;

    public VideoFileItem(Path root, Path path, FileMetadataExtractor metadataExtractor) {
        super(root, path);
        this.metadataExtractor = Objects.requireNonNull(metadataExtractor);
    }

    @Override
    public synchronized MessagePak getMessagePackRepresentation() {
        if (messagePackRepresentation != null) {
            return messagePackRepresentation;
        }

        messagePackRepresentation = super.getMessagePackRepresentation();
        metadataExtractor.enrich(MetadataType.VIDEO, messagePackRepresentation, getPath());
        return messagePackRepresentation;
    }
}
