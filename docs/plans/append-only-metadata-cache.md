# Append-Only Metadata Cache

**Feature slug:** `append-only-metadata-cache`  
**Date:** 2026-08-22  
**Status:** Ready for implementation

## 1. Summary

Replace whole-shard metadata-cache rewrites with a framed, checksummed append-only log for each shard, and give every metadata type a numeric cache version stored in each operation record so format changes are isolated at the file level while metadata-field changes invalidate and rescan only records belonging to the affected type.

## 2. Non-goals

- Do not migrate or preserve the current `cacheRoot/v1/<hex>.mpak` whole-shard files; they are undeployed cache data and may be ignored.
- Do not provide compatibility between the old `MetadataType.cacheKey()` values such as `audio-v1` and the new stable-id plus numeric-version model.
- Do not make the cache authoritative storage or guarantee that the most recent unforced records survive power loss.
- Do not add cross-process shard locking or support multiple live cache instances writing the same root.
- Do not attempt to repair or salvage records after detected middle-of-file corruption.
- Do not add an embedded database, write-ahead database transaction layer, or cryptographic authentication.
- Do not scan and prune every entry on every `put`; ordinary writes must remain proportional to the new record size.
- Do not change metadata extraction fields, GUI columns, cache expiry duration, file identity validation, or negative-cache behavior in this milestone.

## 3. Assumptions & open questions

- Production has one `ShardedFileMetadataCache` for its metadata cache root and one in-process writer per shard. Existing `ReentrantReadWriteLock` instances continue to serialize shard mutation and append operations.
- A type's stable `MetadataType.id()` identifies the metadata profile. A new positive integer `cacheVersion()` identifies the cached representation produced by that profile. Implementations must increment it whenever newly required fields or changed extraction semantics make old cached results incomplete or incompatible.
- The initial built-in cache version is `1` for generic, audio, image, and video. Generic currently has no extractor, but assigning a valid version keeps the interface contract uniform.
- A cache-version mismatch is a normal cache miss, including for a stored negative result. `CachingFileMetadataExtractor` will then extract and append a replacement record through its existing miss path.
- The entry hash should remain stable across cache-version increments. It is derived from metadata type id plus normalized path; version is validated from the record instead of being embedded in the hash.
- The storage format uses a fixed binary envelope for reliable framing and record MessagePaks for extensible semantic fields. A separate MessagePak file-header record is unnecessary in the initial version because the fixed header contains only magic, storage format version, and shard identity.
- No unresolved question blocks implementation.

## 4. Proposed design

Each of the 256 shards becomes an append-only log under a new cache storage version. The file starts with fixed magic bytes, a storage format version, and the shard id. Each following frame contains record magic, a bounded body length, a monotonically increasing shard-local sequence number, one standalone MessagePak body, a CRC32C covering the framed values and body, and an end marker. The outer binary fields make boundaries and incomplete writes detectable; the MessagePak body keeps operation data extensible.

Every operation body carries an operation kind, entry key, metadata type id, and that type's numeric cache version. A PUT additionally carries normalized path, size, last-modified time, creation time, and the optional metadata subtree. A REMOVE is a tombstone and removes the entry key regardless of the version currently represented in memory. Replaying records in sequence reconstructs the current in-memory shard; the newest valid operation for an entry key wins.

Ordinary `put` and effective `remove` calls update the loaded in-memory shard and append one complete frame while holding the shard write lock. The frame is assembled in memory and written with a `FileChannel` loop. No whole-shard serialization, expiry scan, or forced disk synchronization occurs on the ordinary path. Same-instance reads retain their current immediate visibility, while a fresh cache instance can replay all complete records already visible through the filesystem.

Recovery follows a valid-prefix rule. Replay validates bounded lengths, sequence, end marker, CRC, and MessagePak contents before applying a record. An incomplete final record is ignored and the file is truncated to the last valid boundary before later appends. A bad file header, unsupported storage version, complete-record checksum failure, or sequence violation invalidates that shard and starts it empty. This intentionally treats old whole-shard MessagePak files as unsupported cache data rather than migrating them.

