package com.myster.filemanager;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import com.myster.mml.MessagePak;

/**
 * Append-only, disk-backed metadata cache split across 256 shard logs.
 * <p>
 * Shards are stored under {@code cacheRoot/v2/<hex>.mlog}. Complete records are immediately
 * visible to a new cache instance, while recent records may still be in operating-system buffers
 * until {@link #flush()} or normal filesystem writeback. A torn final record preserves the valid
 * prefix; other corrupt or unreadable shard data is treated as an empty cache so indexing can
 * continue. Each entry stores its metadata type id and cache version, including negative entries,
 * and a version mismatch is a normal cache miss.
 * <p>
 * Superseded records, tombstones, and expired entries are removed by occasional atomic compaction.
 * One read/write lock protects each in-memory shard and its log operations. Multiple live cache
 * instances must not write the same cache root.
 */
public class ShardedFileMetadataCache implements FileMetadataCache {
    private static final Logger log = Logger.getLogger(ShardedFileMetadataCache.class.getName());
    private static final int SHARD_COUNT = 256;
    private static final Duration MAX_ENTRY_AGE = Duration.ofDays(183);
    private static final CompactionPolicy DEFAULT_COMPACTION_POLICY =
            new CompactionPolicy(100 * 1024, 2);

    private static final String OPERATION = "/operation";
    private static final String ENTRY_KEY = "/entryKey";
    private static final String METADATA_TYPE_ID = "/metadataTypeId";
    private static final String CACHE_VERSION = "/cacheVersion";
    private static final String PATH = "/path";
    private static final String SIZE = "/size";
    private static final String LAST_MODIFIED_MILLIS = "/lastModifiedMillis";
    private static final String CREATED_AT_MILLIS = "/createdAtMillis";
    private static final String METADATA_DIRECTORY = "/metadata/";

    private final Clock clock;
    private final CompactionPolicy compactionPolicy;
    private final Shard[] shards = new Shard[SHARD_COUNT];

    public ShardedFileMetadataCache(Path cacheRoot) {
        this(cacheRoot, Clock.systemUTC());
    }

    ShardedFileMetadataCache(Path cacheRoot, Clock clock) {
        this(cacheRoot, clock, DEFAULT_COMPACTION_POLICY);
    }

    ShardedFileMetadataCache(Path cacheRoot,
                             Clock clock,
                             CompactionPolicy compactionPolicy) {
        Path cacheDirectory = cacheRoot.resolve("v2");
        this.clock = Objects.requireNonNull(clock);
        this.compactionPolicy = Objects.requireNonNull(compactionPolicy);
        for (int i = 0; i < shards.length; i++) {
            String shardId = String.format("%02x", i);
            shards[i] = new Shard(new MetadataCacheLog(
                    cacheDirectory.resolve(shardId + ".mlog"), i));
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
                    shard.data = new SoftReference<>(loadShard(shard));
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
            MessagePak data = loadShardForWrite(shard);
            boolean existed = data.isADirectory(entryBase(key) + "/");
            long createdAtMillis = clock.millis();
            putEntry(data, key, metadata, createdAtMillis);
            if (!existed) {
                shard.liveEntryCount++;
            }

            if (append(shard, putRecord(key, metadata, createdAtMillis))) {
                maybeCompact(shard, data);
            }
        } finally {
            shard.lock.writeLock().unlock();
        }
    }

    @Override
    public void remove(FileMetadataCacheKey key) {
        Shard shard = shardFor(key);
        shard.lock.writeLock().lock();
        try {
            MessagePak data = loadShardForWrite(shard);
            if (!data.removeDir(entryBase(key) + "/")) {
                return;
            }
            shard.liveEntryCount--;

            if (append(shard, removeRecord(key))) {
                maybeCompact(shard, data);
            }
        } finally {
            shard.lock.writeLock().unlock();
        }
    }

    /**
     * Forces shard logs changed by this cache instance to their storage device.
     * <p>
     * This is a best-effort durability operation. It does not serialize in-memory data, compact
     * logs, or prevent concurrent mutations from remaining unforced after this method returns.
     */
    public void flush() {
        for (Shard shard : shards) {
            shard.lock.writeLock().lock();
            try {
                if (shard.writeGeneration == shard.forcedGeneration) {
                    continue;
                }
                try {
                    shard.cacheLog.force();
                    shard.forcedGeneration = shard.writeGeneration;
                } catch (IOException ex) {
                    log.warning("Could not force metadata cache shard "
                            + shard.cacheLog.path() + ": " + ex.getMessage());
                }
            } finally {
                shard.lock.writeLock().unlock();
            }
        }
    }

