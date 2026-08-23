# Append-Only Metadata Cache Implementation Summary

## What was implemented

Replaced whole-shard metadata-cache rewrites with 256 framed append-only shard logs. Each PUT and
REMOVE is a standalone MessagePak record protected by bounded binary framing, contiguous sequence
numbers, CRC32C, and an end marker. Replay recovers complete prefixes after torn final records,
rejects corrupt shards, prunes expired state, and periodically compacts logs through a forced
temporary file and atomic replacement.

Metadata profiles now expose a positive numeric `cacheVersion()` separately from their stable id.
Every operation record stores both values. Incrementing one profile's version therefore causes its
old positive and negative entries to miss and be re-extracted without changing their entry hashes,
shards, subscriptions, or unrelated metadata types.

## Files changed

- `src/main/java/com/myster/filemanager/MetadataType.java` - replaces combined string cache keys
  with explicit per-profile numeric cache versions and bump rules.
- `src/main/java/com/myster/filemanager/BuiltInMetadataType.java` - assigns cache version `1` to
  generic, audio, image, and video.
- `src/main/java/com/myster/filemanager/FileMetadataCacheKey.java` - carries stable metadata type id
  and expected cache version separately while hashing only id and normalized path.
- `src/main/java/com/myster/filemanager/MetadataCacheLog.java` - new package-private framed log
  codec for append, replay, valid-prefix recovery, force, and atomic rewrite.
- `src/main/java/com/myster/filemanager/ShardedFileMetadataCache.java` - replays and appends operation
  records, validates per-type versions, compacts stale/expired state, and forces changed logs.
- `src/main/java/com/myster/Myster.java` - registers best-effort metadata-cache forcing with the
  existing application shutdown listener.
- `src/test/java/com/myster/filemanager/TestMetadataCacheLog.java` - new byte-level framing,
  corruption, tail recovery, and rewrite tests.
- `src/test/java/com/myster/filemanager/TestShardedFileMetadataCache.java` - append/replay,
  versioning, expiry, negative cache, repair, compaction, flush, and concurrency tests.
- `src/test/java/com/myster/filemanager/TestFileMetadataCacheKey.java` and
  `TestDefaultMetadataTypeRegistry.java` - stable identity and built-in version tests.

## Key decisions

- Storage format version and metadata profile cache version are independent. Shard files live under
  `cacheRoot/v2`, while every operation body stores `/metadataTypeId` and `/cacheVersion`.
- Entry hashes omit cache version. A new version replaces the same logical path entry instead of
  leaving another key in another shard.
- Ordinary mutations append one record and do not scan, serialize, compact, or force an entire
  shard. Channels are opened per operation so compaction can replace files safely on all platforms.
- An incomplete final frame preserves the validated prefix. Invalid header, complete bad frame,
  sequence break, checksum failure, or malformed operation body invalidates only that shard.
- Every append truncates to the last validated offset first, preventing a failed prior tail from
  hiding future records.
- Compaction starts at 100 KiB only when physical record count exceeds twice the live entry count.
  Discovery of expired current entries during replay also compacts immediately.
- Appends rely on operating-system buffering. `flush()` forces changed shard files without rewriting
  them and is invoked on orderly shutdown, but abrupt exits may lose recent cache records.
- Existing `v1` whole-MessagePak shard files are ignored and not migrated.

## Deviations from the plan

- No production failure-injection interface was added for partial `FileChannel` writes or atomic
  replacement failures. Tests create deterministic torn tails and byte corruption directly, which
  exercises the same replay and repair boundaries without adding a test-only storage abstraction.
- No periodic force scheduler was added. The plan selected immediate append plus best-effort
  shutdown forcing, avoiding the obsolete retained-shard batching design.

## Javadoc and design docs

- Updated `MetadataType`, `FileMetadataCacheKey`, and `ShardedFileMetadataCache` contracts.
- Added a byte-offset file-format specification to `MetadataCacheLog` Javadoc covering header and
  frame layouts, magic values, byte order, checksum input, MessagePak operation fields, version
  semantics, recovery classification, and locking.
- Existing files under `docs/design/` do not describe the metadata cache, so no design document
  required an update.

## Verification

- Passed: `mvn -q -DskipTests test-compile`
- Repository-wide `mvn -q -DskipTests javadoc:javadoc` still fails on existing malformed Javadoc in
  unrelated classes; it reported no diagnostics for `MetadataCacheLog`.
- Passed focused suite:
  `mvn -q -Djava.awt.headless=true -Dtest=TestMetadataCacheLog,TestShardedFileMetadataCache,TestFileMetadataCacheKey,TestCachingFileMetadataExtractor,TestDefaultMetadataTypeRegistry test`
- Passed: `mvn -q -Djava.awt.headless=true test`
- The full run produced 83 Surefire XML reports with no failures or errors. One existing test printed
  an expected `IllegalStateException: broken` stack trace while asserting its failure path.
- `git diff --check` passes.

## Known issues and follow-up

- A successful in-memory mutation whose append fails remains visible in that cache instance but may
  be absent after restart. This matches the cache's previous best-effort write-failure behavior;
  metadata extraction reconstructs missing entries.
- Middle-of-file corruption discards one shard rather than attempting record resynchronization.
  Normal interrupted writes affect only the final frame and use valid-prefix recovery.
- Shutdown forcing is constrained by `MysterGlobals.quit()`'s shared one-second listener timeout and
  does not run on every platform exit path.