Append logs are compacted occasionally. Replay omits expired current entries and requests an immediate rewrite when pruning found any. Normal mutation tracks total record count, live entry count, and file size; once a minimum size and stale-record ratio are both exceeded, the shard is rebuilt from current live entries. Rebuild writes a new valid log to a temporary file, forces it, and atomically replaces the old log under the shard write lock. Superseded PUTs, tombstones, expired entries, and stale tail bytes disappear in that rewrite.

Successful appends mark a shard as containing unforced changes. A public best-effort `flush()` forces changed shard files without rewriting them and is registered with the existing application shutdown listener. There is no delayed-write queue or batching scheduler; each operation is appended immediately, and the operating system remains free to buffer physical disk writes.

## 5. Architecture connections

The cache remains behind `FileMetadataCache`, so metadata extraction and GUI code continue to see the same hit, miss, positive-entry, and negative-entry behavior. The long-lived API change is on `MetadataType`: the combined string namespace is replaced by a stable profile id plus an explicit numeric cache version. The storage implementation records both values with each operation.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `MetadataType.cacheVersion()` | each metadata profile implementation | `FileMetadataCacheKey.from` and cache record creation | Separates cached-metadata compatibility from stable `MetadataType.id()` subscriptions |
| Stable-id/version cache key | `FileMetadataCacheKey` | `CachingFileMetadataExtractor`, `ShardedFileMetadataCache` | Keeps shard/entry identity stable while carrying the expected per-type record version |
| Framed shard log | package-private metadata-cache log codec | `ShardedFileMetadataCache` load, append, repair, and compaction paths | Stores standalone MessagePak operation bodies with binary framing and CRC32C |
| Replayed shard state | `ShardedFileMetadataCache.Shard` | cache `get`, `put`, and `remove` | Replaces loading one whole-shard MessagePak while preserving the existing in-memory entry tree and locks |
| Best-effort force operation | `ShardedFileMetadataCache.flush()` | application shutdown and focused tests | Forces appended bytes without serializing or replacing the shard |
| Metadata-cache shutdown listener | `Myster.createFileMetadataExtractor` | `MysterGlobals.quit()` | Requests a final force for changed shard logs within the existing shutdown timeout |

Plain-English write flow: the selected `MetadataType` supplies its stable id and current cache version when the cache key is built. On a miss, extraction produces metadata as today. The cache creates one PUT MessagePak containing that id/version and file identity, frames it, appends it under the shard lock, and updates the same in-memory state used by readers. A future lookup supplies the then-current version; a record with a different version is a miss and therefore triggers extraction and a replacement PUT.

Plain-English startup flow: the first access to a shard validates the new file header, replays complete frames in sequence, and applies each PUT or REMOVE. A torn tail leaves the validated prefix usable and is truncated before append. Unsupported legacy storage is ignored. Expired current entries are omitted, and their discovery causes one compacted replacement rather than a full scan and rewrite on every subsequent mutation.

The new on-disk contract is:

- Cache location: `cacheRoot/v2/<two-hex-digit-shard>.mlog`.
- File header: fixed magic, unsigned storage format version, and numeric shard id. Multi-byte integers use big-endian encoding.
- Record envelope: fixed record magic, unsigned body length, signed 64-bit sequence, body bytes, CRC32C, and fixed end marker.
- Record body: exactly one MessagePak map with `/operation`, `/entryKey`, `/metadataTypeId`, and `/cacheVersion`; PUT bodies also contain `/path`, `/size`, `/lastModifiedMillis`, `/createdAtMillis`, and optional `/metadata/` contents.
- Body length is capped at 16 MiB before allocation or parsing. The CRC covers body length, sequence, and body bytes so damaged framing data cannot silently redirect parsing.
- Sequences start at `1`, increase by one within a file generation, and restart at `1` after compaction creates a replacement file.
- REMOVE bodies carry metadata type id and cache version like every other operation, but replay applies the tombstone by entry key regardless of version.

## 6. Key decisions & edge cases

