# Implementation Summary - Metadata Type Registry

## What changed

Implemented the metadata registry refactor from `docs/plans/metadata-type-provider.md`.

Concrete `MysterType`s now subscribe to runtime metadata behavior through an optional stable
metadata type id on `TypeDescription`. Built-in `MPG3` carries `audio`; built-in `PICT` carries
`image`. The old `StandardTypes` metadata branch path was removed.

## Files changed

- `com.myster.type.TypeDescription` - added normalized optional metadata type id.
- `com.myster.type.TypeDescriptionList` - removed `getType(StandardTypes)`.
- `com.myster.type.DefaultTypeDescriptionList` - parses `Metadata Type` from bundled type data.
- `src/main/resources/com/myster/typedescriptionlist.mml` - marks `MPG3` as `audio`, `PICT` as `image`.
- `com.myster.filemanager.MetadataType` - converted from enum to runtime profile interface.
- `com.myster.filemanager.BuiltInMetadataType` - new built-in `generic`, `audio`, and `image` profiles.
- `com.myster.filemanager.MetadataTypeRegistry`, `DefaultMetadataTypeRegistry` - registry and concrete Myster type resolution model.
- `com.myster.filemanager.*MetadataExtractor` - renamed file enrichment classes away from `Provider`.
- `com.myster.filemanager.FileTypeList` and `FileTypeListManager` - resolve file item creation through metadata profiles.
- `com.myster.search.ui.ClientInfoFactoryUtils` - resolves handlers through metadata profiles.
- `com.myster.Myster` - builds the typed extractor map from `MetadataTypeRegistry.supportedTypes()`.
- Tests updated/added for registry lookup, resolver fallback, explicit built-in metadata ids, and renamed extractors.

## Key decisions

- Removed the planned `StandardType` concept entirely. It duplicated `MetadataType`.
- Kept `TypeDescription` data-oriented by storing only a string id, not a `MetadataType` object.
- Kept source-compatible `MetadataType.AUDIO` and `MetadataType.IMAGE` constants by making them interface constants backed by built-in profiles.
- Kept generic fallback behavior for missing, blank, or unknown metadata type ids.

## Deviations

- `StandardTypes.java` was deleted rather than renamed because no call sites remained after metadata resolution moved to `TypeDescription.getMetadataTypeId()`.
- GUI constructors were not expanded to carry a registry. `ClientInfoFactoryUtils` has a registry overload plus a default registry path for existing callers.

## Verification

- Passed: `mvn -q -DskipTests test-compile`
- Passed: `mvn -q -Djava.awt.headless=true -Dtest=TestDefaultMetadataTypeRegistry,TestMetadataTypeRegistryResolution,TestTypeDescriptionMetadataTypeId,TestDefaultTypeDescriptionListMetadataTypeId,TestClientInfoFactoryUtils,TestFileTypeList,TestCachingFileMetadataExtractor,TestTypeResolvingFileMetadataExtractor,TestMPG3FileItem,TestImageFileItem,TestClientImageHandleObject,TestTikaImageMetadataExtractor test`

Full suite command run:

- `mvn -q -Djava.awt.headless=true test`

The full suite did not pass because of pre-existing/environment-sensitive failures outside this refactor:

- `TestMultiSourceDownload` writes under `/home/andrew/.myster/Incoming/testFilename.p` and reports the file as read-only.
- `TestCustomTypeManager` hits Java Preferences file-lock failures, plus one related delete assertion.
- `TestAsyncDatagramSocket` times out waiting for UDP socket binds.

## Follow-up

- Add access-list operations for custom/private type metadata type ids if custom types should subscribe to `audio`, `image`, or future metadata profiles.
- Consider injecting `MetadataTypeRegistry` into UI/window providers instead of relying on the default registry in `ClientInfoFactoryUtils`.
