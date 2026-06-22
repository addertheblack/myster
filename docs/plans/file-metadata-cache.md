# File Metadata Cache for Expensive Media Parsing

**Feature slug:** `file-metadata-cache`  
**Date:** 2026-06-19  
**Status:** Ready for implementation

---

## 1. Summary

Add an injected, file-backed metadata cache for expensive type-specific file metadata extraction,
starting with `MPG3FileItem`, so Myster does not re-run slow Tika media parsing for unchanged
files on every indexing pass or application launch.

## 2. Non-goals

- Do not introduce a static singleton or global service locator.
- Do not remove Tika, reintroduce `mp3agic`, or replace MP3 parsing in this milestone.
- Do not change the wire/protocol metadata keys consumed by search and client browsing.
- Do not remove `MPG3FileItem`'s in-object `messagePackRepresentation` RAM cache. The new cache
  is a disk-backed metadata cache for cold starts and repeated indexing across app lifetimes.
- Do not cache generic `FileItem` metadata such as `/size`, `/path`, or file hashes.
- Do not add an embedded database dependency.
- Do not build a UI for cache inspection or clearing.

## 3. Assumptions & open questions

- Cache storage belongs under Myster private app data, not tmp and not shared user folders:
  `MysterGlobals.getPrivateDataPath()/MetadataCache/`.
- Myster already prevents multiple app instances for the same user, and different users use
  different private data directories. Therefore the cache only needs in-process synchronization,
  not cross-process file locking.
- Cache records are invalidated by file identity plus observed file state: normalized absolute
  path, metadata type/schema key, file size, and last-modified time. The metadata type, not the
  `MysterType`, determines which metadata fields are available and valid.
- Path-based identity is acceptable for v1. Moving a file causes a cache miss.
- The first version may use write-through shard replacement for each cache update. If initial
  indexing write amplification is measurable, a later milestone can add dirty shard batching.
- `MessagePak` is the preferred on-disk payload format because this is structured,
  forward-compatible metadata.
- Cache entries record their creation time and expire after six months, even if the file identity
  still matches, so stale cache data is eventually refreshed and entries for files that no longer
  exist are eventually garbage-collected when their shard is touched during normal cache access.
- The cache key should reuse file size already obtained by `FileItem`/the superclass path where
  practical, instead of performing an extra size stat solely for the cache.
- Use `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` when supported and fall back to
  `REPLACE_EXISTING` when the filesystem does not support atomic move. This protects against
  partially written shard files if Myster or the machine crashes mid-write; thread safety is still
  handled by per-shard read/write locking.

## 4. Proposed design

Introduce a small metadata-cache layer in `com.myster.filemanager` and inject it through the
existing app construction path. `Myster.java` owns one cache/provider graph for the app instance
and passes it into `FileTypeListManager`. `FileTypeListManager` passes it to every `FileTypeList`;
`FileTypeList.FileListIndexCall` passes the provider to file items that need expensive metadata,
starting with `MPG3FileItem`.

`MPG3FileItem` remains responsible for its in-object `messagePackRepresentation` RAM cache and for
generic `FileItem` metadata plus delegation to an injected top-level `MetadataProvider`. On the
first call, `MPG3FileItem` calls `super.getMessagePackRepresentation()`, which computes and writes
the generic `/size` field, then passes that same `MessagePak` to the provider. Later calls on the
same `MPG3FileItem` can return the in-memory `messagePackRepresentation` as they do today.

The production provider chain is layered:

```text
MPG3FileItem
  -> MetadataProvider
       CachingMetadataProvider
         -> FileMetadataCache
              ShardedFileMetadataCache
         -> MetadataProvider
              TypeResolvingMetadataProvider
                -> TypedMetadataProvider
                     TikaAudioMetadataProvider
```

