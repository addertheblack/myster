# Typed Search-Result Metadata

> **Related plan:** [Split Type Columns from Search-Only Columns](./split-type-columns-from-search-columns.md) — follow-up: gives the shared column handlers the same typed metadata input for search and browsing.

> **Related plan:** [MP3 Length Column in GUI](./mp3-length-column.md) — follow-up: replaces duration and other audio string parsing with typed reads.

> **Related plan:** [Image Metadata for Pictures](./image-metadata-for-pictures.md) — follow-up: removes the separate search parsing path while preserving the image field schema.

> **Related plan:** [Video Metadata Type](./video-metadata-type.md) — follow-up: removes the numeric-to-string adapter and reparsing in video columns.

> **Related plan:** [Metadata Type Registry Refactor](./metadata-type-provider.md) — follow-up: improves the read contract consumed by registry-selected column handlers.

> **Related plan:** [Custom Myster Type Metadata Profile](./custom-type-metadata-profile.md) — follow-up: the same typed handlers serve custom and bundled metadata profiles.

> **Related plan:** [Search-result thumbnails and hover previews](./os-thumbnails-part-5.md) — parallel work: preserves its EDT-confined result lifecycle, row identity, and search column decoration.

**Feature slug:** `typed-search-result-metadata`  
**Date:** 2026-11-02  
**Status:** Implemented; focused automated verification passed. Desktop smoke check remains manual.

**Implementation summary:** [Typed Search-Result Metadata](../impl_summary/typed-search-result-metadata.md)

## Design Section (for the owner/reviewer)

### 1. Summary

Expose search-result file stats through a read-only, typed MessagePak API. Values already arrive as typed MessagePak data; the current string-only search accessor discards those types and makes column handlers reconstruct numbers. Preserve the types at the search-result boundary and let generic, audio, image, and video columns share typed reading and formatting with direct browsing, without exposing mutation of the search result's backing stats.

### 2. Non-goals

- Changing file-stat keys, numeric units, transport sections, serialized formats, cache versions, or server metadata extraction.
- Introducing new columns, metadata profiles, search matching behavior, or date serialization.
- Replacing all mutable MessagePak uses across the repository or changing the public `FileRecord` component type in this milestone.
- Redesigning search scheduling, row identity, thumbnail loading, or download behavior.
- Removing the existing string accessor or adding a general numeric-string coercion layer.

### 3. Assumptions & open questions

- Requested: handlers must have typed getters without mutation authority over search file stats.
- Owner-confirmed: the original `MessagePak` remains the combined read-write API with unchanged semantics. Extracting a separately usable read interface is allowed; altering existing getters, setters, conversion, exceptions, aliasing, or serialization behavior is not.
- Implemented choices: introduce a common read interface plus a defensive wrapper; retain the keyed string getter as a compatibility convenience; use a non-null empty view until stats arrive.
- Search and browsing should use identical type-specific formatting and sorting for the same correctly typed metadata. Existing missing-value sentinels and display conventions remain, including audio's existing unknown labels.
- The current producers publish numeric fields as longs and text as strings. A numeric-looking string is a type mismatch, not a number to parse. Supporting peers that actually publish numeric strings would require an explicit compatibility policy; this plan does not assume such a requirement.
- No blocking question remains. The implementation follows these choices; preservation of the original MessagePak semantics is an owner requirement.

### 4. Proposed design

Extract a separately usable read interface while retaining `MessagePak` as the combined read-write API. A new `ReadOnlyMessagePak` interface declares the existing read operations, including typed scalar and array getters, generic reads, directory inspection, and serialization. `MessagePak` extends it and keeps its existing declarations, factories, mutation methods, default string conversion, and behavior in place. Existing mutable serializers remain unchanged and usable wherever read access is needed. Defensive copying belongs only to the new protected view; direct calls to the original API retain their existing reference-return and error semantics.

A read interface alone is insufficient: returning the mutable serializer under a narrower declared type permits a downcast, and returning a stored date or byte array permits mutation through the getter. Add a wrapper implementing only the read interface. It delegates reads, copies returned mutable values recursively, and never exposes its backing MessagePak. Obtain it through `MessagePak.asReadOnly()` and cache it when stats are installed, rather than allocating a wrapper for every cell.

