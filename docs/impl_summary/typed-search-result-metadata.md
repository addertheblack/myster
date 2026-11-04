# Typed Search-Result Metadata

**Plan:** [Typed Search-Result Metadata](../plans/typed-search-result-metadata.md)

## Implemented

Search results now expose protected typed metadata through `SearchResult.getMetaData()`.
Generic, Audio, Image and Video columns read numbers and strings directly and share media
conversion with browsing. The keyed string accessor remains available for display compatibility.

`MessagePak` retains its complete combined read-write API, default display conversion and
original semantics. Its only API additions are the read superinterface and `asReadOnly()`.
Neither serializer implementation was changed by this work. Defensive copying is confined
to the protected view and does not alter direct mutable getters or their references.

## Files changed

- New `com.myster.mml.ReadOnlyMessagePak` declares existing read signatures.
- New package-private `ReadOnlyMessagePakView` delegates reads and recursively detaches mutable
  dates, arrays and maps nested inside object arrays; it exposes no mutation/unwrap API.
- `MessagePak` adds the superinterface and view factory, retaining existing declarations/bodies.
- `SearchResult` adds the typed accessor and supplies the legacy keyed accessor as a default.
- `MysterSearchResult` publishes a protected view on the EDT, starts with empty metadata, supports
  clearing, and preserves root-level leaf key listing.
- The four `Client*HandleObject` column handlers consume typed metadata; image/audio/video
  conversion is shared by search and browsing. Generic search size no longer caches stale stats.
- `FileTypeColumnHandler` documents typed/current-view expectations.
- New `TestReadOnlyMessagePak`, `TestMysterSearchResult`, `TestTypedMetadataColumns` and shared
  `MetadataColumnTestUtils`; the four existing handler suites use typed wire fixtures and mocks.
- The plan, important-patterns convention and implementation-summary index document the change.

## Decisions and edge cases

- Read-interface extraction is additive: existing MessagePak callers keep the original read/write,
  error, aliasing and serialization behavior. The protected wrapper itself is not a MessagePak.
- Views are live, inherit their owner's threading requirements and retain their original pack
  across search-result replacement. Cell providers fetch the result's current view each time.
- Strict writer mismatches still throw; decoded robust reader mismatches still return empty
  Optionals. Numeric-looking strings are unusable numeric metadata and are not reparsed.
- Mutable values from typed/generic getters are detached recursively. Unknown arbitrary Java
  objects are rejected by protected value/object-array reads; cyclic Java graphs are outside
  the supported MessagePak tree contract.
- Compatible object-array runtime types are retained. If detached concrete map/Date elements no
  longer fit a covariant component type, the view returns a detached Object array instead.
- Existing audio blank-tag behavior is preserved: search keeps blanks while browsing shows `-`.
  Existing missing-size differences between search and browsing also remain.
- Date wire representation remains epoch millis. `getDate` works for stored Date values but
  does not make Date round-trip through the codec. Display conversion is delegated unchanged,
  including its existing exception for object arrays containing null elements.

## Verification

- `mvn -q -DskipTests test-compile` passed.
- Focused suite: **105 tests, 0 failures, 0 errors, 0 skipped**, covering the protected view,
  unchanged strict/robust serializer and decode-limit suites, search publication/replacement,
  four handlers, numeric ordering, malformed remote fields, duration formatting and search
  thumbnail/lifecycle integration:

```sh
mvn -q -Dtest=TestReadOnlyMessagePak,TestMessagePackSerializer,TestRobustMessagePackSerializer,TestMessagePakDecodeLimits,TestMysterSearchResult,TestClientGenericHandleObject,TestClientMPG3HandleObject,TestClientImageHandleObject,TestClientVideoHandleObject,TestTypedMetadataColumns,TestSortableLength,TestSearchThumbnails,TestSearchTabThumbnailLifecycle test
```

- `git diff --check` passed.
- Review found a concrete-array defensive-copy issue; it was fixed, regression-tested with
  TreeMap/Timestamp/String arrays, and re-reviewed with no remaining findings.

## Documentation and remaining checks

Javadoc documents protected-view versus capability semantics, ownership, EDT publication,
replacement, unsupported values, Date encoding and typed column access. Reviewed existing design
documents; none of their protocol or thumbnail contracts became stale. Updated
`docs/conventions/myster-important-patterns.md` with the implemented metadata boundary.

No architectural deviation from the plan. Shared column regression cases were consolidated in
one parameterized suite rather than repeated across all four handler suites.

Desktop/multi-peer visual smoke checks were not run. Remaining manual check: search and directly
browse Generic, Audio, Pictures, Video and a custom metadata profile; verify displayed formats,
metadata arrival, sorting and unchanged Server/Ping columns. Automated coverage verifies those
cell values and search lifecycle behavior without opening desktop windows.

The full repository test suite was not run; the focused suites cover this change. No additional
automated tests are currently deferred. Existing user edits in `TODO.txt` and serializer
whitespace were preserved.