    // must have shard write lock before calling
    private MessagePak loadShardForWrite(Shard shard) {
        MessagePak data = shard.data.get();
        if (data == null) {
            data = loadShard(shard);
            shard.data = new SoftReference<>(data);
        }
        return data;
    }

    // must have shard write lock before calling
    private MessagePak loadShard(Shard shard) {
        resetReplayState(shard);
        MetadataCacheLog.ReplayResult replay;
        try {
            replay = shard.cacheLog.replay();
        } catch (IOException ex) {
            log.warning("Could not read metadata cache shard " + shard.cacheLog.path() + ": "
                    + ex.getMessage());
            return MessagePak.newEmpty();
        }

        if (replay.status() == MetadataCacheLog.ReplayStatus.INVALID) {
            log.warning("Ignoring invalid metadata cache shard " + shard.cacheLog.path() + ": "
                    + replay.detail());
            discardInvalidShard(shard);
            return MessagePak.newEmpty();
        }

        MessagePak data = MessagePak.newEmpty();
        for (MetadataCacheLog.Frame frame : replay.frames()) {
            if (!applyRecord(data, frame.body())) {
                log.warning("Ignoring metadata cache shard with malformed record: "
                        + shard.cacheLog.path());
                discardInvalidShard(shard);
                return MessagePak.newEmpty();
            }
        }

        shard.validLength = replay.validLength();
        shard.nextSequence = replay.nextSequence();
        shard.totalRecordCount = replay.frames().size();
        shard.liveEntryCount = liveEntryCount(data);

        boolean pruned = pruneExpiredEntries(data);
        shard.liveEntryCount = liveEntryCount(data);
        if (pruned) {
            compact(shard, data);
        } else if (replay.status() == MetadataCacheLog.ReplayStatus.TORN_TAIL) {
            try {
                shard.cacheLog.truncate(replay.validLength());
                shard.writeGeneration++;
            } catch (IOException ex) {
                log.warning("Could not truncate incomplete metadata cache record in "
                        + shard.cacheLog.path() + ": " + ex.getMessage());
            }
        }

        return data;
    }

    private void discardInvalidShard(Shard shard) {
        resetReplayState(shard);
        try {
            shard.cacheLog.discard();
        } catch (IOException ex) {
            log.warning("Could not discard invalid metadata cache shard "
                    + shard.cacheLog.path() + ": " + ex.getMessage());
        }
    }

    private static void resetReplayState(Shard shard) {
        shard.validLength = 0;
        shard.nextSequence = 1;
        shard.totalRecordCount = 0;
        shard.liveEntryCount = 0;
    }

    private boolean append(Shard shard, MessagePak record) {
        try {
            long newLength = shard.cacheLog.append(
                    shard.validLength, shard.nextSequence, record);
            shard.validLength = newLength;
            shard.nextSequence++;
            shard.totalRecordCount++;
            shard.writeGeneration++;
            return true;
        } catch (IOException ex) {
            log.warning("Could not append metadata cache shard " + shard.cacheLog.path() + ": "
                    + ex.getMessage());
            return false;
        }
    }

    private void maybeCompact(Shard shard, MessagePak data) {
        if (shard.validLength < compactionPolicy.minimumBytes()
                || shard.totalRecordCount
                        <= (long) shard.liveEntryCount * compactionPolicy.recordToLiveRatio()) {
            return;
        }
        compact(shard, data);
    }

    private void compact(Shard shard, MessagePak data) {
        pruneExpiredEntries(data);
        shard.liveEntryCount = liveEntryCount(data);
        List<MessagePak> records = currentPutRecords(data);
        try {
            MetadataCacheLog.RewriteResult result = shard.cacheLog.rewrite(records);
            shard.validLength = result.validLength();
            shard.nextSequence = result.nextSequence();
            shard.totalRecordCount = result.recordCount();
            shard.liveEntryCount = records.size();
            shard.writeGeneration++;
            shard.forcedGeneration = shard.writeGeneration;
        } catch (IOException ex) {
            log.warning("Could not compact metadata cache shard " + shard.cacheLog.path() + ": "
                    + ex.getMessage());
        }
    }

