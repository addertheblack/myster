# Custom Myster Type Metadata Profile Implementation Summary

## What was implemented

Custom Myster Types can now select exactly one metadata profile. The selection is stored as signed,
shared type metadata in the access-list chain and is used by the existing metadata registry to
select extraction behavior and file-list columns.

Known profiles use an extensible `MetadataTypeId` value. A profile identifier introduced by a newer
Myster version remains distinct and survives import, replay, restart, and unrelated edits. The local
client uses Generic runtime behavior for such an unsupported value while the editor displays a safe,
friendly label such as “Unknown metadata type — Spatial Audio”.

Generic retains default/clear semantics: it is omitted from new genesis blocks and unchanged legacy
types, but changing a specialized or unknown association to Generic appends one explicit clearing
operation.

## Files changed

- `src/main/java/com/myster/type/MetadataTypeId.java`
  - Added the validated metadata-profile `TypeSafeEnum` subtype with canonical Generic, Audio,
    Image, and Video values and friendly unknown labels.
- `src/main/java/com/general/util/TypeSafeEnum.java`
  - Extracted reusable serialized identity, canonical-state, same-subtype equality, immutable
    canonical indexing, and known/unknown resolution behavior.
- `src/main/java/com/myster/type/TypeDescription.java`
  - Replaced the optional string association with a non-null, Generic-defaulting typed identifier.
- `src/main/java/com/myster/type/CustomTypeDefinition.java`
  - Carries the typed association through construction, equality, hashing, and diagnostics.
- `src/main/java/com/myster/type/DefaultTypeDescriptionList.java`
  - Parses bundled profile identifiers and reconstructs custom associations from access-list state.
- `src/main/java/com/myster/access/SetMetadataTypeOp.java`
  - Added the length-framed access-list operation and explicit Generic clear behavior.
- `src/main/java/com/myster/access/OpType.java`, `BlockOperation.java`, `AccessListState.java`, and
  `AccessList.java`
  - Registered, deserialized through an immutable typed dispatcher map, replayed, and conditionally
    placed the association in genesis.
- `src/main/java/com/myster/filemanager/MetadataType.java`, `BuiltInMetadataType.java`,
  `MetadataTypeRegistry.java`, `DefaultMetadataTypeRegistry.java`, and `FileMetadataCacheKey.java`
  - Typed the registry boundary, retained Generic fallback for unsupported values, and kept existing
    cache-key strings stable.
- `src/main/java/com/myster/type/ui/TypeEditorPanel.java`
  - Added the registry-driven friendly selector, contextual unknown choice, read-only behavior, and
    change-only persistence.
- `src/main/java/com/myster/type/ui/TypeManagerPreferences.java` and
  `src/main/java/com/myster/Myster.java`
  - Pass the production metadata registry into the editor.
- Access-list, model, registry, cache, import, file-list, client-handler, and Swing editor tests under
  `src/test/java/com/myster/**`.
- `src/test/java/com/general/util/TestTypeSafeEnum.java`
  - Covers canonical resolution, preserved unknown equality, cross-type inequality, and canonical
    index invariants.
- `docs/design/Myster Private Types — Access Lists (Part 1 Implementation Spec).md` and
  `docs/conventions/myster-coding-conventions.md`
  - Documented metadata association semantics and the required framing convention for new
    forward-compatible operations.

## Key decisions

- `MetadataTypeId` is persisted identity; `MetadataType` is locally executable behavior. This keeps
  a future identifier intact even when this client can only execute Generic behavior.
- `MetadataTypeId`, `Role`, and `OpType` share `TypeSafeEnum`; parsing and validation remain subtype
  responsibilities while the identity and canonical/unknown contract is implemented once.
- `TypeSafeEnum` is reserved for version-skewed wire values that must preserve future identifiers.
  Ordinary closed sets should use Java `enum`; raw string constants are not an enum model.
- Exactly one profile is stored. Multi-profile composition remains outside this feature because the
  current file-item factory and complete column-handler abstractions are not composable.
- The access list is authoritative. Java Preferences still stores only whether a custom type is
  enabled.
- The editor obtains choices from `MetadataTypeRegistry.supportedTypes()`, places Generic first, and
  sorts the rest by their friendly labels. It has no profile-specific branches.
- Unknown values are contextual: users can preserve or replace an imported future value but cannot
  invent arbitrary serialized identifiers in the editor.
- `SET_METADATA_TYPE` uses an outer byte-length frame around its modified-UTF identifier so an older
  reader can retain the operation opaquely and reserialize the signed bytes unchanged.
- Canonical operation payload readers are registered as method references in a single immutable
  `OpType` dispatcher map. The map's checked functional interface passes the active input stream and
  preserves the existing unknown-operation fallback.
- Existing constructors and the previous `AccessList.createGenesis` overload remain available and
  default to Generic for source compatibility.

## Deviations from the plan

- Automated headless Swing coverage was added for the principal editor interactions, so those cases
  did not require an interactive smoke test to establish correctness. A live application check with
  real indexed media remains useful for presentation and end-to-end reindex behavior.
- No other functional deviations were required.

## Verification

Passed compilation and focused regression checks:

```bash
mvn -q -DskipTests compile
mvn -q -DskipTests test-compile
mvn -q -Djava.awt.headless=true -Dtest=TestTypeSafeEnum,TestMetadataTypeId,TestRole,TestOpType,TestBlockOperation test
mvn -q -Djava.awt.headless=true -Dtest=TestMetadataTypeId,TestCustomTypeDefinition,TestTypeDescriptionMetadataTypeId,TestDefaultTypeDescriptionListMetadataTypeId,TestOpType,TestBlockOperation,TestAccessListState,TestAccessList,TestDefaultMetadataTypeRegistry,TestMetadataTypeRegistryResolution,TestFileMetadataCacheKey,TestDefaultTypeDescriptionListImport,TestTypeEditorPanelMetadataType,TestFileTypeList,TestClientInfoFactoryUtils test
```

Passed the complete headless suite:

```bash
mvn -q -Djava.awt.headless=true test
```

Surefire result: 596 tests, 0 failures, 0 errors, 0 skipped. The suite prints the expected
`IllegalStateException: broken` stack trace from a test that deliberately injects that exception;
Maven exits successfully.

`git diff --check` also passes.

## Known issues and follow-up

- Simultaneous Audio, Image, and Video behavior would require a separate composition redesign of
  file-item enrichment and column handlers.
- Unsupported future profile identifiers intentionally use Generic extraction and columns until a
  local implementation is registered.
- An optional live smoke check can confirm selector presentation and the existing update/reindex
  flow against real files; no automated regression remains outstanding.