- **Storage version and metadata version are independent:** file-header version changes invalidate the whole shard format. Record `cacheVersion` changes invalidate only lookups for one metadata type.
- **`cacheKey()` is removed rather than retained:** strings such as `audio-v1` duplicate id and version and cause entry hashes to change on every schema bump. `id()` plus `cacheVersion()` makes the invalidation rule explicit.
- **Version mismatch does not delete eagerly:** it returns a miss. The normal extraction followed by PUT replaces the entry in memory and appends the current version. This avoids a separate invalidation write before the replacement.
- **Negative results are versioned:** an empty metadata payload is reusable only when its record version matches the current metadata type version. Adding extraction support and incrementing the version rescans formerly unsupported files.
- **Frames, not raw concatenated MessagePaks, define boundaries:** MessagePack values are self-delimiting, but explicit length, CRC, sequence, and end marker provide bounded allocation and deterministic torn-write detection.
- **A record is applied only after full validation:** incomplete or corrupt payloads never partially mutate replayed state.
- **Tail damage and middle corruption have different policy:** EOF during the last frame preserves and truncates to the valid prefix. A complete bad frame, sequence break, invalid header, or unsupported file version discards the shard because later operations may have superseded valid-prefix state.
- **Append is not assumed atomic:** the implementation loops until the complete frame is written. Its safety comes from valid-prefix replay, not from one operating-system write call.
- **A failed append retains in-memory behavior:** as with the current cache's swallowed write failure, indexing continues and same-instance reads may see the value. The shard remembers the prior valid length so a later append truncates any failed tail first. Restart may miss the unpersisted value and extract it again.
- **No per-record force:** forcing every append would trade the current write-amplification problem for synchronous durability latency. Abrupt power loss may lose recent records, but must not invalidate an older forced/complete prefix.
- **Compaction is the only ordinary whole-shard rewrite:** it runs under the shard write lock, writes a complete temporary log, forces it, and uses atomic replacement where supported. Opening the channel per append avoids writing to an old inode after replacement and avoids retaining 256 descriptors.
- **Pruning is amortized:** expiry is checked while replaying, on direct lookup, and during compaction. It is not a full-shard operation in every PUT. Discovery of expired current entries marks or performs one compaction.
- **Compaction threshold is bounded and testable:** compact after the log is at least 100 KiB and physical record count is greater than twice the live entry count, or immediately after replay pruning discovers expired current entries. Constants remain implementation details, not user preferences.
- **Memory-pressure behavior remains:** the replayed MessagePak can remain behind the existing soft reference. Scalar replay state such as valid length, next sequence, counts, and force generation remains on the `Shard` object so a reclaimed map can be reconstructed safely.
- **Legacy data is disposable:** `v1` shard files are neither read nor migrated. Invalid files encountered under `v2` are logged and reset without surfacing exceptions to indexing callers.
- **Shutdown force is best effort:** `MysterGlobals.quit()` allows only one second for all listeners and documents exits that bypass it. Correctness cannot depend on this callback.

## 7. Acceptance criteria

