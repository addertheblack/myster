# Metadata Type Registry Refactor

**Feature slug:** `metadata-type-provider`  
**Date:** 2026-08-21  
**Status:** Ready for implementation

## 1. Summary

Refactor Myster's file metadata/type-specific behavior so a concrete `MysterType` explicitly subscribes to one metadata implementation by stable metadata type id, and file item creation, metadata extraction/cache schema, and GUI column selection are all selected through a `MetadataTypeRegistry` and per-type `MetadataType` interface.

## 2. Non-goals

- Do not change the file stats protocol keys or payload shape.
- Do not add a new user-facing metadata implementation such as Movies in this milestone.
- Do not change how file search matching works.
- Do not add a UI for choosing a custom type's metadata type in this milestone.
- Do not add plugin loading or runtime discovery for external metadata implementations yet.
- Do not rewrite the existing Tika audio/image extractors except where needed to fit the new interface.

## 3. Assumptions & open questions

- The planned `StandardType` concept is redundant with `MetadataType`. There should be one metadata behavior concept, not a selector enum plus an implementation object.
- The existing `StandardTypes` enum currently names concrete built-in Myster network types (`MPG3`, `MOOV`, `PICT`). It should not become the metadata-category model. Metadata behavior should stop depending on it.
- A concrete `MysterType` is the network/file-sharing identity. A `MetadataType` is the implementation profile for metadata extraction, cache schema, file item creation, and GUI columns. Many `MysterType`s may subscribe to one `MetadataType`.
- `TypeDescription` should store an optional stable metadata type id string such as `audio` or `image`, not a Java enum. This keeps the type metadata data-oriented and allows future metadata implementations without editing a central enum.
- For this milestone, custom/private types keep generic metadata and generic columns unless their `TypeDescription` has an explicit metadata type id.
- The access-list model is the canonical metadata store for custom/private types, so adding metadata type subscription for custom types requires an optional access-list state field and operation. If that is too large for the implementation pass, populate the new field only from bundled default type data and document access-list support as follow-up.
- Rename the existing `FileMetadataExtractor` interface to `FileMetadataExtractor` because it enriches one file's metadata payload. `MetadataTypeRegistry` is the registry/factory for metadata implementation profiles.

## 4. Proposed design

Use `MetadataType` as the single concept that represents a metadata implementation.

- `TypeDescription` carries an optional metadata type id: `Optional<String> getMetadataTypeId()`.
- The bundled `typedescriptionlist.mml` stores this explicitly with a new `Metadata Type` field. Missing values mean generic behavior.
- `MetadataTypeRegistry` exposes `MetadataType get(String metadataTypeId)` and `MetadataType generic()`.
- `MetadataType` becomes an interface implemented by concrete metadata profiles. A profile owns its stable id, cache schema, typed extractor, `FileItem` subclass creation, and `FileTypeColumnHandler` selection.
- A default registry supplies built-in `generic`, `audio`, and `image` profiles.

The flow becomes:

- Server/indexing code reads the selected `TypeDescription.getMetadataTypeId()`, asks the registry for a `MetadataType`, and calls `createFileItem(...)`.
- Metadata cache/enrichment code continues to receive a `MetadataType`, but now that object is a profile chosen from type-description data rather than an enum branch.
- GUI code reads the same metadata type id, asks the registry for the profile, and uses its `FileTypeColumnHandler`.

This keeps extraction and display behavior together while avoiding a redundant `StandardType` enum. The stable id in `TypeDescription` is the data contract; the `MetadataType` object is the runtime implementation.

## 5. Architecture connections