`SearchResult` gains a no-argument typed metadata accessor. Its existing keyed accessor becomes a default convenience backed by the read view and keeps its nullable string result. Handlers use the typed accessor exclusively. An empty view represents stats not yet received; once the existing EDT publication installs stats, subsequent cell reads see the new view. Items must not capture the initial empty view permanently.

Image and video each get one typed formatting path shared by search and browsing. Audio likewise shares its extra-column conversion. Numeric fields feed numeric sortables directly; strings use string getters. File names, columns, widths, human-readable formats, and search-only Server/Ping decoration remain as they are.

### 5. Architecture connections

Server-side file items and metadata extractors already create typed fields. File-stat responses decode through `MessagePak.fromBytes`, then `StandardMysterSearch.dealWithFileStats` publishes each response to its existing result on the EDT and notifies the listener. The result will retain a protected read view of that response. Column handlers read the current view when cells are requested and construct the same sortable values used by direct client browsing. The metadata registry still selects handlers; this change adds no type-name branches.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `ReadOnlyMessagePak` read capability | `com.myster.mml`; implemented by serializers and protected views | Search API and column conversion helpers | Existing MessagePak getters and their Optional return types |
| Protected live read view | Created by `MessagePak.asReadOnly()`; retained by the result for its installed stats | Search-result consumers | Existing mutable decoded stats; defensive copies of returned mutable values |
| Typed metadata accessor | `MysterSearchResult`; initialized empty, replaced through `setFileStats` | Generic/audio/image/video search items | Existing EDT publication and `searchStats` notification |
| Shared typed column conversion | Each existing column handler | Search rows and `FileRecord` rows | Existing numeric/string sortables and `FileTypeColumnHandler` factories |
| Compatibility string accessor | Default method on `SearchResult` | Existing callers needing formatted text | Existing MessagePak string conversion semantics |

There is no on-wire or on-disk change. `/size`, `/BitRate`, `/Hz`, `/LengthSec`, image dimensions/orientation/bit depth/capture millis, and video numeric fields stay longs; title/artist/album/camera/codec stay strings. `/ImageTakenAtMillis` remains epoch millis. The existing serializer writes `Date` as a long and decodes it as a long, so exposing `getDate` does not promise a Date round trip or justify changing a published field's type.

### 6. Key decisions & edge cases

- **Enforce the boundary at runtime as well as in signatures.** Search getters return a wrapper that is not a `MessagePak`, has no mutation or unwrap API, and detaches returned dates, arrays, and nested mutable values. Hiding setters alone is not sufficient.
- **A live view is not an immutable snapshot.** Legitimate writes through the owning MessagePak are visible through its view. Search publication replaces the current view as a unit on the EDT. Retained older views still describe their original backing pack; row objects re-read the result's accessor to observe replacement. Do not serialize/decode to construct views, which would add work and change Date representation.
- **Preserve the original read-write API.** Do not add defensive copying, coercion, validation, exception handling, or immutability to existing MessagePak methods or serializer implementations. Add the read superinterface and view factory without changing existing behavior; keep the current default `get` implementation and conversion helper on MessagePak.
- **Preserve reader semantics.** The wrapper delegates typed reads without converting types or broadly catching exceptions. `newEmpty()` supplies the strict writer, whose mismatched reads can throw; `fromBytes()` supplies the robust reader, whose expected type/branch mismatches return empty Optionals. Column tests for malformed remote data must exercise the robust reader.
- **Missing stats are normal.** Return a protected empty pack before arrival and when cleared. The compatibility string getter returns null, and the key list is empty. Present but unusable fields follow their existing unknown display/sort conventions; malformed remote types do not crash column sorting.
- **Use current metadata for current cells.** Avoid caching a result's initial empty view or permanently caching the first size if stats can be replaced. Computing the inexpensive size sortable from the current view removes that stale-value risk.
- **No silent schema broadening.** Do not parse numeric strings or ask `getString` for a numeric field. Text stored under a numeric key is unusable metadata, even when it looks decimal. Legacy stringification remains available only to callers explicitly choosing the keyed convenience method.
- **Scope of protection.** This protects reads made through the new search-result boundary. It does not revoke the producer's mutable reference, make the backing store independently thread-safe, or change every existing UI metadata holder such as `FileRecord`.

