package com.myster.filemanager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.logging.Logger;

import com.myster.mml.MessagePak;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Extracts audio metadata using Apache Tika's MP3 parser.
 */
public class TikaAudioMetadataProvider implements TypedMetadataProvider {
    private static final Logger log = Logger.getLogger(TikaAudioMetadataProvider.class.getName());

    @Override
    public void enrich(MessagePak messagePack, Path path) {
        Metadata tikaMetadata = new Metadata();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            new Mp3Parser().parse(in, new DefaultHandler(), tikaMetadata, new ParseContext());
        } catch (IOException | SAXException | TikaException ex) {
            log.warning("Could not read ID3 tag info for: " + path + " - " + ex.getMessage());
            return;
        }

        OptionalDouble durationSeconds = MPG3FileItem.parseDurationSeconds(
                tikaMetadata.get(XMPDM.DURATION));
        if (durationSeconds.isPresent()) {
            messagePack.putLong("/LengthSec", Math.round(durationSeconds.getAsDouble()));
        }

        if (path.getFileName().toString().endsWith(".mp3")) {
            addMp3SpecificInformation(messagePack, path, durationSeconds, tikaMetadata);
        }

        MPG3FileItem.putIfNotBlank(messagePack, "/ID3Name",
                tikaMetadata.get(TikaCoreProperties.TITLE));
        MPG3FileItem.putIfNotBlank(messagePack, "/Artist", tikaMetadata.get(XMPDM.ARTIST));
        MPG3FileItem.putIfNotBlank(messagePack, "/Album", tikaMetadata.get(XMPDM.ALBUM));
    }

    private static void addMp3SpecificInformation(MessagePak messagePack,
                                                  Path path,
                                                  OptionalDouble durationSeconds,
                                                  Metadata tikaMetadata) {
        if (durationSeconds.isPresent() && messagePack.getLong("/size").isPresent()) {
            long size = messagePack.getLong("/size").orElseThrow();
            MPG3FileItem.estimateAverageBitrateBps(size, durationSeconds.getAsDouble())
                    .ifPresent(bps -> messagePack.putLong("/BitRate", bps));
        }

        MPG3FileItem.parseSampleRateHz(tikaMetadata.get(XMPDM.AUDIO_SAMPLE_RATE))
                .ifPresent(hz -> messagePack.putLong("/Hz", hz));
    }
}
