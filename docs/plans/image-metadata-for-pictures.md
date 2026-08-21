# Image Metadata for Pictures

**Feature slug:** `image-metadata-for-pictures`  
**Date:** 2026-08-21  
**Status:** Ready for implementation

## 1. Summary

Add Tika-backed metadata extraction for the built-in `PICT` / Pictures type by extending the existing metadata-provider and cache framework used by `MPG3` sound files. Picture file stats will expose compact, commonly useful image browsing fields such as pixel dimensions, bit depth, capture date, orientation, and camera model, and the search and direct-client file lists will show sortable picture columns using the existing per-type column-handler framework.

## 2. Non-goals

- Do not change the file-search matching algorithm; image metadata will be displayed and sortable, not query-indexed.
- Do not add thumbnails, preview rendering, or image-content recognition.
- Do not add OCR, face detection, color analysis, duplicate detection, or perceptual hashes.
- Do not expose GPS/location metadata in this milestone. GPS is useful for old-photo discovery, but it is privacy-sensitive in a peer-to-peer file-sharing protocol and should be a separate explicit decision.
- Do not add a user preference UI for choosing picture metadata columns.
- Do not change the generic file stats protocol shape, the section 77 / 177 transport flow, or existing audio metadata keys.
- Do not broaden image support beyond files already included in the `PICT` type description.

## 3. Assumptions & open questions

- The built-in picture type already exists in `src/main/resources/com/myster/typedescriptionlist.mml` with internal name `PICT` and description `Pictures`, but `src/main/java/com/myster/type/StandardTypes.java` currently only exposes `MPG3` and `MOOV`. The implementation should add `PICT` to this enum so code can safely identify the standard Pictures type.
- The current production metadata graph is already generic enough for another type: `MPG3FileItem` asks `CachingMetadataProvider` to enrich `MetadataType.AUDIO`, `CachingMetadataProvider` caches only keys declared by `MetadataType`, and `TypeResolvingMetadataProvider` routes to a typed extractor.
- Tika 3.3.1 is already in the local Maven cache with `org.apache.tika:tika-parser-image-module:3.3.1`, and that module contains `org.apache.tika.parser.image.ImageParser`, `JpegParser`, and `TiffParser`. Add the narrow image parser module to `pom.xml`; do not switch to the broad `tika-parsers-standard-package` unless testing proves the narrow module insufficient.
- Tika exposes the core image fields through `org.apache.tika.metadata.TIFF` and `TikaCoreProperties`. The implementation should verify each key with small JPEG/PNG/TIFF fixtures because parser output can vary by format.
- `MessagePak` supports `Date`, but `SearchResult.getMetaData(String)` turns values into strings through `MessagePak.get(...)`. For consistent search-window sorting, the first milestone should store capture time as a long epoch millis key rather than a raw `Date`.
- Camera make/model are shown as one combined `Camera` column, backed by separate `/CameraMake` and `/CameraModel` protocol keys.

## 4. Proposed design

Mirror the existing sound metadata architecture instead of creating a separate image path.

On the server side, `FileTypeList.FileListIndexCall#createFileItem(...)` will create an `ImageFileItem` when the indexed type is `StandardTypes.PICT`. `ImageFileItem` will behave like `MPG3FileItem`: call `super.getMessagePackRepresentation()` for generic fields such as `/size`, `/path`, and hashes, then call the injected `MetadataProvider` to enrich the same `MessagePak` with `MetadataType.IMAGE`.

The production provider graph in `Myster.createMetadataProvider()` will register both typed providers:

```text
CachingMetadataProvider
  -> ShardedFileMetadataCache
  -> TypeResolvingMetadataProvider
       AUDIO -> TikaAudioMetadataProvider
       IMAGE -> TikaImageMetadataProvider
```

`MetadataType.IMAGE` will define the cache namespace and the exact picture keys allowed into the persistent cache. The initial cache key should be `image-v1`; if the emitted image schema changes later, bump it intentionally.

