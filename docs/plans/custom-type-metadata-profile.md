# Custom Myster Type Metadata Profile

**Feature slug:** `custom-type-metadata-profile`  
**Date:** 2026-08-26  
**Status:** Ready for implementation; owner decisions confirmed

## 1. Summary

Allow a custom Myster Type to select exactly one metadata profile and persist that selection in its signed access-list chain, using Myster's extensible/type-safe enum pattern so known profiles resolve to local implementations while a profile introduced by a newer Myster version remains a specific, losslessly preserved unknown value.

## 2. Non-goals

- Do not compose several metadata profiles on one Myster Type in this milestone. A `MetadataType` currently owns one file-item factory and one complete column handler, and those behaviors are not composable.
- Do not add metadata extractors, file-stat protocol keys, cache schemas, or file-list columns.
- Do not infer a metadata profile from file extensions or inspect each file to choose a profile.
- Do not make the selection affect file matching, access policy, membership, or archive traversal.
- Do not let users edit bundled/default Myster Type definitions.
- Do not let users enter arbitrary serialized enum identifiers when creating a type.
- Do not store Generic explicitly in a new genesis block or during an unrelated edit. Generic is the absence/default association unless it must supersede an earlier non-Generic value.
- Do not duplicate shared type metadata in Java Preferences; Preferences remain an enabled/disabled index only.

## 3. Assumptions & open questions

- Owner-confirmed: a custom Myster Type selects exactly one profile. Supporting simultaneous Audio + Image + Video requires a separate composition design.
- Owner-confirmed: the association is shared type metadata, so it belongs in the signed access-list chain and travels with imported types.
- Owner-confirmed: selectable known values come from the active `MetadataTypeRegistry`, not hard-coded editor branches.
- Owner-confirmed: an unrecognized serialized value represents a specific real metadata type that this Myster version does not understand. It is not equivalent to Generic in the model, even though local runtime behavior falls back to Generic.
- Owner-confirmed: known and unknown choices must use user-friendly UI labels. Serialized enum identifiers are protocol/model data and must not be shown as the normal label.
- Existing access lists without an association resolve to `MetadataTypeId.GENERIC`. Saving an unchanged Generic selection writes no operation.
- No owner decision remains open.

## 4. Proposed design

Introduce `MetadataTypeId`, a serialized extensible-enum value distinct from `MetadataType`, the runtime implementation profile.

- `MetadataTypeId` extends the shared `com.general.util.TypeSafeEnum` base also used by `Role` and
  `OpType`: canonical singleton constants, a stable string identifier, `fromString(...)`,
  `isCanonical()`, and equality by concrete type and identifier. This version defines Generic,
  Audio, Image, and Video constants.
- A non-canonical `MetadataTypeId` preserves a well-formed identifier introduced by a newer Myster version. It remains distinguishable from Generic and from every other unknown value.
- `TypeDescription`, `CustomTypeDefinition`, and `AccessListState` carry `MetadataTypeId` instead of an optional raw string. Their default is `GENERIC`.
- `MetadataType.id()` returns the corresponding `MetadataTypeId`, and `MetadataTypeRegistry` resolves a `MetadataTypeId` to a runtime `MetadataType`. Non-canonical or otherwise unsupported ids resolve to the Generic implementation without changing the supplied id.
- The custom-type editor populates its selector from `MetadataTypeRegistry.supportedTypes()`. When editing an imported type with a non-canonical id, it adds that one preserved unknown selection to the model.
- Canonical values provide explicit friendly labels such as “Generic”, “Audio”, “Image”, and “Video”. Unknown values use a safe humanized label such as “Unknown metadata type — Spatial Audio”; the exact serialized identifier stays in the backing object, not in the normal UI label.

Generic uses default/clear semantics:

- A new custom type selecting Generic omits `SET_METADATA_TYPE` from genesis.
- An existing chain with no operation remains Generic after unrelated saves.
- Changing Audio/Image/Video/unknown to Generic appends `SET_METADATA_TYPE(generic)` so replay supersedes the earlier value.
- Once an explicit Generic operation exists, it stays in immutable history; no history rewrite or deletion occurs.