The refactor centralizes type-specific decisions currently spread across `FileTypeList`, `ClientInfoFactoryUtils`, `Myster.createFileMetadataExtractor()`, the current `MetadataType` enum, and `TypeResolvingFileMetadataExtractor`.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `TypeDescription.metadataTypeId()` | `com.myster.type` | `FileTypeList`, `ClientInfoFactoryUtils`, type editors/tests | Explicitly records the metadata implementation id subscribed to by this concrete `MysterType` |
| `typedescriptionlist.mml` `Metadata Type` field | bundled resources | `DefaultTypeDescriptionList` | Stores built-in metadata subscriptions without relying on internal names |
| `MetadataType` interface | `com.myster.filemanager` | cache, metadata extractors, file item creation, GUI factory | Replaces current `MetadataType` enum as the type-specific behavior profile |
| `MetadataTypeRegistry` | `com.myster.filemanager` | `FileTypeList`, `FileTypeListManager`, GUI factory, app startup | Registry/factory keyed by stable metadata type id |
| `DefaultMetadataTypeRegistry` | `com.myster.filemanager` | production startup and default constructors | Supplies built-in generic, audio, and image profiles |
| `MetadataTypeResolver` or equivalent helper | `com.myster.filemanager` | `FileTypeList`, GUI factory | Maps `TypeDescription.metadataTypeId()` to a `MetadataType`, with generic fallback |
| `FileTypeList.FileListIndexCall` | `com.myster.filemanager` | server-side indexing | Delegates `FileItem` creation to selected `MetadataType` |
| `ClientInfoFactoryUtils` | `com.myster.search.ui` | `SearchTab`, `ClientWindow` | Delegates column handler selection to selected `MetadataType` |
| `Myster.createFileMetadataExtractor()` | `com.myster` | app startup | Replaces `createFileMetadataExtractor()` and builds `TypeResolvingFileMetadataExtractor` from registry-supplied typed extractors |

The built-in type description resource gains one optional metadata field per type:

- `<Metadata Type>audio</Metadata Type>` for the built-in `MPG3` type.
- `<Metadata Type>image</Metadata Type>` for the built-in `PICT` type.
- Other built-in types omit the field and remain generic until a metadata profile is implemented.

No file stats protocol changes are intended. Cache entries continue to be keyed by the metadata type cache key (`audio-v1`, `image-v1`) plus file path, size, and last modified time. Existing file stats keys such as `/BitRate`, `/Hz`, `/ImageWidth`, and `/ImageTakenAtMillis` remain unchanged.

Plain-English data flow: the user enables a concrete Myster file type, indexing starts, and `FileTypeList` reads that type's `TypeDescription.getMetadataTypeId()`. For Pictures, the bundled `PICT` type description says `image`, so the image profile creates an `ImageFileItem`, which asks the cache/enrichment layer for image metadata. The same image profile later gives the client UI a `ClientImageHandleObject`, so the metadata keys emitted by the server and the columns displayed by the GUI are selected by the same explicit type subscription.

## 6. Key decisions & edge cases

- **Remove the redundant selector enum:** Do not introduce `StandardType`. The metadata type id in `TypeDescription` plus the `MetadataTypeRegistry` is enough.
- **Keep data and implementation separate:** `TypeDescription` stores only a stable string id. It does not depend on `com.myster.filemanager.MetadataType`, so the general type model stays data-oriented.
- **Stable ids are lowercase ASCII:** Built-in ids should be `generic`, `audio`, and `image`. Cache keys stay separate (`audio-v1`, `image-v1`) so implementation schema changes can invalidate cache without changing type subscriptions.
- **Generic fallback:** Unknown, blank, or missing metadata type ids must use generic behavior: plain `FileItem`, generic columns, no typed extractor, and no typed metadata cache writes.
- **Many `MysterType`s per metadata type:** Multiple concrete Myster network types can store the same metadata type id, so a future private "Family Photos" type can subscribe to `image` without becoming the built-in `PICT` type.
- **Registry map completeness:** If the registry has no implementation for an id, callers should fall back to generic behavior rather than throwing during indexing or UI setup. A warning is acceptable for unknown non-blank ids loaded from persisted type metadata.
- **Cache compatibility:** Built-in audio and image cache keys must remain exactly `audio-v1` and `image-v1`; otherwise existing metadata caches are invalidated unexpectedly.

## 7. Acceptance criteria

