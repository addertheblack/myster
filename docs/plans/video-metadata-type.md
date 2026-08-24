# Video Metadata Type

**Feature slug:** `video-metadata-type`  
**Date:** 2026-08-21  
**Status:** Ready for implementation

## 1. Summary

Add a built-in `video` metadata profile and subscribe the existing `MOOV` Myster type to it, so video files are enriched with duration, dimensions, codec, and bitrate metadata and expose corresponding sortable columns in file and search views.

## 2. Non-goals

- Do not rename or replace the existing concrete `MOOV` Myster network type.
- Do not add an external `ffprobe`/FFmpeg runtime dependency in this milestone.
- Do not promise metadata extraction for every extension currently associated with `MOOV`; Apache Tika support remains best effort by container format.
- Do not add frame rate, audio stream details, subtitles, title/year/genre tags, thumbnails, or media playback UI in this milestone.
- Do not add a custom-type UI for selecting the `video` metadata profile.
- Do not change generic, audio, or image metadata behavior.

## 3. Assumptions & open questions

- The stable metadata profile id is `video`, while `MOOV` remains the concrete network/file-sharing type. Future concrete Myster types may also subscribe to `video`.
- The initial visible columns are `Duration`, `Resolution`, `Codec`, and `Bit Rate`, in that order after the generic file columns.
- Resolution is stored as separate positive numeric width and height values and displayed without spaces as `1920x1080`.
- Bitrate is stored in bits per second. Prefer a parser-provided data rate; when none is available but file size and duration are known, use average file bitrate as a documented estimate. For multiplexed files this estimate includes audio and container overhead and is not a pure video-stream bitrate.
- Apache Tika 3.3.1 and its audio/video parser module remain the extraction backend. The current dependency includes MP4 and FLV parsers but does not provide broad coverage equivalent to FFprobe. AVI and MKV are known unsupported, expensive cases and must be skipped before opening the file; other unsupported formats should retain empty video fields rather than failing indexing.

## 4. Proposed design

Add `VIDEO` to the built-in metadata profiles with stable id `video`, cache namespace `video-v1`, a video file item, a Tika-backed typed extractor, and a video column handler. Mark the bundled `MOOV` type description with `Metadata Type: video`; all indexing and GUI selection then flow through the existing `MetadataTypeRegistry` without a `MOOV` branch.

The video metadata protocol adds five optional fields: duration in whole seconds, width and height in pixels, codec as a normalized display string, and bitrate in bits per second. Values are omitted when unavailable, invalid, zero, or negative. The GUI combines width and height into one sortable resolution column and formats duration and bitrate using the existing audio-style sortable conventions where practical.

Extraction checks the filename extension before opening the file. Case-insensitive `.avi` and `.mkv` files bypass Tika and produce an empty typed metadata result, which the cache persists as a negative entry. Other extensions use Tika auto-detection so every parser available in the configured audio/video module can participate. It reads standard Tika properties for duration, image width/height, video compressor, and file data rate. If no usable data rate exists, it estimates average bitrate from the base file item's `/size` and parsed duration. Parser failures are logged and leave only the generic file metadata.

## 5. Architecture connections

The feature is an extension of the metadata registry refactor. The type description selects one profile; that profile owns server-side item creation, extraction/cache behavior, and client-side columns.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `video` metadata subscription | bundled `typedescriptionlist.mml` | `MetadataTypeRegistry.get(tdList, MOOV)` | Explicitly maps concrete `MOOV` to the reusable video profile |
| `MetadataType.VIDEO` / built-in video profile | `BuiltInMetadataType` | registry, extractor setup, indexing, GUI | Supplies `video-v1`, cache keys, `VideoFileItem`, extractor, and handler |
| `VideoFileItem` | `com.myster.filemanager` | `FileTypeList` through `MetadataType.createFileItem` | Adds optional video protocol fields to the base file stats MessagePak |
| `TikaVideoMetadataExtractor` | built-in video profile | `TypeResolvingFileMetadataExtractor` | Converts Tika metadata and base `/size` into stable Myster protocol values |
| `ClientVideoHandleObject` | built-in video profile | search and client file-list views | Formats and sorts duration, dimensions, codec, and bitrate |

Plain-English data flow: indexing sees that `MOOV` subscribes to `video`, asks the video profile to create a `VideoFileItem`, and enriches its base MessagePak through the cache and typed extractor. Remote clients receive the same optional MessagePak keys already carried by the file stats/search protocol. Their registry resolves `MOOV` to the video profile and selects `ClientVideoHandleObject` to display those values.