### 7. Acceptance criteria

- [x] Existing `MessagePak` callers retain the complete combined read-write API and its original getter/setter, conversion, exception, reference-return, and serialization semantics.
- [x] A search consumer can read long, string, boolean, floating-point, date, and array values through the read interface, without a string round trip.
- [x] The search metadata view cannot be cast to `MessagePak`; no getter exposes a mutable reference that can alter backing stats, including nested values reached through generic/object-array reads.
- [x] Before stats arrive, reads and key listing behave as empty metadata; the existing row displays newly installed/replaced stats after EDT publication.
- [x] Generic, audio, image, and video columns use typed numeric/text reads and share type-specific conversion between search and direct browsing.
- [x] Sorting uses raw numbers, including file sizes above 2 GiB, duration, pixel area, capture millis, and bitrate; supported values retain current formatting.
- [x] Absent and malformed remote metadata retain the existing unknown display behavior without exceptions during sorting.
- [x] The keyed string accessor keeps its previous formatted-string/null behavior, and existing key-list behavior is preserved.
- [x] No protocol/cache schema, metadata producer, search-only column, thumbnail, or download behavior changes.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- **NEW** `src/main/java/com/myster/mml/ReadOnlyMessagePak.java` — separate declarations of the existing read operations; no replacement conversion implementation.
- **NEW** `src/main/java/com/myster/mml/ReadOnlyMessagePakView.java` — package-private final wrapper, defensive copies, no backing-object access.
- `src/main/java/com/myster/mml/MessagePak.java` — extend the read interface; retain all existing declarations and implementations; add `asReadOnly()`.
- `src/main/java/com/myster/search/SearchResult.java` — typed `getMetaData()` plus default legacy `getMetaData(String)`.
- `src/main/java/com/myster/search/MysterSearchResult.java` — keep non-null read-only stats and existing EDT installation; preserve root-leaf key listing.
- `src/main/java/com/myster/search/ui/ClientGenericHandleObject.java` — typed file-size reads and remove obsolete parse/cache handling.
- `src/main/java/com/myster/search/ui/ClientMPG3HandleObject.java` — shared typed audio-column conversion for search and browsing.
- `src/main/java/com/myster/search/ui/ClientImageHandleObject.java` — one conversion method accepting `ReadOnlyMessagePak`; delete search parsing overload.
- `src/main/java/com/myster/search/ui/ClientVideoHandleObject.java` — one conversion method accepting `ReadOnlyMessagePak`; delete string adapter and reparsing.
- `src/main/java/com/myster/search/ui/FileTypeColumnHandler.java` — document typed read expectations for search/file column implementations.
- **NEW** `src/test/java/com/myster/mml/TestReadOnlyMessagePak.java` — read parity, reader semantics, mutation protection, and live-view tests.
- **NEW** `src/test/java/com/myster/search/TestMysterSearchResult.java` — empty, installed, replaced, and compatibility-accessor behavior on the EDT.
- Existing `src/test/java/com/myster/search/ui/TestClient{Generic,MPG3,Image,Video}HandleObject.java` — real typed fixtures.
- **NEW** `src/test/java/com/myster/search/ui/TestTypedMetadataColumns.java` and `MetadataColumnTestUtils.java` — shared typed/decoded fixtures, search/browse parity, replacement, malformed data, and numeric sorting.
- `docs/conventions/myster-important-patterns.md`, this plan, and the eventual matching implementation summary — document the read boundary and validation.

### 9. Step-by-step implementation

1. **Extract the read capability while preserving the original API in place.** In `ReadOnlyMessagePak.java`, declare the existing read signatures: `getString`, scalar getters, typed array getters, `getObjectArray`, `getValue`, `get`, `list`, `isAValue`, `isADirectory`, and `toBytes`. Declare `get` as an abstract read operation. Have `MessagePak extends ReadOnlyMessagePak`, retaining its current read declarations alongside all write declarations, factories, default `get` implementation, and private conversion helper. Add only the new default `ReadOnlyMessagePak asReadOnly()` factory. Do not move or rewrite the conversion implementation, alter existing return/reference semantics, or modify either serializer. The serializers satisfy the read superinterface through their current methods; this is an additive capability extraction.