- [ ] Metadata behavior no longer uses a separate `StandardType`/`StandardTypes` selector.
- [ ] `TypeDescription` exposes an optional metadata type id, and metadata behavior is selected from that field rather than from `internalName`.
- [ ] Built-in `typedescriptionlist.mml` explicitly marks `MPG3` as `audio` and `PICT` as `image`.
- [ ] File item creation for audio and pictures is selected through `MetadataType.createFileItem(...)`, not direct checks in `FileTypeList`.
- [ ] GUI column handlers for audio and pictures are selected through `MetadataType.getHandler(...)`, not direct checks in `ClientInfoFactoryUtils`.
- [ ] Metadata extractor registration in app startup is derived from the default metadata type registry instead of manually constructing a hardcoded `Map.of(...)` in `Myster`.
- [ ] Existing audio and image metadata cache keys and protocol keys are unchanged.
- [ ] Generic, unknown, blank, or missing metadata type ids use generic file items, generic columns, and no typed metadata extraction.
- [ ] Existing MPG3 and Pictures metadata tests continue to pass.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- `src/main/java/com/myster/type/StandardTypes.java` - delete if it has no remaining callers after branch removal, or rename to a concrete built-in type concept only if another caller still needs built-in Myster type lookup. Do not use it for metadata behavior.
- `src/main/java/com/myster/type/TypeDescription.java` - add optional metadata type id field and getter.
- `src/main/java/com/myster/type/TypeDescriptionList.java` - remove or de-emphasize `getType(StandardTypes t)` if no longer needed; callers should use `get(MysterType)` and `TypeDescription.getMetadataTypeId()` for metadata behavior.
- `src/main/java/com/myster/type/DefaultTypeDescriptionList.java` - parse built-in `Metadata Type` fields; build custom descriptions with optional metadata type id when available.
- `src/main/java/com/myster/type/CustomTypeDefinition.java` - add optional metadata type id only if access-list custom type subscription is included in this milestone.
- `src/main/resources/com/myster/typedescriptionlist.mml` - add explicit `Metadata Type` entries for built-in metadata-aware types.
- `src/main/java/com/myster/filemanager/MetadataType.java` - convert from enum to interface with id, cache schema, and type behavior methods.
- `src/main/java/com/myster/filemanager/BuiltInMetadataType.java` or equivalent - **NEW** built-in implementation enum/class for generic, audio, and image behavior.
- `src/main/java/com/myster/filemanager/MetadataTypeRegistry.java` - **NEW** registry interface keyed by metadata type id.
- `src/main/java/com/myster/filemanager/DefaultMetadataTypeRegistry.java` - **NEW** default registry for built-in metadata profiles.
- `src/main/java/com/myster/filemanager/MetadataTypeResolver.java` - **NEW** helper for mapping `MysterType` and `TypeDescriptionList` to a `MetadataType`.
- `src/main/java/com/myster/filemanager/FileTypeList.java` - replace direct built-in `MPG3`/`PICT` checks with selected `MetadataType.createFileItem(...)`.
- `src/main/java/com/myster/filemanager/FileTypeListManager.java` - accept/pass `MetadataTypeRegistry`.
- `src/main/java/com/myster/filemanager/FileMetadataExtractor.java` - rename to `FileMetadataExtractor`.
- `src/main/java/com/myster/filemanager/TypedMetadataExtractor.java` - rename to `TypedMetadataExtractor`.
- `src/main/java/com/myster/filemanager/TikaAudioMetadataExtractor.java` - rename to `TikaAudioMetadataExtractor`.
- `src/main/java/com/myster/filemanager/TikaImageMetadataExtractor.java` - rename to `TikaImageMetadataExtractor`.
- `src/main/java/com/myster/filemanager/TypeResolvingFileMetadataExtractor.java` - route by `MetadataType` to registry-derived typed extractors.
- `src/main/java/com/myster/filemanager/CachingFileMetadataExtractor.java` - continue using `MetadataType.cacheKey()` and `cacheableKeys()` from the interface.
- `src/main/java/com/myster/filemanager/FileMetadataCacheKey.java` - no behavioral change; update Javadoc/imports if needed after `MetadataType` becomes an interface.
- `src/main/java/com/myster/search/ui/ClientInfoFactoryUtils.java` - delegate to `MetadataTypeResolver` and `MetadataType.getHandler(...)`.
- `src/main/java/com/myster/Myster.java` - construct one default registry and pass it to file manager, metadata extractor creation, and UI construction where needed.
- Tests under `src/test/java/com/myster/**` - update references to the old enum and add tests for metadata id resolution and built-in profiles.

### 9. Step-by-step implementation

1. Remove `StandardType` from the design:
   - Do not create a `StandardType` enum.
   - Remove metadata branch points that use `StandardTypes.MPG3` or `StandardTypes.PICT`.
   - If `StandardTypes` has no callers after this refactor, delete it and remove `TypeDescriptionList.getType(StandardTypes)`.
   - If another feature still needs built-in Myster type lookup, rename the enum to a singular concrete-name concept such as `BuiltInMysterType`, but keep it out of metadata resolution.