The new optional MessagePak file metadata fields are:

- `/VideoLengthSec`: duration rounded to whole seconds (`long`).
- `/VideoWidth`: encoded display width in pixels (`long`).
- `/VideoHeight`: encoded display height in pixels (`long`).
- `/VideoCodec`: parser-provided video compressor/codec name, trimmed (`String`).
- `/VideoBitRate`: parser-provided or estimated average bitrate in bits per second (`long`).

Older clients ignore these additional keys. New clients display `-` when talking to older servers or when extraction does not produce a field.

## 6. Key decisions & edge cases

- **Profile name is video:** `video` describes the metadata implementation; `MOOV` remains one concrete subscriber. The implementation must not infer video behavior from the `MOOV` internal name.
- **No combined resolution field:** Numeric width and height preserve numeric sorting and future filtering. The GUI alone creates the `WIDTHxHEIGHT` presentation.
- **Resolution sorting:** Sort by pixel area when both dimensions are positive; missing or incomplete dimensions sort as unknown and display `-`.
- **Duration representation:** Store rounded whole seconds for consistency with existing audio metadata and use an hours-capable display such as `1:42:18`.
- **Bitrate representation:** Store bits per second and format as `kbps` below 1 Mbps and a concise decimal `Mbps` at or above 1 Mbps. Parser-provided values take precedence over estimates.
- **Bitrate estimation:** Only estimate when both file size and a positive finite duration are available. Do not clamp video bitrate to MP3 bitrate buckets. Guard overflow and non-finite arithmetic.
- **Codec normalization:** Trim parser output and omit blank values. Do not maintain a hardcoded codec alias table in the first version; preserving Tika's value avoids silently mislabeling codecs.
- **Partial metadata is valid:** Each key is independent. A file may show codec and duration while resolution or bitrate remains unknown.
- **AVI/MKV fast skip:** Match the final filename extension case-insensitively and return before opening the file. This avoids known-unproductive Tika parsing and produces an empty typed metadata result for negative caching.
- **Parsing must not block indexing failure recovery:** IOException, Tika, SAX, malformed numeric fields, and unsupported formats must be logged and leave unavailable fields absent.
- **Cache evolution:** Keep `video-v1` because the AVI/MKV skip does not change the five cached fields or their meanings. Avoiding a version increment also prevents supported videos from being re-read solely for this extraction-routing change. Any future semantic change to the fields, especially bitrate meaning, requires a new cache key.
- **Negative caching:** A completed extraction with no video fields is a valid cache hit. Persist
  that empty result so unsupported files are not rescanned after hourly reindexing or restart. File
  identity changes, cache schema changes, and normal cache expiry invalidate negative entries.

## 7. Acceptance criteria

- [ ] The bundled `MOOV` type explicitly subscribes to metadata type id `video`.
- [ ] The default registry exposes one built-in video profile without any `MOOV`-specific branch in indexing or GUI factory code.
- [ ] Supported video files can publish optional duration, width, height, codec, and bitrate MessagePak fields.
- [ ] Video metadata is cached under `video-v1` and only the five declared video keys are cached.
- [ ] File and search views show `Duration`, `Resolution`, `Codec`, and `Bit Rate` columns for `MOOV`.
- [ ] Resolution displays as `1920x1080`, duration supports hour-long media, and bitrate uses readable `kbps`/`Mbps` units.
- [ ] Video columns sort by numeric duration, pixel area, codec text, and numeric bitrate rather than formatted display text.
- [ ] Missing or malformed metadata displays as `-` and does not prevent indexing or viewing the file.
- [ ] Formats unsupported by the installed Tika parsers retain generic file metadata without throwing.
- [ ] AVI and MKV files are skipped before file-content access and produce empty typed metadata results regardless of extension case.
- [ ] Empty extraction results are reused from persistent cache rather than rescanned after reindexing or restart.
- [ ] Existing generic, audio, and image metadata tests continue to pass.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- `src/main/resources/com/myster/typedescriptionlist.mml` - add `<Metadata Type>video</Metadata Type>` to `MOOV`.
- `src/main/java/com/myster/filemanager/MetadataType.java` - expose the built-in `VIDEO` compatibility constant.
- `src/main/java/com/myster/filemanager/BuiltInMetadataType.java` - add the `video` profile, `video-v1` cache schema, item factory, handler, and extractor.
- `src/main/java/com/myster/filemanager/VideoFileItem.java` - **NEW**, enrich base file metadata with the video profile and document protocol fields.
- `src/main/java/com/myster/filemanager/TikaVideoMetadataExtractor.java` - **NEW**, parse and normalize video metadata through Tika.
- `src/main/java/com/myster/search/ui/ClientVideoHandleObject.java` - **NEW**, provide sortable and formatted video columns for file and search records.
- `src/test/java/com/myster/type/TestDefaultTypeDescriptionListMetadataTypeId.java` - verify `MOOV` subscribes to `video`.
- `src/test/java/com/myster/filemanager/TestDefaultMetadataTypeRegistry.java` - verify video profile registration.
- `src/test/java/com/myster/filemanager/TestVideoFileItem.java` - **NEW**, verify enrichment, memoization, and protocol behavior.
- `src/test/java/com/myster/filemanager/TestTikaVideoMetadataExtractor.java` - **NEW**, verify property conversion, estimation, failures, and a generated/small fixture if practical.
- `src/test/java/com/myster/search/ui/TestClientVideoHandleObject.java` - **NEW**, verify columns, formatting, sorting values, and missing metadata.
- `src/test/java/com/myster/search/ui/TestClientInfoFactoryUtils.java` - verify a type subscribed to `video` receives the video handler.
- `src/test/java/com/myster/filemanager/TestFileTypeList.java` - verify video subscription creates a `VideoFileItem` through the profile.