## 5. Architecture connections

Custom type metadata is authored in `TypeEditorPanel`, signed into `AccessList`, derived through `AccessListState`, and converted by `DefaultTypeDescriptionList` into runtime `TypeDescription` objects. The new extensible enum follows that path. At runtime, `FileTypeList` and the client UI already resolve the description through `MetadataTypeRegistry`, so a known custom association receives the same implementation as the corresponding bundled type without new branches.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `MetadataTypeId` extensible enum | `com.myster.type` | type descriptions, access-list state, runtime metadata registry, editor | Existing `Role`/`OpType` forward-compatible value pattern |
| `SET_METADATA_TYPE` operation | custom-type administrator; genesis only for non-Generic, appended on actual change | `BlockOperation`, `AccessListState` | Existing signed, append-only type metadata chain |
| `AccessListState.metadataTypeId` | access-list replay | `DefaultTypeDescriptionList`, editor | Defaults to Generic while preserving a specific unknown value |
| `CustomTypeDefinition.metadataTypeId` | access-list state or editor | custom type add/update/edit paths | Carries canonical state without Preferences storage |
| `TypeDescription.metadataTypeId` | bundled MML parser or custom state builder | `MetadataTypeRegistry` | Common association model for bundled and custom Myster Types |
| Metadata Profile selector | editor, populated from the active registry | custom-type creator/administrator | Existing admin-key read-only gate and access-list save flow |
| Friendly enum label | `MetadataTypeId` canonical metadata/fallback formatter | selector renderer and read-only editor | Keeps serialized identifiers out of ordinary presentation text |

The new access-list operation identifier is `SET_METADATA_TYPE`. Its payload is length-framed for compatibility with older readers:

- 4-byte signed payload byte length, bounded by the containing block payload;
- framed bytes containing one Java modified-UTF stable `MetadataTypeId` identifier;
- no trailing framed bytes.

This framing is required because an older client routes the unrecognized operation to `UnknownOp`, which reads a byte count and retains exactly that opaque payload. The older client can then validate, store, and reserialize the signed block byte-for-byte. A newer client parses the same bytes into `SetMetadataTypeOp` and `MetadataTypeId.fromString(...)`.

Plain-English create flow: the editor lists registry-supported profiles with friendly labels. Selecting a non-Generic profile places its id in genesis; selecting Generic writes nothing. Loading the chain derives a `MetadataTypeId`, builds the custom `TypeDescription`, and lets the existing registry-driven indexing and client-column paths select the implementation.

Plain-English import flow: if the serialized id is known, `fromString(...)` returns the canonical singleton and the registry supplies its implementation. If the id came from a newer version, `fromString(...)` creates a non-canonical value that retains that exact normalized identifier. The registry uses Generic behavior locally, while the editor shows a friendly Unknown choice backed by the preserved id. An unrelated edit does not replace it.

## 6. Key decisions & edge cases

- **Association value and implementation are separate.** `MetadataTypeId` is serializable data; `MetadataType` is executable local behavior. An unknown id can exist without a local implementation.
- **One profile is an invariant.** Do not store a list in anticipation of future composition.
- **Unknown is not Generic.** Both execute Generic behavior locally, but only Generic means “no specialized association.” Unknown retains identity so a newer client can resolve it later.
- **Generic is not normally serialized.** Omit the operation for new/default Generic state. Serialize Generic only as the last-operation-wins value that clears a prior specialized or unknown association.
- **Access list is authoritative.** Never write the enum value to `CustomTypeManager` or another Preferences node.
- **Known choices come from the registry.** The editor must not use a switch over Generic/Audio/Image/Video. Generic sorts first; remaining supported profiles sort by friendly label.
- **Unknown choices are contextual.** Users cannot invent an unknown id. The selector adds only the current imported/loaded non-canonical value, allowing it to be preserved or deliberately replaced with a supported choice.
- **UI text is presentation data.** Canonical labels are explicit. The unknown fallback humanizes safe identifier tokens, strips/control-escapes unsafe characters, bounds displayed length, and prefixes the result with “Unknown metadata type”. Swing must never interpret an untrusted identifier as HTML.
- **Identifiers remain lossless within the identifier contract.** Normalize using the documented stable-id rules before constructing the value; do not map an unknown valid id to Generic or a generic `UNKNOWN` singleton. Two different unknown ids remain unequal.
- **Malformed identifiers invalidate the operation.** Reject null/blank, excessive length, illegal characters, truncated data, and trailing bytes through the existing chain validation path.
- **Operation framing is mandatory.** The new payload must match `UnknownOp`'s byte-length convention. Compatibility tests prove opaque round-trip preservation.
- **Changing profiles does not rewrite caches.** The newly selected implementation uses its existing id/version namespace. Prior-profile entries remain harmless until ordinary cleanup.
- **Profile and extension mismatch is allowed.** A chosen extractor may return no typed fields for some files; no warning or compatibility enforcement is added.