The first user-facing metadata set should stay compact and useful for browsing old images:

| MessagePak key | Type | Source priority | UI use |
|---|---:|---|---|
| `/ImageWidth` | `long` | `TIFF.IMAGE_WIDTH` | Resolution column |
| `/ImageHeight` | `long` | `TIFF.IMAGE_LENGTH` | Resolution column |
| `/ImageBitDepth` | `long` | derived from `TIFF.BITS_PER_SAMPLE` and `TIFF.SAMPLES_PER_PIXEL` | Bit Depth column |
| `/ImageTakenAtMillis` | `long` | `TIFF.ORIGINAL_DATE`, then `TikaCoreProperties.CREATED`, then `TikaCoreProperties.MODIFIED` | Taken column |
| `/ImageOrientation` | `long` | `TIFF.ORIENTATION` | Orientation column |
| `/CameraMake` | `String` | `TIFF.EQUIPMENT_MAKE` | Camera column |
| `/CameraModel` | `String` | `TIFF.EQUIPMENT_MODEL` | Camera column |
| `/ImageSoftware` | `String` | `TIFF.SOFTWARE` / `TikaCoreProperties.CREATOR_TOOL` | File stats detail only, not a default column |

The default columns for Pictures should be:

1. `File Name`
2. `File Size`
3. `Resolution` - displayed as `4032 x 3024`, sorted by pixel count or width/height tuple
4. `Taken` - displayed as a local short date/time, sorted by epoch millis
5. `Camera` - combined make/model, string sorted
6. `Orientation` - displayed as EXIF orientation value or a small friendly label
7. `Bit Depth` - displayed as `24-bit`, sorted numerically

This favors old-photo discovery without crowding the UI with every possible EXIF field. Width/height and date are the strongest practical signals; camera and orientation help distinguish scanned photos, phone photos, and rotated camera originals; bit depth is useful for scans, PNGs, and edited assets.

## 5. Architecture connections

Sound metadata was added in three layers:

- `MPG3FileItem` owns when audio enrichment happens.
- `MetadataType.AUDIO`, `CachingMetadataProvider`, `TypeResolvingMetadataProvider`, and `TikaAudioMetadataProvider` own extraction and persistent cache behavior.
- `ClientMPG3HandleObject` and `ClientInfoFactoryUtils` own how those keys become columns in `SearchTab` and `ClientWindow`.

Picture metadata should reuse the same three layers. The only new protocol surface is a set of optional root-level `MessagePak` keys in file stats. Missing values remain absent; clients must render missing picture metadata as `-`.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `StandardTypes.PICT` | `com.myster.type.StandardTypes` | `FileTypeList`, `ClientInfoFactoryUtils` | Existing `typedescriptionlist.mml` built-in `PICT` type |
| `MetadataType.IMAGE` | `com.myster.filemanager.MetadataType` | `CachingMetadataProvider`, `FileMetadataCacheKey`, `ImageFileItem` | Existing metadata cache namespace and allowed-key filtering |
| `ImageFileItem` | `com.myster.filemanager` | `FileTypeList.FileListIndexCall#createFileItem(...)` | Existing `FileItem` base stats and `MetadataProvider` injection |
| `TikaImageMetadataProvider` | `com.myster.filemanager` | `TypeResolvingMetadataProvider` for `MetadataType.IMAGE` | Tika `ImageParser`, `TIFF`, `TikaCoreProperties`, `MessagePak` |
| Provider registration for `IMAGE` | `Myster.createMetadataProvider()` | App startup / file indexing | Existing production provider graph |
| `ClientImageHandleObject` | `com.myster.search.ui` | `ClientInfoFactoryUtils` | Existing `FileTypeColumnHandler`, `SearchColumnDecorator`, `ClientWindow` |
| Image sortable helpers | `com.myster.search.ui` or package-private nested classes | `ClientImageHandleObject` | Existing `SortableLong` / `SortableString` conventions |

