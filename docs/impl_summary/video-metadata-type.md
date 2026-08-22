# Video Metadata Type Implementation Summary

## What was implemented

Added a reusable built-in `video` metadata profile and subscribed the bundled `MOOV` Myster type
to it. Video indexing now creates `VideoFileItem` instances, extracts best-effort metadata through
Apache Tika, caches a versioned five-key schema, and displays sortable Duration, Resolution, Codec,
and Bit Rate columns in local file and remote search views.

## Files changed

- `src/main/resources/com/myster/typedescriptionlist.mml` - marks `MOOV` as `video`.
- `src/main/java/com/myster/filemanager/MetadataType.java` - exposes `MetadataType.VIDEO`.
- `src/main/java/com/myster/filemanager/BuiltInMetadataType.java` - adds the `video` profile,
  `video-v1` cache schema, item factory, GUI handler, and typed extractor.
- `src/main/java/com/myster/filemanager/DefaultMetadataTypeRegistry.java` - registers `video`.
- `src/main/java/com/myster/filemanager/VideoFileItem.java` - new server-side video file item and
  protocol documentation.
- `src/main/java/com/myster/filemanager/TikaVideoMetadataExtractor.java` - new best-effort Tika
  extraction, validation, unit conversion, and bitrate estimation.
- `src/main/java/com/myster/search/ui/ClientVideoHandleObject.java` - new sortable video columns and
  formatting for both file records and search results.
- Registry, type-description, indexing, caching, GUI selection, extractor, file-item, and column
  handler tests under `src/test/java/com/myster/**`.

## Key decisions

- `video` is the reusable metadata profile id; `MOOV` remains a concrete Myster network type.
- The protocol stores dimensions independently as positive numeric `/VideoWidth` and
  `/VideoHeight` values. The GUI renders them as `1920x1080` and sorts by pixel area.
- `/VideoBitRate` is bits per second. A parser-provided Tika data rate wins; otherwise the extractor
  estimates average multiplexed file bitrate from `/size` and duration without MP3-style clamping.
- Partial metadata is valid. Invalid and unavailable fields are omitted independently and display
  as `-`.
- `MessagePak` numeric and string access remain type-specific in the GUI adapter because requesting
  a value through the wrong typed getter throws rather than returning an empty value.
- Empty extraction results are persisted as negative cache hits. This prevents unsupported AVI/MKV
  files from being rescanned after the hourly file-list refresh or an application restart. Negative
  entries use the same path, size, modification time, `video-v1`, and expiry invalidation as normal
  metadata entries and do not add fields to the network payload.

## Deviations from the plan

- No real MP4 fixture was added. The repository has no existing video fixture, and no local FFmpeg
  binary was available to produce one without adding a test prerequisite. Metadata conversion is
  tested using constructed Tika `Metadata`; the parser failure path is tested with a missing file.
- `DefaultMetadataTypeRegistry` requires an explicit `VIDEO` registration because its current
  implementation does not enumerate `BuiltInMetadataType` automatically.

## Javadoc and design docs

- Added protocol and behavior Javadoc to `VideoFileItem`, `TikaVideoMetadataExtractor`, and
  `ClientVideoHandleObject`.
- Existing design documents do not describe the metadata profile subsystem, so none required an
  update.
- Repository-wide Javadoc generation was attempted but fails on pre-existing malformed Javadocs in
  unrelated classes, including `TrackerThreeDnsPanel`, `MultiSourceDownload`, `MysterPreferences`,
  and `MCListTableModel`. No reported Javadoc error originated in the new video classes.

## Verification

- Passed: `mvn -q -DskipTests test-compile`
- Passed focused metadata regression suite:
  `mvn -q -Djava.awt.headless=true -Dtest=TestCachingFileMetadataExtractor,TestShardedFileMetadataCache,TestVideoFileItem,TestTikaVideoMetadataExtractor,TestClientVideoHandleObject,TestTypeResolvingFileMetadataExtractor,TestMPG3FileItem,TestImageFileItem test`
- Passed: `mvn -q -Djava.awt.headless=true test`
- `git diff --check` passes.

An initial full-suite run encountered the repository's intermittent home-directory, Preferences
lock, and UDP bind environment failures. A final rerun completed successfully. The successful run
still printed an existing asynchronous Jimfs `ClosedDirectoryStreamException` after a test closed
its filesystem, but Maven reported no test failure.

## Known issues and follow-up

- Tika 3.3.1 in the current dependency set has materially narrower container support than FFprobe.
  Unsupported `MOOV` extensions keep generic metadata and empty video columns.
- Add a small, legally redistributable MP4 fixture to verify parser integration end to end.
- An optional FFprobe-backed `TypedMetadataExtractor` could later improve container, codec, stream
  bitrate, and frame-rate coverage without changing the profile or GUI protocol.
- The current void extractor contract cannot distinguish unsupported content from a transient
  parser/read failure that was caught by an extractor. Both can produce a negative cache entry;
  file identity changes or normal cache expiry permit a retry. Introduce an explicit extraction
  outcome if transient failures need a separate, shorter retry policy.