2. Make the metadata subscription explicit in type metadata:
   - Add `Optional<String> getMetadataTypeId()` to `TypeDescription`.
   - Store the field internally as a nullable/optional string normalized to lowercase ASCII.
   - Keep constructor overloads that default to empty to reduce churn.
   - Add a constructor overload accepting `String metadataTypeId` or `Optional<String> metadataTypeId`.
   - Update `TypeDescription` Javadoc to distinguish:
     - `MysterType`: concrete network/file-sharing identity.
     - metadata type id: stable key for runtime metadata behavior.
   - Update built-in `typedescriptionlist.mml`:
     - `MPG3` gets `<Metadata Type>audio</Metadata Type>`.
     - `PICT` gets `<Metadata Type>image</Metadata Type>`.
   - Update `DefaultTypeDescriptionList.getTypeDescriptionAtPath(...)` to parse `Metadata Type` with a safe helper. Missing values should be empty. Unknown-looking values should remain as ids, with validation left to the registry/resolver.
   - Stop using `internalName.equals(...)` for metadata behavior.

3. Convert `MetadataType` into an interface:
   - Preserve the cache methods currently on the enum:
     - `String cacheKey()`
     - `List<String> cacheableKeys()`
   - Add:
     - `String id()`
     - `FileTypeColumnHandler getHandler(TypeDescriptionList tdList)`
     - `FileItem createFileItem(Path root, Path path, FileMetadataExtractor metadataExtractor)`
     - `Optional<TypedMetadataExtractor> typedMetadataExtractor()`
   - Provide compatibility constants only if they materially reduce test churn. If constants remain, they should delegate to built-in implementation objects and not reintroduce enum branching.

4. Add built-in metadata type implementations:
   - Add `BuiltInMetadataType` as an enum or final class in `com.myster.filemanager`.
   - Implement profiles:
     - `GENERIC`: id `generic`, empty cache key or no typed extractor, empty cacheable keys, `new FileItem(...)`, `new ClientGenericHandleObject()`.
     - `AUDIO`: id `audio`, cache key `audio-v1`, existing audio cacheable keys, `new MPG3FileItem(...)`, `new TikaAudioMetadataExtractor()`, `new ClientMPG3HandleObject()`.
     - `IMAGE`: id `image`, cache key `image-v1`, existing image cacheable keys, `new ImageFileItem(...)`, `new TikaImageMetadataExtractor()`, `new ClientImageHandleObject()`.
   - Generic should not be registered with `TypeResolvingFileMetadataExtractor` because it has no typed extraction.

5. Add `MetadataTypeRegistry`:
   - Interface:
     - `MetadataType get(String metadataTypeId)`
     - `MetadataType generic()`
     - `Collection<MetadataType> supportedTypes()` or `Stream<MetadataType> supportedTypes()`
   - `DefaultMetadataTypeRegistry` should map:
     - `audio` -> audio
     - `image` -> image
     - missing, blank, unknown -> generic
   - Normalize lookup ids consistently: trim, lowercase ASCII, reject/ignore empty strings.

6. Add a resolver helper:
   - `MetadataTypeResolver.resolve(TypeDescriptionList tdList, MysterType type, MetadataTypeRegistry registry)`
   - It should call `tdList.get(type)`, read `TypeDescription.getMetadataTypeId()`, and ask the registry for that id.
   - It should return generic metadata type when the concrete Myster type is unknown or has no metadata type id.
   - It should not inspect `TypeDescription.getInternalName()`.
   - Keep this helper in `com.myster.filemanager` unless UI/package layering requires a narrower public surface.

7. Refactor indexing:
   - Update `FileTypeList` constructor and `FileListIndexCall` to receive `MetadataTypeRegistry`.
   - Resolve metadata type once for the `FileTypeList`'s `MysterType`, not once per file.
   - Replace `createFileItem(...)` branching with `metadataType.createFileItem(rootPath, path, metadataExtractor)`.
   - Keep the hash-provider callback unchanged.
   - Ensure `FileItem` caching behavior in `MPG3FileItem` and `ImageFileItem` remains unchanged.

8. Refactor file manager construction:
   - Update `FileTypeListManager` constructor to accept `MetadataTypeRegistry`.
   - Pass it into every `new FileTypeList(...)` call, including listener-driven type enablement.
   - Consider a backward-compatible constructor that uses `new DefaultMetadataTypeRegistry()` only if many tests would otherwise need irrelevant setup.