2. **Implement a protected view, not another serializer.** Have `asReadOnly()` instantiate `ReadOnlyMessagePakView(this)`. Keep the constructor and class package-private, the delegate private/final, and expose no unwrap. Delegate immutable scalar getters and `get(String)` directly, preserving the original string conversion without duplicating it. Copy `Date` values, every returned primitive/String array, and object arrays recursively. `getValue` must apply the same copy policy, including maps nested inside decoded object arrays and their mutable descendants. Preserve array runtime types where applicable. Directory lists must be detached/unmodifiable; serialization must return independent bytes. Use a single private copying helper for mutable return values. Support the serializer's value forms (including null array elements); never return an unrecognized mutable object by reference—reject unsupported object forms with a documented `IllegalArgumentException`. Cyclic arbitrary Java object graphs are outside the MessagePak tree contract. Do not add synchronization claims or change strict/robust error behavior.

3. **Expose the typed search API.** Add required `ReadOnlyMessagePak getMetaData()` to `SearchResult`; do not provide a default that reconstructs a pack from keyed strings. Make `getMetaData(String key)` a default method returning `getMetaData().get(key).orElse(null)`. Keep `getKeyList()` and unrelated search APIs. In `MysterSearchResult`, replace the nullable mutable field with a non-null read-only field initialized from a private protected empty pack. Inside the existing `Util.invokeNowOrLater` publication, install `m.asReadOnly()`; preserve `setFileStats(null)` as clearing to the empty view. Read that field from the typed accessor and key-list implementation. Preserve root-level leaf selection and leading slashes in key lists, including exclusion of directory nodes. No new worker, volatile field, or listener is needed: document the existing EDT access requirement.

4. **Migrate generic file size.** In `GenericSearchItem.getValueOfColumn(1)`, obtain the current metadata and call `getLong("/size")`, using the existing absent-size sortable for an empty Optional. Remove string parsing and the stale first-size cache. For a wrong-typed remote size, the robust getter also returns empty, so it follows the unknown-size path; there is no separate parse-error state after this change. Preserve the browsing path's current absent-size default of zero and folder size overrides. Leave name handling unchanged.

5. **Share typed media conversion.** Add an audio extra-column helper in `ClientMPG3HandleObject` accepting `ReadOnlyMessagePak`, use `getLong` for bitrate, Hz, and duration, and `getString` for tag fields. Preserve existing fallbacks and context-specific blank-text handling where it currently differs. Invoke it with `result.getMetaData()` and `record.metaData()`. In `ClientImageHandleObject`, change the existing MessagePak overload to `ReadOnlyMessagePak` and route both contexts through it; delete the SearchResult overload and `parseLong`. In `ClientVideoHandleObject`, change `videoColumn` to accept `ReadOnlyMessagePak`, use positive-filtered `Optional<Long>` reads directly, and retain existing string normalization. Delete `MetadataValues`, `MessagePakValues`, and `parsePositiveLong`. Keep all existing sortable validation, overflow behavior, units, labels, column ordering and widths. Remove imports and obsolete parsing comments/catches from touched code; follow braces and exception conventions.

6. **Update test consumers and documentation.** Replace string-map SearchResult test doubles with Mockito stubs of the new accessor backed by real typed packs, per the repository's mock convention. Include tests that fail if a handler falls back to `getMetaData(String)` or stringifies through `get`. Re-scan all production and test SearchResult implementors/callers; update thumbnail tests only if they rely on metadata or keyed getter stubs. Apply relevant standing refactors only in files already touched; none of the listed legacy utility renames is required by this plan.

7. **Validate and record results.** Compile all production/test sources, then run the focused suites below. Run `git diff --check` and inspect the diff for accidental protocol or unrelated edits. Broaden testing only for failures or new concerns. On implementation, write `docs/impl_summary/typed-search-result-metadata.md` and update plan status. The planning turn itself makes no Java edits and runs no test suites.

### 10. Tests to write