## 7. Acceptance criteria

- [ ] A custom type creator can select exactly one profile from the active registry using friendly names.
- [ ] A new Generic custom type writes no metadata-type operation and resolves to Generic after save/reload.
- [ ] A new non-Generic custom type signs its stable `MetadataTypeId` into genesis and preserves it across restart/import.
- [ ] An authorized administrator changing a specialized/unknown association to Generic appends an explicit Generic operation that supersedes the prior value.
- [ ] Saving without changing the effective selection appends no metadata-type operation, including for legacy access lists with no operation.
- [ ] Known custom associations use their existing file item, extractor/cache schema, and client columns.
- [ ] A newer-version id becomes a distinct non-canonical `MetadataTypeId`, is preserved across replay/edit/save, and uses Generic local runtime behavior.
- [ ] The editor shows an unknown association as friendly Unknown text rather than a bare serialized enum identifier.
- [ ] Read-only/imported types show their current friendly selection but cannot edit it without the existing admin key.
- [ ] Older readers can preserve `SET_METADATA_TYPE` as an opaque unknown operation without changing serialized block bytes or signature validity.
- [ ] No profile association is written to Java Preferences.
- [ ] Existing bundled associations and legacy custom access lists continue to behave correctly.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `src/main/java/com/general/util/TypeSafeEnum.java` - **NEW**, reusable extensible-enum identity,
  canonical state, equality, indexing, and resolution behavior.
- `src/main/java/com/myster/type/MetadataTypeId.java` - **NEW**, `TypeSafeEnum` subtype with canonical
  values, metadata-id validation, unknown preservation, and friendly labels.
- `src/main/java/com/myster/type/TypeDescription.java` - replace optional raw metadata id with a Generic-defaulting `MetadataTypeId`.
- `src/main/java/com/myster/type/CustomTypeDefinition.java` - carry `MetadataTypeId` through constructors, accessors, equality, and diagnostics.
- `src/main/java/com/myster/type/DefaultTypeDescriptionList.java` - parse bundled ids and map access-list state into custom descriptions.
- `src/main/java/com/myster/access/OpType.java` - register canonical `SET_METADATA_TYPE`.
- `src/main/java/com/myster/access/BlockOperation.java` - dispatch the new operation.
- `src/main/java/com/myster/access/SetMetadataTypeOp.java` - **NEW**, immutable length-framed operation carrying one `MetadataTypeId`.
- `src/main/java/com/myster/access/AccessListState.java` - derive the last selected id, defaulting to Generic.
- `src/main/java/com/myster/access/AccessList.java` - optionally include a non-Generic selection in genesis while retaining the existing overload.
- `src/main/java/com/myster/filemanager/MetadataType.java` - return `MetadataTypeId` from `id()`.
- `src/main/java/com/myster/filemanager/BuiltInMetadataType.java` - use canonical `MetadataTypeId` constants.
- `src/main/java/com/myster/filemanager/MetadataTypeRegistry.java` - resolve typed ids, including non-canonical fallback.
- `src/main/java/com/myster/filemanager/DefaultMetadataTypeRegistry.java` - key implementations by typed id and preserve Generic fallback.
- `src/main/java/com/myster/filemanager/FileMetadataCacheKey.java` - convert the typed id to its stable string only at the cache serialization/key boundary.
- `src/main/java/com/myster/type/ui/TypeEditorPanel.java` - add the registry-driven, friendly metadata selector and persist actual changes.
- `src/main/java/com/myster/type/ui/TypeManagerPreferences.java` - accept/pass the production registry and update standalone setup.
- `src/main/java/com/myster/Myster.java` - pass the existing application registry into type preferences.
- `src/test/java/com/myster/type/TestMetadataTypeId.java` - **NEW**, canonical/unknown/equality/normalization/friendly-label coverage.
- `src/test/java/com/general/util/TestTypeSafeEnum.java` - **NEW**, shared resolution, equality, and
  canonical-index invariant coverage.
