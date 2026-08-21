# Image Metadata for Pictures

## Summary

Implemented Tika-backed metadata extraction for the built-in Pictures (`PICT`) type using the
same metadata-provider and persistent-cache framework that audio metadata uses. Picture file
stats now have an `IMAGE` metadata namespace and optional keys for dimensions, bit depth,
capture timestamp, orientation, camera make/model, and software. Search and direct client
browsing now select a Pictures-specific column handler with sortable `Resolution`, `Taken`,
`Camera`, `Orientation`, and `Bit Depth` columns.

## Files changed

- `pom.xml` - added `tika-parser-image-module`.
- `src/main/java/com/myster/type/StandardTypes.java` - added `PICT`.
- `src/main/java/com/myster/filemanager/MetadataType.java` - added `IMAGE`.
- `src/main/java/com/myster/filemanager/ImageFileItem.java` - new image-aware file item.
- `src/main/java/com/myster/filemanager/TikaImageMetadataProvider.java` - new Tika image provider.
- `src/main/java/com/myster/filemanager/FileTypeList.java` - creates `ImageFileItem` for Pictures.
- `src/main/java/com/myster/Myster.java` - registers the image provider.
- `src/main/java/com/myster/search/ui/ClientImageHandleObject.java` - new picture column handler.
- `src/main/java/com/myster/search/ui/ClientInfoFactoryUtils.java` - renamed utility and routes `PICT`.
- `src/main/java/com/myster/client/ui/ClientWindow.java`, `src/main/java/com/myster/search/ui/SearchTab.java`, `src/main/java/com/myster/search/ui/FileTypeColumnHandler.java` - updated factory references.
- `docs/conventions/standing-refactors.md` - removed the completed `ClientInfoFactoryUtilities` rename.
- `docs/plans/image-metadata-for-pictures.md` - kept the plan current with the resolved camera-column decision.
- New/updated tests under `src/test/java/com/myster/filemanager/` and `src/test/java/com/myster/search/ui/`.

## Key decisions

- GPS/location metadata is intentionally omitted.
- Capture time is stored as `/ImageTakenAtMillis` epoch millis for stable sorting.
- Camera make/model are stored separately and displayed as one compact `Camera` column.
- PNG/GIF/BMP-style images use Tika `ImageParser`; JPEG and TIFF use the format-specific Tika parsers.
- Tika/ImageIO may return bit depth as whitespace-separated values such as `8 8 8`; the provider accepts that shape but omits bit depth when any component is invalid.

## Deviations from plan

- The provider now sets `Metadata.CONTENT_TYPE` before invoking `ImageParser`; Tika's generic image parser returns without dimensions when content type is absent.
- `JpegParser` and `TiffParser` are selected by extension immediately instead of waiting for later fixture testing, because they are the narrow module's intended metadata-extractor path for EXIF-heavy formats.

## Tests

Passed:

- `mvn test -Dtest=TestMPG3FileItem,TestCachingMetadataProvider,TestTypeResolvingMetadataProvider,TestClientMPG3HandleObject,TestClientGenericHandleObject,TestClientImageHandleObject,TestClientInfoFactoryUtils,TestImageFileItem,TestTikaImageMetadataProvider`
- `mvn test -Djava.awt.headless=true -Dtest=TestFileTypeList`

Full-suite check:

- `mvn test -Djava.awt.headless=true` was run and reached 476 tests, but failed in unrelated environment-sensitive tests:
  - `TestMultiSourceDownload` cannot write `/home/andrew/.myster/Incoming/testFilename.p`.
  - `TestCustomTypeManager` hit Java preferences file-lock errors plus one delete assertion.
  - `TestAsyncDatagramSocket` could not bind sockets.

## Known follow-up

- Add a small stable JPEG EXIF fixture if we want direct coverage of camera/date extraction from real EXIF data rather than helper-level coverage plus generated PNG dimension coverage.
- Manual smoke test with a real shared Pictures folder is still useful to inspect column widths with large camera strings and mixed missing metadata.