    private static MessagePak putRecord(FileMetadataCacheKey key,
                                        MessagePak metadata,
                                        long createdAtMillis) {
        MessagePak record = MessagePak.newEmpty();
        record.putString(OPERATION, "put");
        record.putString(ENTRY_KEY, key.entryKey());
        record.putString(METADATA_TYPE_ID, key.metadataTypeId());
        record.putInt(CACHE_VERSION, key.cacheVersion());
        record.putString(PATH, key.normalizedAbsolutePath());
        record.putLong(SIZE, key.size());
        record.putLong(LAST_MODIFIED_MILLIS, key.lastModifiedMillis());
        record.putLong(CREATED_AT_MILLIS, createdAtMillis);
        MessagePakTreeUtils.copyDirectory(metadata, "/", record, METADATA_DIRECTORY);
        return record;
    }

    private static MessagePak removeRecord(FileMetadataCacheKey key) {
        MessagePak record = MessagePak.newEmpty();
        record.putString(OPERATION, "remove");
        record.putString(ENTRY_KEY, key.entryKey());
        record.putString(METADATA_TYPE_ID, key.metadataTypeId());
        record.putInt(CACHE_VERSION, key.cacheVersion());
        return record;
    }

    private static void putEntry(MessagePak data,
                                 FileMetadataCacheKey key,
                                 MessagePak metadata,
                                 long createdAtMillis) {
        String base = entryBase(key);
        data.removeDir(base + "/");
        data.putString(base + METADATA_TYPE_ID, key.metadataTypeId());
        data.putInt(base + CACHE_VERSION, key.cacheVersion());
        data.putString(base + PATH, key.normalizedAbsolutePath());
        data.putLong(base + SIZE, key.size());
        data.putLong(base + LAST_MODIFIED_MILLIS, key.lastModifiedMillis());
        data.putLong(base + CREATED_AT_MILLIS, createdAtMillis);
        MessagePakTreeUtils.copyDirectory(metadata, "/", data, base + METADATA_DIRECTORY);
    }

    private static boolean applyRecord(MessagePak data, MessagePak record) {
        String operation = record.getString(OPERATION).orElse(null);
        String entryKey = record.getString(ENTRY_KEY).orElse(null);
        String metadataTypeId = record.getString(METADATA_TYPE_ID).orElse(null);
        int cacheVersion = record.getInt(CACHE_VERSION).orElse(0);
        if (!isEntryKey(entryKey)
                || metadataTypeId == null
                || metadataTypeId.isBlank()
                || cacheVersion <= 0) {
            return false;
        }

        String base = entryBase(entryKey);
        if ("remove".equals(operation)) {
            data.removeDir(base + "/");
            return true;
        }
        if (!"put".equals(operation)) {
            return false;
        }

        String normalizedPath = record.getString(PATH).orElse(null);
        Optional<Long> size = record.getLong(SIZE);
        Optional<Long> lastModifiedMillis = record.getLong(LAST_MODIFIED_MILLIS);
        Optional<Long> createdAtMillis = record.getLong(CREATED_AT_MILLIS);
        if (normalizedPath == null
                || normalizedPath.isBlank()
                || size.isEmpty()
                || size.get() < 0
                || lastModifiedMillis.isEmpty()
                || createdAtMillis.isEmpty()
                || record.isAValue("/metadata")) {
            return false;
        }

        data.removeDir(base + "/");
        data.putString(base + METADATA_TYPE_ID, metadataTypeId);
        data.putInt(base + CACHE_VERSION, cacheVersion);
        data.putString(base + PATH, normalizedPath);
        data.putLong(base + SIZE, size.get());
        data.putLong(base + LAST_MODIFIED_MILLIS, lastModifiedMillis.get());
        data.putLong(base + CREATED_AT_MILLIS, createdAtMillis.get());
        MessagePakTreeUtils.copyDirectory(record, METADATA_DIRECTORY, data,
                base + METADATA_DIRECTORY);
        return true;
    }