- [ ] Ordinary PUT and REMOVE operations append one bounded record instead of serializing the accumulated shard.
- [ ] Existing `v1` whole-shard cache files are ignored without migration or indexing failure.
- [ ] Every PUT and REMOVE record stores metadata type id and a positive per-type cache version.
- [ ] Built-in metadata types declare cache version `1`, and the combined string `cacheKey()` API is removed.
- [ ] Incrementing one metadata type's cache version makes its older positive and negative records miss while records for other metadata types remain usable.
- [ ] A current-version PUT after a version miss replaces the logical entry without changing its entry key or shard.
- [ ] Same-instance reads observe successful mutations immediately, and fresh instances replay complete appended records.
- [ ] A truncated final frame preserves all earlier complete records and is removed before another append.
- [ ] Invalid length, CRC, end marker, sequence, file magic, or storage version cannot partially apply a record or cause unbounded allocation.
- [ ] Complete-record or middle-file corruption invalidates only that shard and does not prevent indexing.
- [ ] Negative cache entries remain distinct from misses and participate in cache-version validation.
- [ ] Expired entries remain misses and are removed by a compacted rewrite without a whole-shard expiry scan on every PUT.
- [ ] Superseded records and tombstones are removed once the compaction threshold is reached.
- [ ] Compaction preserves the latest live value for every entry and leaves either the old or new valid file across replacement failure.
- [ ] Concurrent same-shard writers cannot interleave frames; different shards retain independent locking.
- [ ] `flush()` forces changed logs without rewriting them, and production shutdown invokes it on a best-effort basis.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- `src/main/java/com/myster/filemanager/MetadataType.java` - replace `cacheKey()` with positive numeric `cacheVersion()` and document bump rules.
- `src/main/java/com/myster/filemanager/BuiltInMetadataType.java` - store cache version separately from stable id; initialize all built-ins at version `1`.
- `src/main/java/com/myster/filemanager/FileMetadataCacheKey.java` - carry metadata type id and expected cache version while hashing only stable id plus normalized path.
- `src/main/java/com/myster/filemanager/MetadataCacheLog.java` - **NEW**, package-private framed-file codec for header validation, record append/replay, tail recovery, force, and atomic replacement.
- `src/main/java/com/myster/filemanager/ShardedFileMetadataCache.java` - replace whole-map persistence with log replay, per-operation append, version validation, replay statistics, pruning, compaction, and public `flush()`.
- `src/main/java/com/myster/Myster.java` - register the concrete cache's best-effort force operation with `MysterGlobals` shutdown.
- `src/test/java/com/myster/filemanager/TestFileMetadataCacheKey.java` - verify stable entry identity and explicit version carriage.
- `src/test/java/com/myster/filemanager/TestMetadataCacheLog.java` - **NEW**, verify binary framing, bounded parsing, replay, corruption handling, truncation, append, force, and replacement.
- `src/test/java/com/myster/filemanager/TestShardedFileMetadataCache.java` - replace whole-shard file assertions and add end-to-end versioning, append, replay, recovery, pruning, compaction, and concurrency coverage.
- `src/test/java/com/myster/filemanager/TestDefaultMetadataTypeRegistry.java` - verify built-in ids remain stable and cache versions are positive/current.

### 9. Step-by-step implementation

1. Separate profile identity from cache compatibility:
   - Replace `MetadataType.cacheKey()` with `int cacheVersion()`.
   - Document that ids remain stable subscription identifiers and versions must be positive and increment whenever previously cached positive or negative results may lack newly expected data.
   - Change `BuiltInMetadataType` constructor fields from `(id, cacheKey, cacheableKeys)` to `(id, cacheVersion, cacheableKeys)` and assign version `1` to every built-in.
   - Do not derive versions by parsing strings.

2. Refactor `FileMetadataCacheKey`:
   - Store `metadataTypeId` and `cacheVersion` as separate fields with accessors of those names.
   - In `from(MetadataType, Path, long)`, copy `id()` and `cacheVersion()` and reject a non-positive version.
   - Hash `metadataTypeId + "\n" + normalizedPath` for `entryKey`; deliberately omit cache version, size, and mtime.
   - Include id and version in equality/hash code because they describe the lookup expectation, while preserving entry-key and shard stability across version bumps.
   - Add a package-private factory accepting explicit id/version for focused version-transition tests without implementing a complete fake metadata profile.

3. Implement `MetadataCacheLog` as the storage boundary:
   - Define fixed file magic, `STORAGE_FORMAT_VERSION`, record magic, end marker, header sizes, and `MAX_RECORD_BYTES = 16 * 1024 * 1024` in one class.
   - Use `DataOutputStream` or `ByteBuffer` only for the fixed big-endian envelope and `MessagePak.toBytes()`/`fromBytes()` for record bodies; do not hand-build MessagePack.
   - Represent decoded frames with a package-private immutable record containing sequence, body, start offset, and end offset.
   - Represent replay output with decoded records, last valid offset, next sequence, total record count, and a recovery disposition: valid, torn tail, or invalid shard.
   - Validate file magic/version/shard before reading frames. Validate length bounds before allocating, require contiguous sequence numbers, read every field fully, verify end marker and CRC32C, and parse the body before returning a frame.
   - Classify EOF before a final frame completes as torn tail. Classify a complete bad CRC/end marker, sequence violation, bad body, bad header, or unsupported storage version as invalid shard.
   - `append(...)` must open the file under the caller's shard lock, create/write the file header when empty, truncate to the caller's known valid offset, position there, and loop until the prebuilt frame buffer is exhausted. Return the new valid offset only after the full write succeeds.
   - `rewrite(...)` must write a new header and compacted frames to a same-directory temporary file, call `force(true)`, then atomically replace the shard with `REPLACE_EXISTING`; retain the current non-atomic fallback and temp cleanup behavior.
   - `force(...)` opens an existing shard for write and calls `FileChannel.force(false)`.

