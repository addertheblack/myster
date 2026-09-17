package com.myster.thumbnail;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Explicit acquisition whitelists; an allowed extension does not guarantee a thumbnail. */
enum ThumbnailPlatform {
    WINDOWS(Set.of("jpg", "jpeg", "avi", "mkv", "mp4")),
    MAC(Set.of("jpg", "jpeg", "mp4")),
    LINUX(Set.of("jpg", "jpeg", "avi", "mkv", "mp4")),
    UNSUPPORTED(Set.of());

    private final Set<String> extensions;

    ThumbnailPlatform(Set<String> extensions) {
        this.extensions = extensions;
    }

    /** Immutable lowercase extensions without dots. */
    Set<String> allowedExtensions() {
        return extensions;
    }

    /** Checks only the final filename extension, without filesystem or native access. */
    boolean isAllowed(Path file) {
        Path filename = file.getFileName();
        if (filename == null) {
            return false;
        }
        String name = filename.toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    static ThumbnailPlatform fromOsName(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return WINDOWS;
        }
        if (os.startsWith("mac")) {
            return MAC;
        }
        if (os.startsWith("linux")) {
            return LINUX;
        }
        return UNSUPPORTED;
    }

    ThumbnailProvider createProvider() {
        return switch (this) {
            case WINDOWS -> new WindowsThumbnailProvider();
            case MAC -> new MacThumbnailProvider();
            case LINUX -> new LinuxThumbnailProvider();
            case UNSUPPORTED -> (file, size) -> Optional.empty();
        };
    }
}