### 9. Step-by-step implementation

1. Add the type subscription and profile constant:
   - In `typedescriptionlist.mml`, add `<Metadata Type>video</Metadata Type>` directly under the `MOOV` type.
   - Add `MetadataType VIDEO = BuiltInMetadataType.VIDEO` beside the existing compatibility constants.

2. Add `BuiltInMetadataType.VIDEO`:
   - Use id `video` and cache key `video-v1`.
   - Set cacheable keys exactly to `/VideoLengthSec`, `/VideoWidth`, `/VideoHeight`, `/VideoCodec`, and `/VideoBitRate`.
   - Return `ClientVideoHandleObject`, create `VideoFileItem`, and return `TikaVideoMetadataExtractor` from `typedMetadataExtractor()`.
   - Rely on `DefaultMetadataTypeRegistry`'s existing enumeration/registration behavior; do not add a separate registry branch unless its implementation requires an explicit list.

3. Implement `VideoFileItem` following `ImageFileItem`:
   - Constructor accepts root, path, and `FileMetadataExtractor`; reject a null extractor.
   - Memoize `getMessagePackRepresentation()`.
   - Start with `super.getMessagePackRepresentation()`, then call `metadataExtractor.enrich(MetadataType.VIDEO, messagePack, getPath())`.
   - Document all five optional MessagePak keys and bitrate estimation semantics in class Javadoc.

4. Implement `TikaVideoMetadataExtractor`:
   - Return immediately for case-insensitive `.avi` and `.mkv` filename extensions before opening or reading the path.
   - Parse through `AutoDetectParser` with `DefaultHandler`, `Metadata`, and `ParseContext`, using a buffered file input stream and try-with-resources.
   - Read duration from `XMPDM.DURATION`, accepting positive finite decimal seconds and writing rounded `/VideoLengthSec`.
   - Read dimensions from Tika's `Metadata.IMAGE_WIDTH` and `Metadata.IMAGE_LENGTH`/height property as positive integers and write `/VideoWidth` and `/VideoHeight` independently.
   - Read codec from `XMPDM.VIDEO_COMPRESSOR`, trim it, and write `/VideoCodec` only when non-blank.
   - Parse `XMPDM.FILE_DATA_RATE` using a unit-aware helper. Support plain numeric values and common `kbps`, `kbit/s`, `Mbps`, and `Mbit/s` spellings case-insensitively; convert to bits per second. Reject ambiguous or malformed values rather than guessing.
   - If no parser bitrate was accepted, estimate `(fileSizeBytes * 8) / durationSeconds` from `/size`. Round to a positive `long`, guard overflow/non-finite values, and do not clamp.
   - Keep parsing/conversion helpers package-private static where focused tests need them.
   - Catch parser/read exceptions consistently with other extractors and do not remove base metadata or partially extracted valid fields.