The cache layer reads the already-computed `/size` value from the pack and checks the sharded
persistent cache before delegating. If a valid cache entry exists, it merges the cached
type-specific metadata into the current `MessagePak`. If no valid entry exists, it calls the next
`MetadataProvider` in the chain. The resolver provider finds the `TypedMetadataProvider` for the
requested `MetadataType` and lets that provider run the expensive extraction. The cache layer then
stores the extracted type-specific metadata and merges it into the current `MessagePak`.
Missing `/size` is a caller contract violation for `CachingMetadataProvider` and should throw an
`IllegalStateException`; the provider must not silently parse or cache metadata for an incomplete
base file payload. If the cache key cannot be built, log and return without delegating to the
extractor. Recoverable cache read problems belong inside the `FileMetadataCache` implementation
and should be represented as cache misses, not thrown to the provider.

The key interface boundary is that `ShardedFileMetadataCache` is not a `MetadataProvider`.
`ShardedFileMetadataCache` implements `FileMetadataCache` and only knows how to load/store cached
metadata payloads by key. `CachingMetadataProvider` implements `MetadataProvider` and is the layer
that decides whether to use the cache or call the next provider.

Cache files are sharded into 256 `MessagePak` files by the first byte of a stable cache-key hash:

```text
PrivateDataPath/
  MetadataCache/
    v1/
      00.mpak
      01.mpak
      ...
      ff.mpak
```

Each shard file contains many entries. On a cache update, the manager reads or reuses the shard,
modifies one entry, writes a temporary file in the same directory, and replaces the shard file.
The implementation uses a per-shard `ReentrantReadWriteLock` so cache-hit readers on the same
shard do not block each other, while shard loads, updates, pruning, and file replacement remain
exclusive. Indexing work on different shards also proceeds independently without a global lock.
Parsed shard contents are held through `SoftReference`s so the JVM can reclaim memory under
pressure.

The first production extractor should preserve current behaviour, including the already-added
`BufferedInputStream` around the MP3 Tika parse stream. That does not avoid Tika's full-frame
scan, but empirical testing showed it significantly improves the current path.

## 5. Architecture connections

Today, `FileTypeListManager` creates one `FileTypeList` per enabled type. Each `FileTypeList`
indexes its root directory in a background virtual thread and creates `MPG3FileItem` directly
when the type is `StandardTypes.MPG3`. `MPG3FileItem.getMessagePackRepresentation()` builds the
generic payload, then performs slow Tika parsing inline.

The new design keeps indexing ownership in the same place but injects the expensive metadata
dependency instead of hiding it behind a singleton. This lets tests replace the cache/provider
with a no-op or fake implementation and unit-test file-list behaviour without also testing disk
cache persistence or Tika.

### Connections table

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `FileMetadataCache` interface | `com.myster.filemanager` | `CachingMetadataProvider` | `MessagePak`; `MysterGlobals.getPrivateDataPath()` via implementation |
| `ShardedFileMetadataCache` | `com.myster.filemanager` | Production app setup in `Myster.java` | Private app data path; `MessagePak.fromBytes()` / `toBytes()` |
| `MetadataType` enum | `com.myster.filemanager` | `MetadataProvider`; cache key | Stable metadata namespaces such as `AUDIO` |
| `MetadataProvider` interface | `com.myster.filemanager` | `MPG3FileItem`; provider chain | Generic enrichment contract by requested `MetadataType` |
| `CachingMetadataProvider` | `com.myster.filemanager` | `MPG3FileItem` via DI | `FileMetadataCache`; delegates cache misses to another `MetadataProvider` |
| `TypeResolvingMetadataProvider` | `com.myster.filemanager` | `CachingMetadataProvider` | Maps `MetadataType` to a `TypedMetadataProvider` |
| `TypedMetadataProvider` interface | `com.myster.filemanager` | `TypeResolvingMetadataProvider` | Type-specific extraction contract |
| `TikaAudioMetadataProvider` | `com.myster.filemanager` | `TypeResolvingMetadataProvider` for `MetadataType.AUDIO` | Existing Tika MP3 parsing helpers and protocol keys |
| Constructor DI through `Myster.java` / `FileTypeListManager` / `FileTypeList` | App assembly and file manager layer | Indexing background task | Existing manual dependency injection pattern |

### Cache file format

Shard files use `MessagePak` bytes. Suggested v1 shape:

