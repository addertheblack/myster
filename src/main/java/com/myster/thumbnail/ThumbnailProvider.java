package com.myster.thumbnail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** Blocking platform acquisition, called only on thumbnail worker threads. */
interface ThumbnailProvider {
    /** Returns an image to fit, or an empty optional when the platform cannot supply a thumbnail. */
    Optional<BufferedImage> load(Path file, int size) throws IOException, InterruptedException;
}