- `src/test/java/com/myster/type/TestTypeDescriptionMetadataTypeId.java` - update for typed Generic-default behavior.
- `src/test/java/com/myster/type/TestDefaultTypeDescriptionListMetadataTypeId.java` - update bundled typed-id expectations.
- `src/test/java/com/myster/type/TestCustomTypeDefinition.java` - **NEW**, typed association/default/equality coverage.
- `src/test/java/com/myster/type/TestDefaultTypeDescriptionListImport.java` - custom import/restart/prefs coverage.
- `src/test/java/com/myster/access/TestOpType.java` - new canonical operation id.
- `src/test/java/com/myster/access/TestBlockOperation.java` - framed operation and unknown-reader round trip.
- `src/test/java/com/myster/access/TestAccessListState.java` - Generic default and last-operation-wins behavior.
- `src/test/java/com/myster/access/TestAccessList.java` - genesis omission/inclusion, explicit Generic clear, storage replay, and validation.
- `src/test/java/com/myster/filemanager/TestDefaultMetadataTypeRegistry.java` - typed lookup and unknown fallback.
- `src/test/java/com/myster/filemanager/TestMetadataTypeRegistryResolution.java` - custom known/unknown resolution without identity loss.
- `src/test/java/com/myster/filemanager/TestFileMetadataCacheKey.java` - stable cache identity remains the same string.
- `src/test/java/com/myster/type/ui/TestTypeEditorPanelMetadataType.java` - **NEW**, registry choices, friendly rendering, unknown preservation, and save semantics.
- `docs/impl_summary/custom-type-metadata-profile.md` - **NEW during implementation**, delivered behavior and verification.

## 9. Step-by-step implementation

1. Implement the serialized `MetadataTypeId` extensible enum.
   - Add canonical singletons `GENERIC`, `AUDIO`, `IMAGE`, and `VIDEO` using the existing stable lowercase ids.
   - Follow the `Role`/`OpType` pattern: private constructor, known-value map, `fromString(String)`, `getIdentifier()`, `isCanonical()`, `equals`, and `hashCode` by identifier.
   - Define and document the stable identifier contract: trimmed/lowercase ASCII token, a conservative maximum length, and allowed separators. Reject malformed values rather than converting them to Generic.
   - Give canonical values explicit user-facing labels. Add a safe bounded humanizer for non-canonical values that returns `Unknown metadata type — <readable words>` without emitting HTML/control text or using the raw token as the whole label.
   - Keep protocol/log access through `getIdentifier()` separate from display access through `getDisplayName()`; do not make callers rely on `toString()` for UI text.

2. Replace stringly typed associations in the type model.
   - Store a non-null `MetadataTypeId` in `TypeDescription`; legacy constructors and missing MML values default to `GENERIC`.
   - Change the accessor to return `MetadataTypeId` directly and update Javadoc/call sites.
   - Parse bundled `Metadata Type` strings through `MetadataTypeId.fromString(...)`; MPG3/Image/MOOV become canonical Audio/Image/Video and missing entries become Generic.
   - Add `MetadataTypeId` to `CustomTypeDefinition`, with existing constructors delegating to Generic. Update getters, validation, equality, hash code, and `toString`. The deprecated Preferences loader returns Generic and writes no new key.