```text
/schemaVersion = 1
/entries/<entryKey>/path = absolute normalized path string
/entries/<entryKey>/metadataType = metadata schema key string
/entries/<entryKey>/size = long
/entries/<entryKey>/lastModifiedMillis = long
/entries/<entryKey>/createdAtMillis = long
/entries/<entryKey>/metadata/... = cached type-specific MessagePak keys
```

Only type-specific keys belong under `/metadata`: for MP3 this means `/BitRate`, `/Hz`,
`/LengthSec`, `/ID3Name`, `/Artist`, and `/Album` when present. Generic file-list keys remain
computed live.

## 6. Key decisions & edge cases

- **Manual dependency injection, no singleton.** The cache/provider graph is constructed by the
  file-manager owner and passed down. Tests can use fake providers or a no-op cache.
- **Private app data, not tmp.** The cache is valuable across restarts and should not be stored
  in a transient temp directory.
- **Shard files, not one giant file.** 256 shard files bound read/modify/write costs and isolate
  corruption to one shard.
- **Soft references, not weak references.** Weak references are likely to vanish too quickly to
  help. Soft references better match "keep shard data if memory allows."
- **Per-shard read/write locking.** Avoid a single global cache lock while keeping each shard's
  file and in-memory state consistent. Use a shard-local `ReentrantReadWriteLock`: read locks for
  cache-hit reads when shard data is already loaded, write locks for shard load, put/remove, prune,
  and file replacement. This is in-process protection for one running Myster app instance; do not
  add cross-process file locking because Myster already prevents multiple same-user app instances
  and each user has a separate private data directory.
- **Do not parse while holding shard locks.** Cache lookup should return a copied metadata payload
  or a miss, then release locks. Slow Tika extraction must happen outside `ShardedFileMetadataCache`
  locks, followed by a separate cache `put`.
- **Write temp then replace.** Avoid partially written shard files. Corrupt or unreadable shard
  files should be treated as empty and rebuilt without throwing an error to callers. Log at an
  appropriate level, then continue as cache misses.
- **Cache validity checks are mandatory.** A cache hit is usable only when path, metadata
  type/schema key, size, and last-modified millis match the current file.
- **Cache entries expire after six months.** Expired entries are misses and can be pruned from the
  shard during normal read/put flows. This also garbage-collects entries for files that are no
  longer encountered by indexing when their shard is touched.
- **Reuse known file size.** `FileItem` already computes file size for `/size`; pass that known
  value into cache-key creation where practical to avoid an extra filesystem call.
- **Missing `/size` is invalid.** `CachingMetadataProvider` requires the `MessagePak` produced by
  `FileItem.getMessagePackRepresentation()`. If `/size` is absent, throw `IllegalStateException`
  rather than falling back to parsing.
- **Do not bypass the cache on cache-layer failures.** If key construction fails, for example
  because last-modified time cannot be read, log and return without calling the delegate extractor.
  A cache miss is the only path that should delegate. `FileMetadataCache` implementations should
  absorb recoverable cache read problems such as missing, unreadable, or corrupt shard files and
  return a miss.
- **Keep `MPG3FileItem`'s local `messagePackRepresentation` cache.** The new cache is a persistent
  disk cache, not a replacement for per-object RAM caching.
- **Provider routing is a layer below caching.** `CachingMetadataProvider` should not know which
  extractor handles each metadata type. It only owns cache hit/miss behaviour and delegates misses
  to another `MetadataProvider`. `TypeResolvingMetadataProvider` owns the `MetadataType` to
  `TypedMetadataProvider` mapping.
- **Unsupported metadata types are no-ops.** `MetadataType.AUDIO` is the first supported kind.
  Unsupported metadata types should return without writing empty cache records.
- **Buffered Tika parse stays in place.** It is already part of the current MP3 parsing path and
  should be preserved when extraction moves into `TikaAudioMetadataProvider`, even though the
  cache is the main fix for repeated work.

## 7. Acceptance criteria