Data flow in plain English: when a Pictures file is indexed, Myster stores an `ImageFileItem` for that path. When a remote client asks for file stats through section 77 or 177, the file item builds generic stats, then asks the metadata-provider chain for `MetadataType.IMAGE`. The cache either supplies prior image metadata or delegates to Tika. The resulting `MessagePak` travels through the existing file stats response. On the client, the same `ClientImageHandleObject` is used by `SearchTab` and `ClientWindow` to define columns and turn the optional keys into sortable display values.

## 6. Key decisions & edge cases

- **Use one combined `Resolution` column.** Width and height are stored separately for protocol clarity, but the UI should not consume two columns for dimensions.
- **Use epoch millis for the capture date.** This keeps both direct-client and search-result sorting numeric and avoids parsing locale-dependent `Date.toString()` / `SimpleDateFormat` output.
- **Derive bit depth conservatively.** If Tika returns multiple bits-per-sample values, sum them. If it returns one bits-per-sample value and samples-per-pixel, multiply them. If only one value is available, store that value. If inputs are absent or invalid, omit `/ImageBitDepth`.
- **Missing metadata is normal.** PNGs, GIFs, screenshots, scanned files, and stripped JPEGs often lack capture date, camera model, orientation, or bit depth. Do not log warnings for absent fields; only log parse/open failures.
- **Do not let Tika failures break indexing.** `ImageFileItem` should still return generic `FileItem` stats even when image parsing fails.
- **Privacy: omit GPS.** Tika exposes latitude/longitude through `TikaCoreProperties`, but this milestone intentionally does not store or display them.
- **File type detection stays extension-based.** The PICT type definition decides which files are indexed. Tika may fail on unsupported or malformed files under that type, and that should only result in missing image metadata.
- **Standing refactor applied.** The legacy `ClientInfoFactoryUtilities` helper is renamed to `ClientInfoFactoryUtils` while routing Pictures to the image handler.

## 7. Acceptance criteria

- [ ] Files under the built-in Pictures / `PICT` type are represented by an image-aware file item during indexing.
- [ ] Picture file stats include `/ImageWidth` and `/ImageHeight` when Tika can extract dimensions.
- [ ] Picture file stats include `/ImageBitDepth`, `/ImageTakenAtMillis`, `/ImageOrientation`, `/CameraMake`, `/CameraModel`, and `/ImageSoftware` only when valid values are available.
- [ ] Image metadata is cached under a separate `MetadataType.IMAGE` namespace and cache misses do not affect the existing audio cache.
- [ ] Search window results for Pictures show the configured image columns plus `Server` and `Ping`.
- [ ] Direct client browsing for Pictures shows the same type columns without `Server` and `Ping`.
- [ ] Missing image metadata displays as `-` and does not throw during sorting.
- [ ] Malformed or unreadable image files still return generic `/size` and `/path` stats.
- [ ] GPS/location metadata is not emitted.
- [ ] Existing MPG3 metadata tests and column behavior continue to pass.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `pom.xml` - add `org.apache.tika:tika-parser-image-module:3.3.1` beside the existing Tika dependencies.
- `src/main/java/com/myster/type/StandardTypes.java` - add `PICT`.
- `src/main/java/com/myster/filemanager/MetadataType.java` - add `IMAGE("image-v1", List.of(...))`.
- `src/main/java/com/myster/filemanager/ImageFileItem.java` - **NEW** file item subclass that enriches `MetadataType.IMAGE`.
- `src/main/java/com/myster/filemanager/TikaImageMetadataProvider.java` - **NEW** typed metadata provider for image metadata extraction.
- `src/main/java/com/myster/filemanager/FileTypeList.java` - instantiate `ImageFileItem` for `tdList.getType(StandardTypes.PICT)`.
- `src/main/java/com/myster/Myster.java` - register `MetadataType.IMAGE` with `new TikaImageMetadataProvider()` in the provider map.
- `src/main/java/com/myster/search/ui/ClientImageHandleObject.java` - **NEW** picture column handler.
- `src/main/java/com/myster/search/ui/ClientInfoFactoryUtils.java` - route Pictures to `ClientImageHandleObject`; renamed from `ClientInfoFactoryUtilities` per standing refactor.
- `src/main/java/com/myster/search/ui/SearchTab.java` - update import/reference for the factory rename.
- `src/main/java/com/myster/client/ui/ClientWindow.java` - update import/reference for the factory rename.
- `src/test/java/com/myster/filemanager/TestImageFileItem.java` - **NEW** provider-injection and message-pack behavior tests.
- `src/test/java/com/myster/filemanager/TestTikaImageMetadataProvider.java` - **NEW** parser helper/failure tests and at least one real small image fixture test.
- `src/test/java/com/myster/filemanager/TestTypeResolvingMetadataProvider.java` - extend to prove `MetadataType.IMAGE` routes independently.
- `src/test/java/com/myster/search/ui/TestClientImageHandleObject.java` - **NEW** UI column count, formatting, missing-value, and search/client item tests.
- `src/test/java/com/myster/search/ui/TestClientMPG3HandleObject.java` and any compile-affected tests - update only if the factory rename changes references.