4. Define and validate operation MessagePaks in `ShardedFileMetadataCache`:
   - Use operation values `put` and `remove` and the exact paths documented in the on-disk contract.
   - PUT writes entry key, metadata type id, positive cache version, normalized path, size, mtime, creation time, and optional metadata directory.
   - REMOVE writes operation, entry key, metadata type id, and positive cache version. Replay removes by entry key after validating all required fields.
   - Reject malformed operations without partially applying them. Treat a syntactically framed but semantically malformed record as an invalid shard, not as a skippable operation.
   - Keep a present empty `/metadata/` result distinguishable as a valid negative entry; absence of profile fields must not turn the record into a cache miss.

5. Replace shard loading with replay:
   - Expand `Shard` with soft-referenced in-memory data plus valid length, next sequence, total record count, live entry count, and append/force generation counters.
   - On first load, call `MetadataCacheLog.replay`, apply records in order to a new MessagePak entry tree, and update scalar state.
   - If the file is absent or has an unsupported legacy/header format, initialize empty state. Delete or replace the invalid file before the next append; do not attempt MessagePak whole-file migration.
   - If replay reports a torn tail, preserve replayed state and truncate to the returned valid offset before append. A package-private repair method may truncate immediately while already under the shard write lock.
   - For each PUT, replace the logical entry at `/entries/<entryKey>/`; for each REMOVE, remove it. Track whether each append supersedes an older live record for compaction accounting.
   - During replay, apply order first, then remove current entries whose creation time is invalid or older than `MAX_ENTRY_AGE`. If any current entries are pruned, rewrite the live state once before releasing the loaded shard.

6. Convert cache operations to append:
   - `get` keeps the current read-lock/upgrade-to-load loop. `readEntry` additionally requires record metadata type id and cache version to equal the key's expected values.
   - A version mismatch returns `Optional.empty()`; do not append a tombstone because `CachingFileMetadataExtractor` will normally follow the miss with a replacement PUT.
   - `put` loads under the shard write lock, mutates the in-memory entry, builds one PUT body, appends one frame at `nextSequence`, and updates scalar counters/offset only after success.
   - `remove` builds one REMOVE only when the logical entry existed, removes it from memory, and attempts the append. Maintain the current best-effort persistence boundary: log and continue indexing if append fails, while retaining immediate in-memory behavior.
   - Before every append, truncate to the last known valid offset. This removes a partial frame left by an earlier failed append in the same process.
   - Mark successful appends as unforced by incrementing the shard write generation.

7. Add bounded compaction:
   - Add package-private constants for the 100 KiB (`100 * 1024` bytes) minimum physical size and 2:1 total-record-to-live-entry ratio.
   - After a successful mutation, compact only when both threshold conditions hold. Do not list or scan all entries unless compaction is actually selected.
   - Also compact once after load when replay pruning removed expired current entries.
   - Build one PUT body per current live entry, preserving its stored metadata type id and cache version, and omit tombstones and superseded records.
   - Rewrite with fresh contiguous sequences starting at `1`; only swap in the new scalar state after atomic replacement succeeds. On failure, retain the old log state and continue serving the in-memory cache.

8. Add force-on-shutdown without delayed batching:
   - Add public `ShardedFileMetadataCache.flush()` that visits only shards whose write generation exceeds their forced generation.
   - For each such shard, take its write lock, force the current file, and advance the forced generation only on success. Do not serialize MessagePak or compact during `flush()`.
   - In `Myster.createFileMetadataExtractor`, keep the concrete `ShardedFileMetadataCache` reference long enough to register `cache::flush` with `MysterGlobals.addShutdownListener`, then pass it to `CachingFileMetadataExtractor` through the existing interface.
   - Do not introduce a scheduler or retain open channels.