- [ ] `MPG3FileItem` receives metadata enrichment through an injected provider, not a singleton.
- [ ] A no-op or fake metadata provider can be used in unit tests without touching disk or Tika.
- [ ] MP3 metadata for an unchanged file is read from the cache on later indexing runs.
- [ ] Changing file size or last-modified time invalidates the cached metadata.
- [ ] Cache entries older than six months are ignored and eventually removed/replaced.
- [ ] Expired cache entries for files no longer encountered by indexing are pruned when their shard is touched.
- [ ] Cache files are stored under Myster private app data in 256 shard files.
- [ ] Cache updates are thread-safe for concurrent indexing.
- [ ] Corrupt or missing shard files do not break indexing; they are rebuilt and treated as cache misses.
- [ ] `MPG3FileItem` keeps its existing in-object `messagePackRepresentation` RAM cache after metadata-provider injection.
- [ ] Existing emitted MP3 metadata keys and value types are preserved.
- [ ] Existing `MPG3FileItem` tests still pass, with new cache/provider tests added.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `com.myster.filemanager.FileMetadataCache` — **NEW** interface for cache lookup/update.
- `com.myster.filemanager.FileMetadataCacheKey` — **NEW** immutable value object for path/metadata-type/size/mtime identity and shard key derivation.
- `com.myster.filemanager.ShardedFileMetadataCache` — **NEW** file-backed 256-shard cache implementation.
- `com.myster.filemanager.MetadataType` — **NEW** enum for cacheable metadata groups, initially `AUDIO`.
- `com.myster.filemanager.MetadataProvider` — **NEW** interface for type-specific metadata enrichment by requested metadata type.
- `com.myster.filemanager.TypedMetadataProvider` — **NEW** interface for a provider that handles one metadata type.
- `com.myster.filemanager.TypeResolvingMetadataProvider` — **NEW** provider that routes `MetadataType` to `TypedMetadataProvider`.
- `com.myster.filemanager.TikaAudioMetadataProvider` — **NEW** or extracted typed provider containing current Tika MP3 extraction logic for `MetadataType.AUDIO`.
- `com.myster.filemanager.CachingMetadataProvider` — **NEW** top-level provider that wraps the disk cache and delegates misses to another `MetadataProvider`.
- `com.myster.filemanager.MessagePakTreeUtils` — **NEW** package-private static helper for copying `MessagePak` directories and cacheable metadata keys; uses the `Utils` suffix required for static-only classes.
- `com.myster.filemanager.NoOpMetadataProvider` — **NEW** test/simple fallback provider.
- `com.myster.filemanager.MPG3FileItem` — constructor injection; delegate metadata enrichment to provider; keep existing local `messagePackRepresentation` caching; remove the obsolete `patchFunction2(...)` helper.
- `com.myster.filemanager.FileTypeList` — accept provider/cache dependency and pass it to `MPG3FileItem`.
- `com.myster.Myster` — construct production provider graph and pass it into `FileTypeListManager`.
- `com.myster.filemanager.FileTypeListManager` — accept provider graph and pass it into `FileTypeList`.
- `com.myster.application.MysterGlobals` — optional helper `getMetadataCachePath()` if preferred over local path construction.
- Tests under `src/test/java/com/myster/filemanager/` — new cache/provider tests; update `TestMPG3FileItem` as needed.

Implementation mapping:

| Class | Implements | Role |
|---|---|---|
| `ShardedFileMetadataCache` | `FileMetadataCache` | Disk-backed cache storage only |
| `CachingMetadataProvider` | `MetadataProvider` | Top-level cache hit/miss wrapper |
| `TypeResolvingMetadataProvider` | `MetadataProvider` | Routes misses by `MetadataType` |
| `TikaAudioMetadataProvider` | `TypedMetadataProvider` | Extracts audio metadata |
| `NoOpMetadataProvider` | `MetadataProvider` | Test/fallback provider |

## 9. Step-by-step implementation

