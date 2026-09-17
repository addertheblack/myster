package com.myster.thumbnail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;

/**
 * Shared desktop cache first; a detected Thumbnailer1 service may populate cache misses.
 * Cache reads can overlap, but service generation runs one request at a time.
 * Logs service errors separately from timeouts and completion without a usable cache entry.
 */
final class LinuxThumbnailProvider implements ThumbnailProvider {
    private static final Logger log = Logger.getLogger(LinuxThumbnailProvider.class.getName());
    private static final String THUMBNAILER_SERVICE_NAME = "org.freedesktop.thumbnails.Thumbnailer1";
    private static final String THUMBNAILER_INTERFACE_NAME = "org.freedesktop.thumbnails.Thumbnailer1";
    private static final String THUMBNAILER_OBJECT_PATH = "/org/freedesktop/thumbnails/Thumbnailer1";
    private static final String DBUS_SERVICE_NAME = "org.freedesktop.DBus";
    private static final String DBUS_OBJECT_PATH = "/org/freedesktop/DBus";
    private static final String XDG_CACHE_HOME_ENV_VAR = "XDG_CACHE_HOME";
    private static final String DBUS_SESSION_BUS_ADDRESS_ENV_VAR = "DBUS_SESSION_BUS_ADDRESS";
    private static final String USER_HOME_PROPERTY = "user.home";
    private static final String DEFAULT_CACHE_DIRECTORY = ".cache";
    private static final String THUMBNAIL_CACHE_DIRECTORY = "thumbnails";
    private static final String DEFAULT_THUMBNAIL_SCHEDULER = "default";
    private static final int MAX_CONCURRENT_GENERATION_REQUESTS = 1;
    private static final int GENERATION_TIMEOUT_SECONDS = 15;
    // Each request owns and closes its connection.
    private static final boolean SHARE_DBUS_CONNECTIONS = false;
    /** Queue's zero handle means this request does not cancel any earlier request. */
    private static final UInt32 NO_PREVIOUS_REQUEST_HANDLE = new UInt32(0);
    private final FreedesktopThumbnailCache cache = new FreedesktopThumbnailCache(cacheRoot());
    private final Semaphore generationPermit = new Semaphore(MAX_CONCURRENT_GENERATION_REQUESTS);

    private static Path cacheRoot() {
        String xdg = System.getenv(XDG_CACHE_HOME_ENV_VAR);
        Path base = xdg == null || xdg.isBlank() ? null : Path.of(xdg);
        if (base == null || !base.isAbsolute()) {
            base = Path.of(System.getProperty(USER_HOME_PROPERTY), DEFAULT_CACHE_DIRECTORY);
        }
        return base.resolve(THUMBNAIL_CACHE_DIRECTORY);
    }

    @Override
    public Optional<BufferedImage> load(Path file, int size) throws IOException, InterruptedException {
        BufferedImage image = cache.load(file, size);
        if (image != null) {
            return Optional.of(image);
        }
        // Tumbler 4.18 can misroute Ready signals and finish concurrent queues without
        // generating their files. Serialize our service requests; cache hits stay parallel.
        generationPermit.acquire();
        try {
            image = cache.load(file, size);
            return image != null ? Optional.of(image) : generate(file, size);
        } finally {
            generationPermit.release();
        }
    }