9. Remove obsolete whole-shard code and assumptions:
   - Remove `SCHEMA_VERSION`, `loadShard` whole-file `MessagePak.fromBytes`, `writeShard`, and per-PUT `pruneExpiredEntries` persistence.
   - Change test helpers that parse `v1/<shard>.mpak` directly to replay `v2/<shard>.mlog` through the package-private codec or a fresh cache instance.
   - Keep corrupt storage as a logged cache miss rather than propagating to extraction callers.

10. Run verification:
   - Run `mvn -q -DskipTests test-compile`.
   - Run `mvn -q -Djava.awt.headless=true -Dtest=TestMetadataCacheLog,TestShardedFileMetadataCache,TestFileMetadataCacheKey,TestCachingFileMetadataExtractor,TestDefaultMetadataTypeRegistry test`.
   - Run the full headless Maven test suite and document pre-existing environmental failures separately from regressions.

### 10. Tests to write

- `TestFileMetadataCacheKey`
  - Stable id and path produce the same entry key/shard across cache versions.
  - Different metadata type ids still produce different entry keys.
  - Expected cache version participates in key equality but not disk identity.
  - Non-positive profile versions are rejected.
- `TestDefaultMetadataTypeRegistry`
  - Generic, audio, image, and video retain their stable ids and expose cache version `1`.
- `TestMetadataCacheLog`
  - New file contains the expected header, shard id, and first framed record.
  - Multiple record MessagePaks append and replay in sequence without changing the preceding file prefix.
  - CRC covers sequence, length, and body; changing any covered byte rejects the frame.
  - Body lengths below zero when interpreted as signed, above 16 MiB, or beyond remaining bytes are rejected before allocation.
  - Truncating the final frame at representative points returns the earlier valid prefix and exact repair offset.
  - Appending after torn-tail recovery truncates the invalid suffix and produces a fully replayable file.
  - Wrong magic, unsupported storage version, mismatched shard id, complete bad CRC/end marker, malformed MessagePak, and sequence gaps invalidate the shard.
  - Rewrite creates a valid compact file, restarts sequence numbering, and preserves the old file if replacement fails.
- `TestShardedFileMetadataCache`
  - PUT is immediately readable and survives replay through a new cache instance.
  - A second PUT to the same entry appends bytes and the newest value wins.
  - REMOVE appends a tombstone and remains removed after replay.
  - Empty metadata survives as a present negative cache hit.
  - A cache version increment turns both positive and negative old records into misses.
  - A replacement PUT at the new version keeps the same entry key and restores a hit for the new version.
  - Version changes for audio do not invalidate image records sharing the same physical shard.
  - Size and mtime mismatches remain misses.
  - Existing `v1` whole-shard data is ignored without throwing.
  - Torn final writes preserve previous records and are repaired before the next PUT.
  - Complete corruption resets only the affected shard.
  - Expired latest entries miss and a replay-triggered compaction removes them physically.
  - Compaction removes superseded PUTs and tombstones while preserving every latest live value and record cache version.
  - Same-shard concurrent PUTs produce complete non-interleaved frames; different-shard writes remain independent.
  - A simulated append failure leaves immediate in-memory state usable and the next append truncates the failed tail.
  - `flush()` forces only changed shard generations and does not rewrite file contents.

Use deterministic byte truncation and corruption in temporary files rather than process-kill or timing tests. Where production thresholds would make compaction fixtures large, expose a package-private constructor/config record for thresholds instead of weakening the production values or relying on timing.

### 11. Docs / Javadoc to update

- Update `MetadataType` Javadoc to distinguish stable subscription id, per-type cache version, cacheable keys, and the exact reasons to increment the version.
- Update `FileMetadataCacheKey` Javadoc to explain that disk identity is stable across cache versions while lookup compatibility is not.
- Replace `ShardedFileMetadataCache` class Javadoc with the append-log location, crash-recovery model, version-miss behavior, compaction policy, negative-entry semantics, and best-effort durability boundary.
- Add package/class documentation to `MetadataCacheLog` describing the binary envelope, maximum record size, CRC coverage, corruption classifications, and caller-held locking requirement.
- Add Javadoc to `ShardedFileMetadataCache.flush()` clarifying that it forces appended bytes but does not rewrite, compact, or guarantee survival when application shutdown bypasses listeners.
- Add concise comments only around valid-offset updates, failed-tail truncation, and compaction state replacement, where ordering is essential to correctness.