1. **Create metadata-provider types.**
   - Add `MetadataType`:

   ```java
   public enum MetadataType {
       AUDIO("audio-v1",
               List.of("/BitRate", "/Hz", "/LengthSec", "/ID3Name", "/Artist", "/Album"));

       private final String cacheKey;
       private final List<String> cacheableKeys;

       MetadataType(String cacheKey, List<String> cacheableKeys) {
           this.cacheKey = cacheKey;
           this.cacheableKeys = List.copyOf(cacheableKeys);
       }

       public String cacheKey() {
           return cacheKey;
       }

       List<String> cacheableKeys() {
           return cacheableKeys;
       }
   }
   ```

   - Add `MetadataProvider`:

   ```java
   public interface MetadataProvider {
       void enrich(MetadataType metadataType, MessagePak messagePack, Path path);
   }
   ```

   - Add `TypedMetadataProvider`:

   ```java
   public interface TypedMetadataProvider {
       void enrich(MessagePak messagePack, Path path);
   }
   ```

   - Add `NoOpMetadataProvider` for tests and non-production fallback.

2. **Extract current MP3 enrichment into `TikaAudioMetadataProvider`.**
   - Move the old direct Tika parsing behavior into `TikaAudioMetadataProvider.enrich(...)`.
   - `TikaAudioMetadataProvider` should implement `TypedMetadataProvider`; it does not need to
     inspect `MetadataType` because `TypeResolvingMetadataProvider` only calls it for
     `MetadataType.AUDIO`.
   - Remove `MPG3FileItem.patchFunction2(...)`; metadata enrichment now goes through
     `metadataProvider.enrich(MetadataType.AUDIO, messagePackRepresentation, getPath())`.
   - Preserve the existing `BufferedInputStream` around Tika's input stream.
   - Stop catching `Throwable`; catch recoverable parse/open exceptions explicitly enough to
     match the project's exception convention.

3. **Create `FileMetadataCacheKey`.**
   - Fields:
     - `String metadataType`
     - `String normalizedAbsolutePath`
     - `long size`
     - `long lastModifiedMillis`
     - `long createdAtMillis` in stored cache entries, not necessarily in the lookup identity
   - Factory:

   ```java
   static FileMetadataCacheKey from(MetadataType metadataType, Path path, long fileSize) throws IOException
   ```

   - `metadataType.cacheKey()` is the stable persisted namespace, such as `"audio-v1"`. It is not
     `MysterType`.
   - Use `path.toAbsolutePath().normalize().toString()` for v1. Do not call expensive content
     hashing.
   - Use the `fileSize` already computed by `FileItem` where possible; only stat the file inside
     key construction when no caller-known size is available.
   - Read last-modified time via `Files.getLastModifiedTime(path).toMillis()`.
   - Store `metadataType` in `/metadataType`.
   - Derive `entryKey` using SHA-256 over `metadataType + "\n" + normalizedAbsolutePath`, hex encoded.
   - Shard id is the first two hex chars of `entryKey`.

4. **Create `FileMetadataCache`.**
   - Recommended API:

   ```java
   public interface FileMetadataCache {
       Optional<MessagePak> get(FileMetadataCacheKey key);
       void put(FileMetadataCacheKey key, MessagePak metadata);
       void remove(FileMetadataCacheKey key);
   }
   ```

   - `get(...)` returns only the cached type-specific metadata payload when the stored metadata
     type and file state match the key.

5. **Implement `ShardedFileMetadataCache`.**
   - Constructor takes `Path cacheRoot`.
   - Store under `cacheRoot.resolve("v1")`.
   - Maintain 256 shard state objects:

   ```java
   private static final class Shard {
       final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
       SoftReference<MessagePak> data = new SoftReference<>(null);
   }
   ```

   - On read:
     1. take the read lock and use the soft-referenced shard data if present
     2. if shard data is absent, release the read lock, take the write lock, load `xx.mpak`, and
        publish it into the soft reference
     3. under the read lock, validate the entry fields and return copied/isolated metadata; expired
        entries are treated as cache misses
   - On put/remove: take the write lock, load shard if needed, modify `/entries/<entryKey>`, prune
     expired entries in the touched shard when convenient, write temp file, then replace shard.
   - Use `MessagePak.fromBytes(...)` and `MessagePak.toBytes()`.
   - Treat unreadable/corrupt shard bytes as an empty shard after logging a warning. Do not throw
     to indexing callers; the shard will be rebuilt naturally as entries are written.
   - Prune expired entries only during writes, when the shard write lock is already held. Reads
     should only ignore expired entries and return cache misses.
   - Use a package-private `MessagePakTreeUtils` helper for `MessagePak` subtree copying so
     shard serialization does not duplicate recursive copy logic.