## 9. Step-by-step implementation

1. **Add the Tika image parser dependency.**
   - In `pom.xml`, add:

   ```xml
   <!-- Contains Tika's image parsers and metadata-extractor integration -->
   <dependency>
     <groupId>org.apache.tika</groupId>
     <artifactId>tika-parser-image-module</artifactId>
     <version>3.3.1</version>
   </dependency>
   ```

   - Keep existing `tika-core`, `tika-parser-audiovideo-module`, and `slf4j-jdk14`.
   - Run a focused Maven test compile after adding it. If Maven cannot resolve the dependency in a clean environment, use the same Tika version already chosen for audio and do not substitute the broad parser package unless necessary.

2. **Expose Pictures as a standard type.**
   - Add `PICT` to `StandardTypes`.
   - Use `tdList.getType(StandardTypes.PICT)` for comparisons, matching the existing `MPG3` style.
   - Account for tests with custom `TypeDescriptionList` implementations; they may need to return a value for `PICT` or only call the new branch when the type exists.

3. **Add the image metadata namespace.**
   - Extend `MetadataType`:

   ```java
   IMAGE("image-v1",
       List.of("/ImageWidth",
               "/ImageHeight",
               "/ImageBitDepth",
               "/ImageTakenAtMillis",
               "/ImageOrientation",
               "/CameraMake",
               "/CameraModel",
               "/ImageSoftware"))
   ```

   - Keep audio keys unchanged.
   - Use `image-v1` because this is a new cache schema. Future changes to emitted image keys or units should bump the schema key.

4. **Create `ImageFileItem`.**
   - Follow `MPG3FileItem` structure:
     - constructor takes `Path root`, `Path path`, and `MetadataProvider`
     - holds a `MessagePak messagePackRepresentation` RAM cache
     - `getMessagePackRepresentation()` calls `super.getMessagePackRepresentation()`
     - calls `metadataProvider.enrich(MetadataType.IMAGE, messagePackRepresentation, getPath())`
   - Add class Javadoc documenting the image metadata protocol keys and value types.
   - Consider moving the shared `putIfNotBlank(...)` helper out of `MPG3FileItem` only if needed by both audio and image; a package-private static helper is acceptable, but do not refactor audio more than necessary.

5. **Create `TikaImageMetadataProvider`.**
   - Implement `TypedMetadataProvider`.
   - Use `BufferedInputStream` plus Tika `ImageParser` first:
     - `new ImageParser().parse(in, new DefaultHandler(), metadata, new ParseContext())`
   - If testing shows `ImageParser` misses JPEG EXIF fields that `JpegParser` provides, branch by extension or detected media type to `JpegParser` for JPEG and `TiffParser` for TIFF; keep this behind the same `TikaImageMetadataProvider` contract.
   - Catch `IOException`, `SAXException`, and `TikaException`, log a warning, and return without throwing.
   - Do not warn for absent metadata fields.