5. Implement `ClientVideoHandleObject` following the newer image handler structure rather than copying legacy switch duplication from the audio handler:
   - Extend `ClientGenericHandleObject` and append headers `Duration`, `Resolution`, `Codec`, `Bit Rate`.
   - Use widths appropriate for `1:42:18`, `1920x1080`, common codec names, and `12.5 Mbps` without changing generic columns.
   - Share helper methods between `FileRecord` and `SearchResult` paths so both parse and format identically.
   - Duration sortable: numeric seconds; display `m:ss` below one hour and `h:mm:ss` at or above one hour.
   - Resolution sortable: pixel area; display `width + "x" + height`; require both values to be positive.
   - Codec sortable: normalized string with `-` for null/blank.
   - Bitrate sortable: numeric bits per second; display rounded integral `kbps` below 1,000,000 bps and one decimal `Mbps` at or above it, trimming an unnecessary `.0` if consistent with local formatting conventions.
   - Folder rows and missing/malformed values display `-` and sort as unknown.

6. Extend registry and integration tests:
   - Verify `registry.get("video")` returns `MetadataType.VIDEO`, normalization still applies, and `supportedTypes()` includes a unique `video` id.
   - Verify the bundled `MOOV` description returns `Optional.of("video")`.
   - Add a fake subscribed Myster type to existing GUI/indexing tests rather than branching on the concrete `MOOV` value.

7. Add extractor and protocol tests:
   - Test duration parsing for decimal, blank, non-numeric, zero, negative, NaN, and infinity.
   - Test bitrate parsing and unit conversion, including overflow and malformed units.
   - Test fallback bitrate estimation and verify parser bitrate takes precedence.
   - Test independent omission of invalid dimensions and blank codec.
   - Test that a missing or unsupported file does not throw or emit video keys.
   - If a tiny MP4 fixture can be generated using existing Java/test dependencies or checked in under the repository's fixture conventions, verify end-to-end Tika extraction. Do not add FFmpeg as a test prerequisite. If no stable fixture is practical, inject or factor the metadata-to-MessagePak conversion so it can be tested with a constructed Tika `Metadata` object.

8. Run verification:
   - Run `mvn -q -DskipTests test-compile`.
   - Run the new video tests plus existing registry, type-description, caching, type-resolving extractor, audio, and image focused tests in headless mode.
   - Run the full headless test suite and document pre-existing environment failures separately from feature regressions.

9. Persist negative extraction results:
   - Treat a present empty `MessagePak` from `FileMetadataCache.get(...)` as a cache hit, distinct
     from `Optional.empty()`.
   - Write a cache entry after extraction even when no profile-owned fields were produced.
   - Keep empty entries out of the file-stat payload and invalidate them using the normal cache key
     and expiry rules.

### 10. Tests to write

- `TestDefaultMetadataTypeRegistry`
  - Resolves normalized `video` id and lists the profile once.
- `TestDefaultTypeDescriptionListMetadataTypeId`
  - Resolves bundled `MOOV` to metadata type id `video`.
- `TestVideoFileItem`
  - Calls enrichment with `MetadataType.VIDEO`, preserves base fields, exposes enriched fields, and enriches once.
- `TestTikaVideoMetadataExtractor`
  - Skips AVI/MKV case-insensitively without accessing file contents and leaves the typed MessagePak empty.
  - Converts Tika duration, dimensions, codec, and data rate to protocol values.
  - Uses average bitrate only when parser bitrate is absent.
  - Omits invalid values and tolerates missing/unsupported files.
- `TestClientVideoHandleObject`
  - Defines the expected columns and widths.
  - Formats file-list and search metadata identically.
  - Displays `1920x1080`, `1:42:18`, codec text, and readable bitrate units.
  - Uses numeric sortable values and displays `-` for folders or malformed/missing values.
- Existing integration tests
  - Select `VideoFileItem` and `ClientVideoHandleObject` through an explicit `video` subscription.
  - Preserve existing audio/image/generic behavior and cache routing.
  - Verify an empty video result survives a fresh disk-cache instance and does not invoke extraction again.

### 11. Docs / Javadoc to update

- `MetadataType` - include `VIDEO` with the built-in compatibility constants if those constants remain documented.
- `BuiltInMetadataType` - no per-enum-value Javadoc required, but keep class documentation accurate for all built-in profiles.
- `VideoFileItem` - document each protocol key, exact data type/unit, optional behavior, and bitrate estimation semantics.
- `TikaVideoMetadataExtractor` - document Tika's best-effort format coverage and that unsupported formats leave fields absent.
- `ClientVideoHandleObject` - document the four appended columns and their source protocol keys.
- `docs/impl_summary/video-metadata-type.md` - create during implementation with completed changes, extraction limitations, fixture coverage, and verification results.