6. **Implement `TypeResolvingMetadataProvider`.**
   - Constructor takes provider routing for supported `MetadataType` values. A simple first
     version can take a `Map<MetadataType, TypedMetadataProvider>`.
   - On `enrich(...)`, look up the typed provider for the requested `MetadataType`.
   - If no provider exists for the requested type, return without modifying the `MessagePak`.
   - If a provider exists, delegate to `typedProvider.enrich(messagePack, path)`.

7. **Implement `CachingMetadataProvider`.**
   - Constructor takes `FileMetadataCache cache` and a delegate `MetadataProvider`.
   - On `enrich(...)`:
     1. read the already-computed `/size` from the supplied `MessagePak`
     2. build `FileMetadataCacheKey` using `metadataType.cacheKey()` and that known size
     3. if `cache.get(key)` returns metadata, merge it into the outgoing `MessagePak`
     4. otherwise call the delegate for the requested metadata type with the same already-populated
        `MessagePak`, then extract only the type-specific keys into the disk cache
   - If `/size` is missing, throw `IllegalStateException` because callers must pass the populated
     base `FileItem` metadata pack.
   - If key construction fails due to `IOException`, log and return without modifying the pack.
   - `FileMetadataCache.get(key)` should not throw for recoverable cache storage problems; those
     are cache misses.
   - Merge only allowed type-specific keys.
   - Use `MessagePakTreeUtils` when copying cacheable keys between packs.
   - If the delegate does not add any cacheable metadata keys, do not write an empty cache entry.

8. **Wire DI through the file manager.**
   - Add a `MetadataProvider` field to `FileTypeList`.
   - Require `MetadataProvider` in the `FileTypeList` constructor; update tests to pass
     `NoOpMetadataProvider` or a fake explicitly.
   - Update `Myster.java` app assembly to construct:

   ```java
   Path cacheRoot = MysterGlobals.getPrivateDataPath().toPath().resolve("MetadataCache");
   FileMetadataCache cache = new ShardedFileMetadataCache(cacheRoot);
   MetadataProvider resolver =
       new TypeResolvingMetadataProvider(Map.of(MetadataType.AUDIO, new TikaAudioMetadataProvider()));
   MetadataProvider metadataProvider =
       new CachingMetadataProvider(cache, resolver);
   ```

   - Pass `metadataProvider` into `FileTypeListManager`; `FileTypeListManager` passes it to every
     `FileTypeList`. Only file items that need expensive
     metadata use it.

9. **Update `MPG3FileItem`.**
   - Add constructor:

   ```java
   public MPG3FileItem(Path root, Path path, MetadataProvider metadataProvider)
   ```

   - Do not keep the old no-provider constructor; production and tests should both inject a
     provider explicitly.
   - In `getMessagePackRepresentation()`, after `super.getMessagePackRepresentation()` has
     populated `/size`, call the provider with the same pack:

   ```java
   MessagePak messagePack = super.getMessagePackRepresentation();
   metadataProvider.enrich(MetadataType.AUDIO, messagePack, getPath());
   return messagePack;
   ```

10. **Keep `MPG3FileItem` local `messagePackRepresentation` caching.**
   - Keep the `private MessagePak messagePackRepresentation` field in `MPG3FileItem`.
   - `getMessagePackRepresentation()` should continue returning the existing in-memory value when
     present.
   - On first construction of the in-memory pack, build through `super.getMessagePackRepresentation()`,
     then call the injected provider.
   - The new persistent cache handles cold starts and new `MPG3FileItem` instances; the local RAM
     cache handles repeated calls on the same object.