6. **Map Tika metadata to `MessagePak`.**
   - Dimensions:
     - `metadata.getInt(TIFF.IMAGE_WIDTH)` -> `/ImageWidth`
     - `metadata.getInt(TIFF.IMAGE_LENGTH)` -> `/ImageHeight`
     - write only positive values.
   - Bit depth:
     - `metadata.getIntValues(TIFF.BITS_PER_SAMPLE)`
     - `metadata.getInt(TIFF.SAMPLES_PER_PIXEL)`
     - derive using the conservative rule from section 6.
   - Capture date:
     - first non-null `metadata.getDate(TIFF.ORIGINAL_DATE)`, then `metadata.getDate(TikaCoreProperties.CREATED)`, then `metadata.getDate(TikaCoreProperties.MODIFIED)`
     - write `/ImageTakenAtMillis` as `date.getTime()`.
   - Orientation:
     - parse `metadata.get(TIFF.ORIENTATION)` as a positive long if possible.
   - Camera:
     - `metadata.get(TIFF.EQUIPMENT_MAKE)` -> `/CameraMake`
     - `metadata.get(TIFF.EQUIPMENT_MODEL)` -> `/CameraModel`
   - Software:
     - prefer `metadata.get(TIFF.SOFTWARE)`, fallback `metadata.get(TikaCoreProperties.CREATOR_TOOL)` -> `/ImageSoftware`.
   - Do not write:
     - latitude, longitude, altitude
     - raw EXIF dumps
     - empty strings
     - sentinel values

7. **Hook Pictures into the crawler/indexer.**
   - Update `FileTypeList.FileListIndexCall#createFileItem(...)`.
   - Replace the current ternary with a small helper or if/else so both `MPG3` and `PICT` are readable:
     - `MPG3` -> `new MPG3FileItem(rootPath, path, metadataProvider)`
     - `PICT` -> `new ImageFileItem(rootPath, path, metadataProvider)`
     - otherwise -> `new FileItem(rootPath, path)`
   - Keep hash lookup behavior unchanged.
   - If `tdList.getType(StandardTypes.PICT)` could throw in custom test lists, isolate the comparison in a helper that treats unknown standard types as non-matches.

8. **Register the provider.**
   - In `Myster.createMetadataProvider()`, change the map to include both entries:
     - `MetadataType.AUDIO` -> `new TikaAudioMetadataProvider()`
     - `MetadataType.IMAGE` -> `new TikaImageMetadataProvider()`
   - Keep the cache root unchanged: `MysterGlobals.getPrivateDataPath()/MetadataCache`.

9. **Create sortable display helpers for image columns.**
   - Add package-private helpers in `ClientImageHandleObject` unless reuse justifies separate files.
   - Recommended helpers:
     - `SortableResolution(long width, long height)` displays `width + " x " + height`, sorts by `width * height`, then width, then height; missing displays `-`.
     - `SortableTimestamp(long epochMillis)` displays a concise local date/time, sorts numerically; missing displays `-`.
     - `SortableBitDepth(long bits)` displays `bits + "-bit"`, sorts numerically; missing displays `-`.
     - `SortableOrientation(long orientation)` displays a friendly label for EXIF values 1, 3, 6, and 8 if desired, otherwise the numeric value; missing displays `-`.
   - Keep formatting deterministic enough for tests. Use an explicit `DateTimeFormatter` rather than platform default formatting.

10. **Create `ClientImageHandleObject`.**
    - Extend `ClientGenericHandleObject`, as `ClientMPG3HandleObject` does.
    - Extra headers and suggested widths:
      - `Resolution` - 110
      - `Taken` - 140
      - `Camera` - 170
      - `Orientation` - 90
      - `Bit Depth` - 80
    - Extra key array can include raw keys:
      - `/ImageWidth`, `/ImageHeight`, `/ImageTakenAtMillis`, `/CameraMake`, `/CameraModel`, `/ImageOrientation`, `/ImageBitDepth`
    - Implement both `getSearchItem(SearchResult)` and `getFileItem(FileRecord)` because search reads strings through `SearchResult.getMetaData(...)`, while direct browsing can use typed `MessagePak` access.
    - `getFolderItem(String)` should delegate base columns and return `SortableString("-")` for every image-specific column.