    private static List<MessagePak> currentPutRecords(MessagePak data) {
        if (!data.isADirectory("/entries/")) {
            return List.of();
        }
        List<MessagePak> records = new ArrayList<>();
        for (String entryKey : data.list("/entries/")) {
            String base = entryBase(entryKey);
            MessagePak record = MessagePak.newEmpty();
            record.putString(OPERATION, "put");
            record.putString(ENTRY_KEY, entryKey);
            record.putString(METADATA_TYPE_ID,
                    data.getString(base + METADATA_TYPE_ID).orElseThrow());
            record.putInt(CACHE_VERSION,
                    data.getInt(base + CACHE_VERSION).orElseThrow());
            record.putString(PATH, data.getString(base + PATH).orElseThrow());
            record.putLong(SIZE, data.getLong(base + SIZE).orElseThrow());
            record.putLong(LAST_MODIFIED_MILLIS,
                    data.getLong(base + LAST_MODIFIED_MILLIS).orElseThrow());
            record.putLong(CREATED_AT_MILLIS,
                    data.getLong(base + CREATED_AT_MILLIS).orElseThrow());
            MessagePakTreeUtils.copyDirectory(data, base + METADATA_DIRECTORY, record,
                    METADATA_DIRECTORY);
            records.add(record);
        }
        return records;
    }

    private Optional<MessagePak> readEntry(MessagePak data, FileMetadataCacheKey key) {
        String base = entryBase(key);
        if (!key.metadataTypeId().equals(data.getString(base + METADATA_TYPE_ID).orElse(null))
                || data.getInt(base + CACHE_VERSION).orElse(0) != key.cacheVersion()
                || !key.normalizedAbsolutePath().equals(data.getString(base + PATH).orElse(null))
                || data.getLong(base + SIZE).orElse(Long.MIN_VALUE) != key.size()
                || data.getLong(base + LAST_MODIFIED_MILLIS).orElse(Long.MIN_VALUE)
                        != key.lastModifiedMillis()) {
            return Optional.empty();
        }

        long createdAtMillis = data.getLong(base + CREATED_AT_MILLIS).orElse(Long.MIN_VALUE);
        if (isExpired(createdAtMillis)) {
            return Optional.empty();
        }

        MessagePak metadata = MessagePak.newEmpty();
        MessagePakTreeUtils.copyDirectory(data, base + METADATA_DIRECTORY, metadata, "/");
        return Optional.of(metadata);
    }

    private boolean pruneExpiredEntries(MessagePak data) {
        if (!data.isADirectory("/entries/")) {
            return false;
        }

        boolean changed = false;
        List<String> entryKeys = new ArrayList<>(data.list("/entries/"));
        for (String entryKey : entryKeys) {
            String base = entryBase(entryKey);
            long createdAtMillis = data.getLong(base + CREATED_AT_MILLIS)
                    .orElse(Long.MIN_VALUE);
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

    private static int liveEntryCount(MessagePak data) {
        return data.isADirectory("/entries/") ? data.list("/entries/").size() : 0;
    }

    private Shard shardFor(FileMetadataCacheKey key) {
        return shards[Integer.parseInt(key.shardId(), 16)];
    }

    private static boolean isEntryKey(String entryKey) {
        if (entryKey == null || entryKey.length() != 64) {
            return false;
        }
        for (int i = 0; i < entryKey.length(); i++) {
            char c = entryKey.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static String entryBase(FileMetadataCacheKey key) {
        return entryBase(key.entryKey());
    }

    private static String entryBase(String entryKey) {
        return "/entries/" + entryKey;
    }

    record CompactionPolicy(long minimumBytes, int recordToLiveRatio) {
        CompactionPolicy {
            if (minimumBytes < 0) {
                throw new IllegalArgumentException("minimumBytes must not be negative");
            }
            if (recordToLiveRatio < 1) {
                throw new IllegalArgumentException("recordToLiveRatio must be positive");
            }
        }
    }

    private static final class Shard {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final MetadataCacheLog cacheLog;
        SoftReference<MessagePak> data = new SoftReference<>(null);
        long validLength;
        long nextSequence = 1;
        long totalRecordCount;
        int liveEntryCount;
        long writeGeneration;
        long forcedGeneration;

        Shard(MetadataCacheLog cacheLog) {
            this.cacheLog = cacheLog;
        }
    }
}
