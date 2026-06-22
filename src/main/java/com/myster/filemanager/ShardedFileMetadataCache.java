package com.myster.filemanager;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.myster.mml.MessagePak;

/**
 * Disk-backed metadata cache split across 256 MessagePak shard files.
 * <p>
 * Shards are stored under {@code cacheRoot/v1/<hex>.mpak}. Each shard uses a
 * read/write lock: loaded cache-hit readers can proceed together, while shard
 * load, update, prune, and file replacement are exclusive. Corrupt or unreadable
 * shard files are treated as empty so indexing can continue.
 */
public class ShardedFileMetadataCache implements FileMetadataCache {
    private static final Logger log = Logger.getLogger(ShardedFileMetadataCache.class.getName());
    private static final int SHARD_COUNT = 256;
    private static final int SCHEMA_VERSION = 1;
    private static final Duration MAX_ENTRY_AGE = Duration.ofDays(183);

    private final Path cacheDirectory;
    private final Clock clock;
    private final Shard[] shards = new Shard[SHARD_COUNT];

    public ShardedFileMetadataCache(Path cacheRoot) {
        this(cacheRoot, Clock.systemUTC());
    }

    ShardedFileMetadataCache(Path cacheRoot, Clock clock) {
        this.cacheDirectory = cacheRoot.resolve("v1");
        this.clock = clock;
        for (int i = 0; i < shards.length; i++) {
            shards[i] = new Shard();
        }
    }

    @Override
    public Optional<MessagePak> get(FileMetadataCacheKey key) {
        Shard shard = shardFor(key);
        while (true) {
            shard.lock.readLock().lock();
            try {
                MessagePak data = shard.data.get();
                if (data != null) {
                    return readEntry(data, key);
                }
            } finally {
                shard.lock.readLock().unlock();
            }

            shard.lock.writeLock().lock();
            try {
                if (shard.data.get() == null) {
                    shard.data = new SoftReference<>(loadShard(key.shardId()));
                }
            } finally {
                shard.lock.writeLock().unlock();
            }
        }
    }

    @Override
    public void put(FileMetadataCacheKey key, MessagePak metadata) {
        Shard shard = shardFor(key);
        shard.lock.writeLock().lock();
        try {
            MessagePak data = loadShardForWrite(shard, key.shardId());
            pruneExpiredEntries(data);

            String base = entryBase(key);
            data.removeDir(base + "/");
            data.putString(base + "/path", key.normalizedAbsolutePath());
            data.putString(base + "/metadataType", key.metadataType());
            data.putLong(base + "/size", key.size());
            data.putLong(base + "/lastModifiedMillis", key.lastModifiedMillis());
            data.putLong(base + "/createdAtMillis", clock.millis());
            MessagePakTreeUtils.copyDirectory(metadata, "/", data, base + "/metadata/");

            writeShard(key.shardId(), data);
        } finally {
            shard.lock.writeLock().unlock();
        }
    }

    @Override
    public void remove(FileMetadataCacheKey key) {
        Shard shard = shardFor(key);
        shard.lock.writeLock().lock();
        try {
            MessagePak data = loadShardForWrite(shard, key.shardId());
            boolean changed = pruneExpiredEntries(data);
            if (data.removeDir(entryBase(key) + "/")) {
                changed = true;
            }
            if (changed) {
                writeShard(key.shardId(), data);
            }
        } finally {
            shard.lock.writeLock().unlock();
        }
    }

    // must have write lock before calling
    private MessagePak loadShardForWrite(Shard shard, String shardId) {
        MessagePak data = shard.data.get();
        if (data == null) {
            data = loadShard(shardId);
            shard.data = new SoftReference<>(data);
        }
        return data;
    }

    private MessagePak loadShard(String shardId) {
        Path shardFile = shardFile(shardId);
        if (!Files.exists(shardFile)) {
            MessagePak data = MessagePak.newEmpty();
            data.putInt("/schemaVersion", SCHEMA_VERSION);
            return data;
        }

        try {
            MessagePak data = MessagePak.fromBytes(Files.readAllBytes(shardFile));
            data.putInt("/schemaVersion", SCHEMA_VERSION);
            return data;
        } catch (IOException ex) {
            log.warning("Ignoring corrupt metadata cache shard: " + shardFile + " - "
                    + ex.getMessage());
            MessagePak data = MessagePak.newEmpty();
            data.putInt("/schemaVersion", SCHEMA_VERSION);
            return data;
        }
    }

    private void writeShard(String shardId, MessagePak data) {
        Path tempFile = null;
        try {
            Files.createDirectories(cacheDirectory);
            tempFile = Files.createTempFile(cacheDirectory, shardId + "-", ".tmp");
            Files.write(tempFile, data.toBytes());
            Path shardFile = shardFile(shardId);
            try {
                Files.move(tempFile, shardFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile, shardFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            log.warning("Could not write metadata cache shard " + shardId + ": "
                    + ex.getMessage());
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException deleteEx) {
                    log.fine("Could not delete metadata cache temp file: " + tempFile + " - "
                            + deleteEx.getMessage());
                }
            }
        }
    }

    private Optional<MessagePak> readEntry(MessagePak data, FileMetadataCacheKey key) {
        String base = entryBase(key);
        if (!key.metadataType().equals(data.getString(base + "/metadataType").orElse(null))
                || !key.normalizedAbsolutePath().equals(data.getString(base + "/path").orElse(null))
                || data.getLong(base + "/size").orElse(Long.MIN_VALUE) != key.size()
                || data.getLong(base + "/lastModifiedMillis").orElse(Long.MIN_VALUE)
                        != key.lastModifiedMillis()) {
            return Optional.empty();
        }

        long createdAtMillis = data.getLong(base + "/createdAtMillis").orElse(Long.MIN_VALUE);
        if (isExpired(createdAtMillis)) {
            return Optional.empty();
        }

        MessagePak metadata = MessagePak.newEmpty();
        MessagePakTreeUtils.copyDirectory(data, base + "/metadata/", metadata, "/");
        if (MessagePakTreeUtils.isEmpty(metadata)) {
            return Optional.empty();
        }

        return Optional.of(metadata);
    }

    private boolean pruneExpiredEntries(MessagePak data) {
        if (!data.isADirectory("/entries/")) {
            return false;
        }

        boolean changed = false;
        List<String> entryKeys = new ArrayList<>(data.list("/entries/"));
        for (String entryKey : entryKeys) {
            String base = "/entries/" + entryKey;
            long createdAtMillis = data.getLong(base + "/createdAtMillis").orElse(Long.MIN_VALUE);
            if (isExpired(createdAtMillis) && data.removeDir(base + "/")) {
                changed = true;
            }
        }

        return changed;
    }

    private boolean isExpired(long createdAtMillis) {
        return createdAtMillis == Long.MIN_VALUE
                || clock.millis() - createdAtMillis > MAX_ENTRY_AGE.toMillis();
    }

    private Shard shardFor(FileMetadataCacheKey key) {
        return shards[Integer.parseInt(key.shardId(), 16)];
    }

    private Path shardFile(String shardId) {
        return cacheDirectory.resolve(shardId + ".mpak");
    }

    private static String entryBase(FileMetadataCacheKey key) {
        return "/entries/" + key.entryKey();
    }

    private static final class Shard {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        SoftReference<MessagePak> data = new SoftReference<>(null);
    }

}
