package com.myster.thumbnail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

/**
 * Quick Look subprocesses have isolated output directories and a finite lifetime.
 * The thumbnail service filters requests through the macOS extension whitelist first.
 */
final class MacThumbnailProvider implements ThumbnailProvider {
    private static final Logger log = Logger.getLogger(MacThumbnailProvider.class.getName());

    @Override
    public Optional<BufferedImage> load(Path file, int size) throws IOException, InterruptedException {
        Path output = Files.createTempDirectory("myster-quicklook-");
        Process process = null;
        try {
            log.info(() -> "Thumbnail request source=macOS Quick Look file=" + file);
            process = new ProcessBuilder("/usr/bin/qlmanage", "-t", "-s", Integer.toString(size),
                                         "-o", output.toString(), file.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                throw new IOException("Quick Look timed out after 15 seconds");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Quick Look exited with status " + process.exitValue());
            }
            Path thumbnail = output.resolve(file.getFileName() + ".png");
            if (!Files.isRegularFile(thumbnail)) {
                return Optional.empty();
            }
            BufferedImage image = ImageIO.read(thumbnail.toFile());
            if (image != null) {
                log.info(() -> "Thumbnail source=macOS Quick Look output=" + thumbnail + " file=" + file);
            }
            return Optional.ofNullable(image);
        } finally {
            try {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            } finally {
                cleanOutput(output);
            }
        }
    }

    private void cleanOutput(Path output) {
        try (var files = Files.list(output)) {
            for (Path temporary : files.toList()) {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            log.log(Level.FINE, "Cannot clean Quick Look output " + output, e);
        }
        try {
            Files.deleteIfExists(output);
        } catch (IOException e) {
            log.log(Level.FINE, "Cannot remove Quick Look directory " + output, e);
        }
    }
}