11. **Optional app path helper.**
    - If local path construction is repeated or unclear, add
      `MysterGlobals.getMetadataCachePath()` returning
      `new File(getPrivateDataPath(), "MetadataCache")`.

## 10. Tests to write

| Test | Verifies |
|---|---|
| `FileMetadataCacheKey_sameFileStateSameKey` | Stable key for same metadata type/path/size/mtime |
| `FileMetadataCacheKey_differentPathDoesNotReuseEntry` | Same metadata type and file state under different paths produce separate entries |
| `FileMetadataCacheKey_changedSizeOrMtimeInvalidates` | Different file state does not reuse entry |
| `ShardedFileMetadataCache_putThenGet` | Stored metadata can be read back |
| `ShardedFileMetadataCache_getReturnsEmptyForStaleSize` | Size mismatch is a cache miss |
| `ShardedFileMetadataCache_getReturnsEmptyForStaleMtime` | Mtime mismatch is a cache miss |
| `ShardedFileMetadataCache_corruptShardIsMiss` | Bad shard bytes do not fail indexing |
| `ShardedFileMetadataCache_expiredEntryIsMiss` | Entries older than six months are ignored on read and pruned on a later shard write |
| `ShardedFileMetadataCache_concurrentDifferentShards` | Concurrent access does not corrupt shard files |
| `ShardedFileMetadataCache_sameShardReadersDoNotBlockEachOther` | Concurrent cache-hit reads on the same loaded shard can proceed together |
| `ShardedFileMetadataCache_sameShardWritesSerialize` | Same-shard updates serialize safely |
| `CachingMetadataProvider_cacheHitDoesNotCallDelegate` | Cache hit avoids expensive parser |
| `CachingMetadataProvider_cacheMissCallsDelegateAndStores` | Miss extracts once and stores |
| `CachingMetadataProvider_emptyDelegateResultDoesNotWriteCacheEntry` | Unsupported metadata types do not create empty cache records |
| `CachingMetadataProvider_missingSizeThrows` | Missing `/size` is treated as a caller contract violation |
| `CachingMetadataProvider_keyCreationFailureDoesNotCallDelegate` | Missing mtime/key construction failure does not bypass the cache |
| `TypeResolvingMetadataProvider_routesByMetadataType` | `MetadataType.AUDIO` calls the audio typed provider |
| `TypeResolvingMetadataProvider_unsupportedTypeNoOps` | Missing providers leave the pack unchanged |
| `MPG3FileItem_usesInjectedProvider` | Unit test can enrich without Tika/disk cache |
| `MPG3FileItem_keepsMessagePakRamCache` | Repeated calls on the same item return the in-object cached pack and do not re-enter the provider |
| Existing `TestMPG3FileItem` | Current helper and failure semantics remain valid or are updated to provider tests |

Manual smoke:

1. Share a directory with many MP3 files and clear `PrivateDataPath/MetadataCache`.
2. Start Myster and allow indexing; confirm metadata appears.
3. Restart Myster without changing files; confirm indexing is materially faster and logs show no parse failures.
4. Modify one MP3's timestamp or replace one file; confirm only that file is reparsed.

## 11. Docs / Javadoc to update

- `FileMetadataCache` — document cache validity contract and that values are type-specific metadata only.
- `ShardedFileMetadataCache` — class Javadoc for private app data storage, shard format, corruption handling, and thread-safety.
- `MetadataType` — document cache key stability requirements.
- `MetadataProvider` — document that implementations enrich a provided `MessagePak` in place and must not throw for ordinary parse failures.
- `TypedMetadataProvider` — document single-type extraction contract.
- `TypeResolvingMetadataProvider` — document metadata-type routing and unsupported-type no-op behaviour.
- `CachingMetadataProvider` — document cache hit/miss flow and fallback behaviour.
- `MPG3FileItem` — update class Javadoc to describe injected metadata enrichment instead of direct parser ownership.
- `docs/conventions/myster-important-patterns.md` — add a short note if this establishes a reusable pattern for file-backed caches with per-shard locks and manual DI.