    private Optional<BufferedImage> generate(Path file, int size) throws IOException, InterruptedException {
        String address = System.getenv(DBUS_SESSION_BUS_ADDRESS_ENV_VAR);
        if (address == null || address.isBlank()) {
            log.info("Linux thumbnail service unavailable: no session D-Bus address");
            return Optional.empty();
        }
        try (DBusConnection connection = DBusConnectionBuilder.forAddress(address)
                .withShared(SHARE_DBUS_CONNECTIONS).build()) {
            DBus bus = connection.getRemoteObject(DBUS_SERVICE_NAME, DBUS_OBJECT_PATH, DBus.class);
            if (!bus.NameHasOwner(THUMBNAILER_SERVICE_NAME)
                    && !Arrays.asList(bus.ListActivatableNames()).contains(THUMBNAILER_SERVICE_NAME)) {
                log.info("Linux thumbnail service unavailable: " + THUMBNAILER_SERVICE_NAME);
                return Optional.empty();
            }
            Thumbnailer service = connection.getRemoteObject(THUMBNAILER_SERVICE_NAME,
                                                             THUMBNAILER_OBJECT_PATH,
                                                             Thumbnailer.class);
            String flavor = FreedesktopThumbnailCache.flavor(size);
            if (!Arrays.asList(service.GetFlavors()).contains(flavor)) {
                log.info("Linux thumbnail service does not support size flavor " + flavor);
                return Optional.empty();
            }
            String mime = Files.probeContentType(file);
            if (mime == null) {
                return Optional.empty();
            }
            String uri = file.toUri().toASCIIString();
            connection.addSigHandler(Thumbnailer.Error.class, service, signal -> {
                if (signal.uris.contains(uri)) {
                    log.info(() -> "Linux thumbnail generation failed: handle=" + signal.handle
                            + " size=" + size + " flavor=" + flavor + " code=" + signal.code
                            + " message=" + signal.message + " file=" + file);
                }
            });
            Set<UInt32> finished = new HashSet<>();
            connection.addSigHandler(Thumbnailer.Finished.class, service, signal -> {
                synchronized (finished) {
                    finished.add(signal.handle);
                    finished.notifyAll();
                }
            });
            log.info(() -> "Thumbnail request source=Linux D-Bus service=" + THUMBNAILER_SERVICE_NAME
                    + " size=" + size + " flavor=" + flavor + " file=" + file);
            UInt32 handle = service.Queue(new String[] { uri },
                                         new String[] { mime }, flavor, DEFAULT_THUMBNAIL_SCHEDULER,
                                         NO_PREVIOUS_REQUEST_HANDLE);
            boolean completed = false;
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GENERATION_TIMEOUT_SECONDS);
                synchronized (finished) {
                    while (!finished.contains(handle)) {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) {
                            log.info("Linux thumbnail service timed out: handle=" + handle
                                    + " size=" + size + " flavor=" + flavor + " file=" + file);
                            return Optional.empty();
                        }
                        TimeUnit.NANOSECONDS.timedWait(finished, remaining);
                    }
                }
                completed = true;
                BufferedImage image = cache.load(file, size);
                if (image == null) {
                    log.info(() -> "Linux thumbnail service finished without a usable cache entry: handle="
                            + handle + " size=" + size + " flavor=" + flavor + " file=" + file);
                }
                return Optional.ofNullable(image);
            } finally {
                if (!completed) {
                    try {
                        service.Dequeue(handle);
                    } catch (DBusExecutionException e) {
                        log.log(Level.FINE, "Cannot dequeue desktop thumbnail request", e);
                    }
                }
            }
        } catch (DBusException | DBusExecutionException e) {
            log.info("Linux thumbnail service unavailable: " + e.getMessage());
            log.log(Level.FINE, "D-Bus thumbnail failure", e);
            return Optional.empty();
        }
    }

    /** Public solely because dbus-java reflects on the proxy and signal constructors. */
    @DBusInterfaceName(THUMBNAILER_INTERFACE_NAME)
    public interface Thumbnailer extends DBusInterface {
        UInt32 Queue(String[] uris, String[] mimeTypes, String flavor, String scheduler, UInt32 previous);

        void Dequeue(UInt32 handle);

        String[] GetFlavors();

        /** Generation failed for the listed URIs; a Finished signal still ends the queue. */
        class Error extends DBusSignal {
            public final UInt32 handle;
            public final List<String> uris;
            public final int code;
            public final String message;

            public Error(String path, UInt32 handle, List<String> uris, int code, String message)
                    throws DBusException {
                super(path, handle, uris, code, message);
                this.handle = handle;
                this.uris = uris;
                this.code = code;
                this.message = message;
            }
        }

        /** A queue has finished processing, whether thumbnail generation succeeded or failed. */
        class Finished extends DBusSignal {
            public final UInt32 handle;

            public Finished(String path, UInt32 handle) throws DBusException {
                super(path, handle);
                this.handle = handle;
            }
        }
    }
}