- **Read view parity:** cover every exposed getter with writer-backed and decoded-reader-backed fixtures; verify Optional results, integral normalization (`putInt`/`putShort` readable through `getLong`), floating-point reads, directory probes/listing, and serialized bytes. Verify strict mismatch exceptions remain strict and robust mismatches remain empty; do not turn numbers into text to satisfy a test.
- **Mutation protection:** assert the returned view is not a `MessagePak`; mutate returned Date, bytes, every typed array family, object arrays with nested Date/arrays/maps, and generic `getValue` results, then re-read the source and show it is unchanged. Include a decoded array containing a map with mutable descendants. Check null elements and unsupported-object rejection. Returned serialized bytes/list operations cannot alter the source. A subsequent legitimate owner write is visible through the same live view.
- **Date limitation:** use a direct `putDate` fixture to prove `getDate` and defensive copying. Separately confirm the existing round trip yields an epoch long; do not introduce a failing expectation that decoding restores Date.
- **Search lifecycle:** construct `MysterSearchResult` with test mocks and an ordinary stub; use `SwingUtilities.invokeAndWait`/an EDT drain for installation. Test initial empty values/list, typed installation, replacement observed by an already-created row, clear-to-empty, read-only runtime type, keyed string/null compatibility, and root-leaf key listing. Do not add a production test-only constructor or use sleeps.
- **Handler parity and sorting:** migrate the four existing handler suites to real long/string metadata. For the same valid pack, compare search/browse media display strings and raw sortable values. Assert numeric ordering for values such as 2 and 10 (not lexical ordering), file sizes above 2 GiB, and each media numeric column. Preserve existing duration, image-date, bitrate, and folder formatting tests. Verify an already-created generic row observes newly installed and replaced size values.
- **Malformed remote values:** decode fixture bytes through `MessagePak.fromBytes` before use, then exercise absent fields, wrong scalar types, numeric-looking strings, branches where leaves are expected, blank text, and invalid/nonpositive numeric values according to each sortable's current rules. Numeric strings stay unknown rather than being reparsed. Do not use a strict writer with wrong-typed fields and then expect robust behavior.
- **Compatibility:** run the existing strict/robust serializer and decode-limit suites unchanged. Verify direct MessagePak reads retain their original mutable-reference behavior while protected-view reads detach those same values. Assert the keyed getter still stringifies scalar/date/array values as before and returns null for absent values, with the view delegating `get(String)` to the original implementation. Retain search-column decoration and existing thumbnail lifecycle coverage.

Completed implementation verification (JDK 26 as required by `pom.xml`; 105 focused tests passed):

```sh
mvn -q -DskipTests test-compile
mvn -q -Dtest=TestReadOnlyMessagePak,TestMessagePackSerializer,TestRobustMessagePackSerializer,TestMessagePakDecodeLimits,TestMysterSearchResult,TestClientGenericHandleObject,TestClientMPG3HandleObject,TestClientImageHandleObject,TestClientVideoHandleObject,TestTypedMetadataColumns,TestSortableLength,TestSearchThumbnails,TestSearchTabThumbnailLifecycle test
git diff --check
```

Manual smoke on a desktop (not run during implementation): search and directly browse Audio, Pictures, Video, and Generic profiles; sort their numeric columns, observe metadata appearing on existing rows, and check missing metadata and search-only Server/Ping columns. A custom type using one of those profiles should receive the same columns automatically.

### 11. Docs / Javadoc to update

- `ReadOnlyMessagePak` and `MessagePak.asReadOnly()` — capability versus protected view, live-view ownership, mutable-return copying, supported value trees, and preserved reader semantics.
- `ReadOnlyMessagePakView` — delegation and defensive-copy responsibilities, including generic/nested values and unsupported forms.
- `SearchResult.getMetaData()` / `getMetaData(String)` — non-null empty view, typed reads versus display conversion, nullable compatibility return, and metadata refresh expectations.
- `MysterSearchResult.setFileStats` and accessors — EDT publication/read contract, null clearing, and view lifetime across replacement.
- Handler/`FileTypeColumnHandler` comments — correct typed getter for each schema field and shared conversion path.
- `docs/conventions/myster-important-patterns.md` — document the implemented boundary, preserved reader semantics, and Date/display conversion limitations.
- Link this plan and validation from `docs/impl_summary/typed-search-result-metadata.md` during implementation; retain the reciprocal related-plan links and plan index.