9. Refactor GUI handler selection:
   - Update `ClientInfoFactoryUtils.getHandler(...)` to resolve the metadata type and call `metadataType.getHandler(tdList)`.
   - Remove local `isStandardType(...)` branching from `ClientInfoFactoryUtils`.
   - Leave `ClientMPG3HandleObject` and `ClientImageHandleObject` behavior unchanged.

10. Rename and refactor production metadata extractor setup:
   - Change `Myster.createFileMetadataExtractor(...)` to accept `MetadataTypeRegistry`.
   - Build the extractor map by iterating `metadataTypeRegistry.supportedTypes()` and collecting `typedMetadataExtractor()` values.
   - Keep `new CachingFileMetadataExtractor(cache, resolver)`.
   - Construct a single `DefaultMetadataTypeRegistry` in `Myster.main(...)` and pass it to `createFileMetadataExtractor(...)` and `FileTypeListManager`.

11. Preserve tests and compatibility:
   - Update tests using `MetadataType.AUDIO` and `MetadataType.IMAGE` to use the new built-in constants or implementation enum.
   - Update fake `TypeDescriptionList` classes to return `TypeDescription` instances with explicit metadata type ids where audio/image behavior is expected.
   - Add focused tests before broad cleanup so failures identify resolver mistakes.

12. Decide custom/access-list subscription scope:
   - Minimum implementation: custom types default to no metadata type id, and the new `TypeDescription` field is populated only from bundled default type data.
   - Fuller implementation: add optional metadata type id to access-list state and `CustomTypeDefinition`, then propagate it into custom `TypeDescription` creation.
   - If using the fuller path, add a new access-list operation for metadata type subscription rather than storing it in Java Preferences, because access lists are the canonical type metadata store.

### 10. Tests to write

- `TestDefaultMetadataTypeRegistry`
  - `audio` returns audio profile.
  - `image` returns image profile.
  - Unknown, blank, and missing ids return generic profile.
  - `supportedTypes()` includes audio and image but excludes duplicate ids.

- `TestMetadataTypeResolver`
  - Resolves a `TypeDescription` carrying `audio` to audio.
  - Resolves a `TypeDescription` carrying `image` to image.
  - Returns generic for an unknown concrete Myster type.
  - Returns generic for a known type with no metadata type id.
  - Returns generic for a known type with an unregistered metadata type id.

- `TestTypeDescription`
  - Constructor overloads without a metadata type id return `Optional.empty()`.
  - Constructor overloads with `audio` return `Optional.of("audio")`.
  - Whitespace/case normalization is deterministic if normalization is done in `TypeDescription`; otherwise registry lookup normalization is covered in registry tests.

- `TestDefaultTypeDescriptionList`
  - Built-in `MPG3` type description carries metadata type id `audio`.
  - Built-in `PICT` type description carries metadata type id `image`.
  - Built-in types without `Metadata Type` remain valid and generic.

- `TestFileTypeList`
  - Audio metadata type indexing creates `MPG3FileItem` through the selected `MetadataType`.
  - Image metadata type indexing creates `ImageFileItem` through the selected `MetadataType`.
  - Generic metadata type indexing creates plain `FileItem`.

- `TestClientInfoFactoryUtils`
  - Audio handler is still `ClientMPG3HandleObject`.
  - Image handler is still `ClientImageHandleObject`.
  - Generic handler is still `ClientGenericHandleObject`.

- Existing tests to run:
  - `mvn test -Dtest=TestClientInfoFactoryUtils,TestFileTypeList,TestCachingFileMetadataExtractor,TestTypeResolvingFileMetadataExtractor,TestMPG3FileItem,TestImageFileItem,TestClientImageHandleObject`
  - If focused tests pass, run the full project test suite if practical.

### 11. Docs / Javadoc to update

- `MetadataType` - document that this is the type-specific behavior profile and cache schema identity.
- `MetadataTypeRegistry` - document that it maps stable metadata type ids to runtime metadata profiles.
- `TypeDescription` - document the explicit optional metadata type id and its relationship to concrete `MysterType`.
- `MetadataTypeResolver` - document fallback behavior when a type is unknown, has no metadata type id, or names an unregistered id.
- `FileTypeList` constructor/Javadoc - update to mention registry-driven metadata type resolution and profile-driven file item creation.
- `ClientInfoFactoryUtils` - update Javadoc to say handlers come from `MetadataType`, not local type branching.
