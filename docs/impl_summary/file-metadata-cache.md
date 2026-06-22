# File Metadata Cache Implementation Summary

## Overview

Implemented a disk-backed metadata cache for expensive file metadata extraction, starting with
MP3/audio metadata. `MPG3FileItem` keeps its existing in-object `MessagePak` RAM cache and now
delegates expensive enrichment through an injected metadata provider chain.

## Files changed

- `src/main/java/com/myster/filemanager/MetadataType.java` — new metadata type enum, initially `AUDIO`.
- `src/main/java/com/myster/filemanager/MetadataProvider.java` — new top-level enrichment interface.
- `src/main/java/com/myster/filemanager/TypedMetadataProvider.java` — new single-type extractor interface.
- `src/main/java/com/myster/filemanager/TypeResolvingMetadataProvider.java` — routes `MetadataType` to typed providers.
- `src/main/java/com/myster/filemanager/CachingMetadataProvider.java` — cache hit/miss wrapper.
- `src/main/java/com/myster/filemanager/FileMetadataCache.java` — cache contract.
- `src/main/java/com/myster/filemanager/FileMetadataCacheKey.java` — file identity and shard key.
- `src/main/java/com/myster/filemanager/ShardedFileMetadataCache.java` — 256-shard MessagePak disk cache.
- `src/main/java/com/myster/filemanager/TikaAudioMetadataProvider.java` — extracted Tika MP3 metadata reader.
- `src/main/java/com/myster/filemanager/MessagePakTreeUtils.java` — package helper for copying allowed MessagePak keys, renamed during review to follow the static-only `Utils` convention.
- `src/main/java/com/myster/filemanager/MPG3FileItem.java` — constructor injection and provider delegation.
- `src/main/java/com/myster/filemanager/FileTypeList.java` — carries metadata provider into indexed MP3 file items.
- `src/main/java/com/myster/Myster.java` — builds the production cache/provider graph.
- `src/main/java/com/myster/filemanager/FileTypeListManager.java` — receives and passes through the provider graph.
- `src/test/java/com/myster/filemanager/Test*Metadata*.java` and `TestMPG3FileItem.java` — new focused coverage.
- `docs/conventions/myster-important-patterns.md` — documented when read/write locks are appropriate.
- `docs/plans/file-metadata-cache.md` — kept current with implementation details.

## Key decisions

- `ShardedFileMetadataCache` implements `FileMetadataCache`, not `MetadataProvider`.
- `CachingMetadataProvider` calls the delegate with the already-populated `MessagePak`, so `/size`
  from `FileItem` is available without another file-size stat.
- `MPG3FileItem.patchFunction2(...)` was removed; audio enrichment now goes through the injected
  provider path.
- Missing `/size` is treated as a caller contract violation and throws `IllegalStateException`
  instead of silently parsing or caching against an incomplete payload.
- Key creation failures log and return without delegation; only a successful cache miss calls the
  extractor. Recoverable cache read problems are handled inside `FileMetadataCache` implementations
  as misses.
- Only `MetadataType`-owned keys are written to disk; generic `/size`, `/path`, and hashes are live.
- Cache shard files use per-shard `ReentrantReadWriteLock`, soft-referenced loaded data, and temp-file
  replacement with atomic move fallback.
- Expired entries are ignored on read and pruned during shard writes, avoiding read-lock to
  write-lock promotion in the cache-hit path.
- Corrupt or unreadable shard files are treated as empty cache misses.
- `FileMetadataCacheKey` only exposes the `MetadataType` factory publicly; its string-key helper is
  private implementation detail.
- Review follow-up named the static-only helper `MessagePakTreeUtils` to match the repository
  utility-class naming convention.

## Deviations from plan

- Old no-provider `FileTypeList` and `MPG3FileItem` constructors were removed. Production injects
  the disk-cached provider from `Myster.java`; tests inject `NoOpMetadataProvider` or fakes.

## Tests

Passed:

```text
mvn -Dtest=TestFileMetadataCacheKey,TestShardedFileMetadataCache,TestCachingMetadataProvider,TestTypeResolvingMetadataProvider,TestMPG3FileItem test
```

Result: 45 tests, 0 failures, 0 errors.

After later review cleanup, also passed:

```text
mvn -Dtest=TestFileMetadataCacheKey,TestShardedFileMetadataCache,TestCachingMetadataProvider,TestTypeResolvingMetadataProvider,TestMPG3FileItem,TestSortableLength,TestClientMPG3HandleObject test
```

Result: 60 tests, 0 failures, 0 errors.

Also attempted with `TestFileTypeList`; the added tests still passed, but `TestFileTypeList` hit the
existing X11 failure in `FileTypeList.waitForIndexer()` via `Util.invokeAndWait` when run without
headless mode. The same test class passes with:

```text
mvn -Djava.awt.headless=true -Dtest=TestFileTypeList test
```

Result: 24 tests, 0 failures, 0 errors.

## Docs

No `docs/design/` file described this file-manager metadata cache directly, so no design doc update
was needed. Javadoc was added for the new cache/provider contracts and updated on `MPG3FileItem`.

## Follow-up

- Consider adding a manual or integration smoke test with a large MP3 share to measure first-run parse
  cost versus restart/cache-hit indexing cost.
- If write amplification appears during large first-time indexing, add dirty-shard batching as a later
  milestone.