3. Type the runtime profile/registry boundary.
   - Change `MetadataType.id()` from `String` to `MetadataTypeId` and have `BuiltInMetadataType` use the matching canonical constants.
   - Change `MetadataTypeRegistry.get(...)` to accept `MetadataTypeId`; its contextual lookup reads the typed value from `TypeDescription`.
   - `DefaultMetadataTypeRegistry` maps canonical ids to implementations. A non-canonical or unsupported id returns `generic()` without modifying or replacing the caller's id.
   - At cache boundaries, use `metadataType.id().getIdentifier()` so existing ids, hashes, log records, and on-disk cache compatibility remain unchanged.
   - Update focused tests and any diagnostic string usage affected by the typed return value.

4. Add the forward-compatible access-list operation.
   - Add/register `OpType.SET_METADATA_TYPE` and dispatch it from `BlockOperation.deserialize(...)`.
   - Implement final `SetMetadataTypeOp` holding a non-null `MetadataTypeId`.
   - Serialize an inner `writeUTF(id.getIdentifier())` into temporary bytes, then write the inner byte length and bytes. On read, validate the frame, parse exactly one identifier via `MetadataTypeId.fromString(...)`, and reject trailing bytes.
   - Keep existing canonical operation formats unchanged.

5. Derive Generic/default/unknown values correctly.
   - Initialize `AccessListState.metadataTypeId` to `MetadataTypeId.GENERIC`.
   - Apply `SetMetadataTypeOp` with last-operation-wins semantics. A non-canonical value remains in state unchanged; an explicit Generic value clears the effective specialized selection.
   - Expose non-null `MetadataTypeId getMetadataTypeId()`.
   - In `DefaultTypeDescriptionList.buildCustomTypeDefinition(...)`, carry the typed state value; in `buildTypeDescription(...)`, carry it again without resolving it through the registry.

6. Extend genesis without serializing default Generic.
   - Add a `createGenesis(..., MetadataTypeId metadataTypeId)` overload used by the editor and retain the existing signature as a Generic-default compatibility overload.
   - Add `SetMetadataTypeOp` and increment the genesis operation count only when the selected id is not `GENERIC`.
   - Keep ordering documented: type key, writer/members/onramps, descriptive metadata, optional metadata type, then policy.

7. Add the registry-driven selector.
   - Inject `MetadataTypeRegistry` into `TypeManagerPreferences`, pass it to `TypeEditorPanel`, and pass the already-created production registry from `Myster`. Use a default registry in standalone setup.
   - Build choices from `supportedTypes()`, using each profile's typed id and `MetadataTypeId.getDisplayName()`. Deduplicate by id, put Generic first, and sort remaining entries by friendly label.
   - Create mode selects Generic. Edit mode selects the exact value from `AccessListState`.
   - If the current value is non-canonical/not supported, add that one backing value to the combo model and render its safe friendly Unknown label. Do not offer arbitrary unknown values for new selections.
   - Add concise help text explaining that the choice controls extracted details and columns, not file matching.
   - Disable the selector under the existing admin-key read-only gate.

8. Persist only actual selection changes.
   - On create, pass the selected `MetadataTypeId` to genesis; Generic is omitted by `AccessList`.
   - On edit, compare the selected value with `state.getMetadataTypeId()` by value equality. Append one `SetMetadataTypeOp` only when different.
   - Therefore absent/default Generic plus selected Generic writes nothing, while specialized/unknown to Generic appends the explicit clear value.
   - Include the typed value in constructed `CustomTypeDefinition` objects and use the existing access-list save/type-list refresh event flow.
   - Preserve an unknown selection during unrelated edits; replace it only after an authorized user deliberately picks another supported choice.

9. Verify integration without adding custom-type branches.
   - Use `TypeSource.CUSTOM` descriptions in registry, file-index, and client-handler tests to prove known profiles resolve normally.
   - Prove an unknown description retains its non-canonical id while registry lookup returns Generic.
   - Prove import and fresh startup reconstruct known and unknown values from access-list state and write only enabled state to Preferences.
   - Do not add MysterType constants, switches, or `instanceof` profile branches to `FileTypeList`, `ClientInfoFactoryUtils`, or the editor.