11. **Route the image column handler and apply standing refactor.**
    - Rename `ClientInfoFactoryUtilities` to `ClientInfoFactoryUtils`.
    - Update imports and references in `SearchTab`, `ClientWindow`, and any tests.
    - In the factory:
      - if type is `MPG3`, return `ClientMPG3HandleObject`
      - if type is `PICT`, return `ClientImageHandleObject`
      - otherwise return `ClientGenericHandleObject`
    - Keep search-only `Server` and `Ping` behavior in `SearchColumnDecorator`; do not add those to the image handler.

12. **Verify protocol and UI behavior manually.**
    - Start two local Myster instances or use the existing manual workflow for browsing a local server if available.
    - Share a folder under Pictures with at least:
      - JPEG with EXIF date/camera
      - PNG screenshot
      - GIF or malformed image
    - Confirm file stats are present through direct browsing and search.
    - Confirm sorting works for Resolution, Taken, and Bit Depth.
    - Confirm missing fields display as `-`.

## 10. Tests to write

- `TestImageFileItem`
  - `getMessagePackRepresentation_usesInjectedProvider()` verifies `MetadataType.IMAGE`, existing `/size`, and path passed to provider.
  - `getMessagePackRepresentation_keepsMessagePakRamCache()` mirrors the MPG3 RAM-cache test.
  - `getMessagePackRepresentation_returnsGenericStatsWhenProviderWritesNothing()`.

- `TestTikaImageMetadataProvider`
  - helper-level tests for positive integer parsing / omission rules if helpers are package-visible.
  - bit-depth derivation tests:
    - `[8, 8, 8]` -> `24`
    - `8` with `3` samples -> `24`
    - invalid or missing values -> omitted
  - `doesNotThrowForNonExistentFile()`.
  - `leavesMessagePakWithoutImageKeysOnParseFailure()`.
  - fixture test with a tiny PNG or JPEG generated/stored in test resources that proves width and height extraction.
  - fixture test for EXIF date/camera if a small stable fixture is practical; otherwise keep date/camera covered by metadata-mapping helper tests.

- `TestTypeResolvingMetadataProvider`
  - add a case proving `MetadataType.IMAGE` routes to a distinct provider and does not invoke the audio provider.

- `TestCachingMetadataProvider`
  - existing tests should naturally cover any metadata type, but add or parameterize one case for `MetadataType.IMAGE` to prove allowed-key filtering does not cache generic keys.

- `TestClientImageHandleObject`
  - column count and headers.
  - direct `FileRecord` formatting for resolution, taken date, camera, orientation, and bit depth.
  - `SearchResult` formatting from string metadata.
  - missing values display `-`.
  - folder rows show `-` for image-specific columns.

- Existing regression tests to run:
  - `mvn test -Dtest=TestMPG3FileItem,TestCachingMetadataProvider,TestTypeResolvingMetadataProvider,TestClientMPG3HandleObject,TestClientGenericHandleObject,TestClientImageHandleObject,TestFileTypeList`
  - If dependency changes affect the build, run the full `mvn test`.

## 11. Docs / Javadoc to update

- `ImageFileItem` class Javadoc should document every picture `MessagePak` key, type, unit, and omission rule.
- `TikaImageMetadataProvider` Javadoc should state that it intentionally omits GPS/location metadata.
- `MetadataType` Javadoc can mention that each enum value is a cache schema namespace and must be bumped on incompatible key/unit changes.
- `ClientImageHandleObject` should document the derived UI columns, especially that `Resolution` is backed by `/ImageWidth` and `/ImageHeight`.
- Add a short implementation summary in `docs/impl_summary/image-metadata-for-pictures.md` after implementation is complete.