10. Run verification during implementation.
   - Run `mvn -q -DskipTests test-compile`.
   - Run focused type-id, access-list, registry, cache-key, file-list, client-handler, import, and editor tests headlessly.
   - Run the full headless Maven suite and distinguish pre-existing environment failures from regressions.
   - Manually create Generic and Image custom types, confirm genesis omission/inclusion, enable Image against a JPEG folder, and verify image columns.
   - Import a fixture with a future id such as `spatial_audio`, verify a friendly “Unknown metadata type — Spatial Audio” label and Generic local columns, edit an unrelated field, and confirm the future id remains unchanged.
   - Change the Image type to Generic and verify one clear operation is appended and type-specific columns disappear after the existing update/reindex flow.

## 10. Tests to write

- `TestMetadataTypeId`
  - Known ids return canonical singleton constants and explicit friendly labels.
  - Two different future ids create distinct non-canonical values; repeated parsing compares equal and preserves the normalized identifier.
  - Unknown display labels are readable, bounded, non-HTML, and do not expose a bare raw token.
  - Null, blank, excessive, and illegal identifiers are rejected; normalization follows the documented contract.
- `TestBlockOperation`
  - `SetMetadataTypeOp` round-trips canonical Generic/Audio and a non-canonical future id.
  - Negative, oversized, truncated, and trailing framed data is rejected.
  - The new framed payload can round-trip through the older `UnknownOp` representation without byte changes.
- `TestAccessListState`
  - Empty state is canonical Generic.
  - Later known, unknown, and Generic operations replace the previous value exactly.
  - Writer authorization matches other metadata operations.
- `TestAccessList`
  - Generic genesis omits the operation; specialized genesis includes it and remains valid.
  - Specialized/unknown to Generic appends an explicit clearing operation.
  - Known and non-canonical ids survive serialization, validation, and replay.
- Type model/resource tests
  - Legacy constructors and missing MML entries use `MetadataTypeId.GENERIC`.
  - Bundled MPG3, PICT, and MOOV map to canonical Audio, Image, and Video.
  - `CustomTypeDefinition` equality/hash includes the typed association.
- Registry/cache tests
  - Canonical ids resolve matching implementations; non-canonical values resolve Generic.
  - Runtime fallback does not alter the `TypeDescription`'s unknown id.
  - Cache keys/log ids remain byte-for-byte compatible with existing lowercase strings.
- Import/persistence tests
  - Known and unknown access-list values become the same typed values after import and restart.
  - Preferences contain only the enabled flag.
- `TestTypeEditorPanelMetadataType`
  - Choices derive from a fake registry, use friendly labels, and default to Generic.
  - A current unknown value appears as one friendly Unknown selection and retains its backing id.
  - Read-only mode disables the selector.
  - Unchanged Generic/known/unknown selections append nothing; actual changes append exactly one operation; specialized/unknown to Generic emits the clear value.
- Existing integration tests
  - A custom Audio/Image/Video description creates the same profile-owned file item and handler as a bundled type.
  - Existing built-in metadata extraction, cache, and column behavior remains unchanged.

## 11. Docs / Javadoc to update

- `MetadataTypeId` - document the TypeSafeEnum/extensible-enum contract, canonical values, unknown identity preservation, identifier validation, and display-name separation.
- `TypeDescription` and `CustomTypeDefinition` - document non-null Generic-default associations and access-list ownership for custom types.
- `MetadataType.id()` - distinguish the serialized typed identity from runtime behavior and cache version.
- `MetadataTypeRegistry` - document non-canonical/unsupported fallback without identity mutation.
- `SetMetadataTypeOp` - document stable id contents, exact length framing, Generic clear semantics, and older-reader compatibility.
- `AccessList.createGenesis(...)` - document omission for Generic and operation ordering.
- `AccessListState.getMetadataTypeId()` - document Generic default and last-operation-wins behavior.
- `TypeEditorPanel` - document registry-derived choices, friendly rendering, contextual unknown values, preservation, and admin-key edit gate.
- `docs/impl_summary/custom-type-metadata-profile.md` - create after implementation with protocol shape, compatibility evidence, UI behavior, and verification results.
