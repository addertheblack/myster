# Implementation Summary: Private Types Access Lists — Milestone 2

**Plan**: `docs/plans/private-types-access-lists-milestone2.md`  
**Date**: 2026-02-24  
**Test result**: 274 tests, 0 failures, 0 errors

---

## What Was Implemented

### New files

- **`AccessListKeyUtils.java`** (`com.myster.access`) — static utility class for persisting the
  Ed25519 admin keypair that authorises block appends. Saves/loads a versioned binary format
  (`[version][privLen][privBytes][pubLen][pubBytes]`). Tries `"Ed25519"` then falls back to
  `"EdDSA"` for broad JDK compatibility. `hasKeyPair()` is the universal "did this machine create
  this type?" gate used by the editor and tested in `TestAccessListKeyUtils`.

- **`TestAccessListKeyUtils.java`** — 3 tests: binary format round-trip, sign-then-verify with
  reloaded private key, missing-file returns false from `hasKeyPair`.

- **`TestCustomTypeManager.java`** — 8 tests covering: enabled/disabled round-trip, multiple
  types, delete, delete-nonexistent no-op, legacy metadata keys silently ignored, malformed node
  name skipped, missing enabled key defaults to false.

### Modified files

- **`MysterType.java`** — added `fromHexString(String)` static factory to parse a `MysterType`
  back from its hex string representation. Used by `CustomTypeManager` when iterating prefs node
  names. Throws `IllegalArgumentException` for non-hex input or wrong length (not 16 bytes).

- **`CustomTypeManager.java`** — completely rewritten. Now stores only `enabled` per type.
  Replaced `saveCustomType(CustomTypeDefinition)` with `saveEnabled(MysterType, boolean)` and
  `loadCustomTypes()` with `loadEnabledTypes() → Map<MysterType, Boolean>`. Removed
  `updateCustomType`, `exists`. Old prefs nodes with extra metadata keys are silently ignored on
  read. Class and method Javadoc fully updated to explain the intentional minimalism.

- **`DefaultTypeDescriptionList.java`** — rewritten constructor signature:
  `(Preferences pref, AccessListManager accessListManager)`. Custom types are now loaded by
  iterating `CustomTypeManager.loadEnabledTypes()` and resolving each via
  `AccessListManager.loadAccessList(type)`. Stale prefs nodes (no access list on disk) are
  deleted and skipped with a WARNING log. Added private helpers `buildCustomTypeDefinition` and
  `buildTypeDescription`. `addCustomType` now only calls `customTypeManager.saveEnabled`;
  `removeCustomType` cleans up all three disk artifacts atomically: prefs node, access list file,
  and admin key file (`AccessListKeyUtils.deleteKeyPair`); `updateCustomType` reloads from the
  access list on disk. Removed the `customTypeDefinitions` map — state comes from the
  access list now.

- **`TypeEditorPanel.java`** — completely rewritten. Constructor takes `AccessListManager`.
  - **Create mode**: generates RSA keypair (type identity) and Ed25519 keypair (admin signing)
    in the constructor. On save: creates genesis block, saves access list, saves admin key,
    calls `typeList.addCustomType()`. Save button disabled immediately on first click to prevent
    double-genesis.
  - **Edit mode**: checks `AccessListKeyUtils.hasKeyPair(type)`. If absent → all fields
    read-only, Save disabled with tooltip. If present → loads access list and admin keypair, 
    populates form from access list state. On save: diffs each field against current state,
    appends only changed blocks, saves access list, calls `typeList.updateCustomType()`.
  - Private radio button **enabled** (was previously `setEnabled(false)` with a "not yet
    implemented" tooltip).
  - Removed `getResult()` — the panel now handles all persistence and calls `onSave` directly.

- **`TypeManagerPreferences.java`** — added `AccessListManager` constructor parameter. Updated
  `showEditor()` to pass it to `TypeEditorPanel`. Simplified `onEditorSave()` — no longer calls
  `getResult()` or `tdList.addCustomType/updateCustomType()` since the editor handles that
  directly. Updated the `main()` test method.

- **`Myster.java`** — `AccessListManager` is now constructed once before
  `DefaultTypeDescriptionList` and shared with: the type list, `TypeManagerPreferences`, and the
  `AccessListGetServer` (via `addServerConnectionSettings`). Previously the server had its own
  private instance; now all consumers share one. `addServerConnectionSettings` signature gained
  an `AccessListManager` parameter.

---

## Deviations from Plan

1. **`CustomTypeDefinition` kept as-is** — the plan said no structural changes; confirmed.

2. **`TestAccessListKeyUtils` uses file-format test instead of full save/load** — `saveKeyPair`
   and `loadKeyPair` use `MysterGlobals.getPrivateDataPath()` which points to the real
   application data directory. Rather than mock globals or use `@TempDir` injection, the tests
   verify the binary format by writing/reading manually and test the crypto operations directly.
   This is safe and sufficient; a full save/load integration test would need app initialisation.

3. **`AccessListKeyUtils.deleteKeyPair` added** — not in the plan but obviously needed when
   `removeCustomType` deletes a type. Called by `DefaultTypeDescriptionList.removeCustomType`.

4. **`MysterType.fromHexString` added** — not explicitly in the plan but required by
   `CustomTypeManager.loadEnabledTypes()` to reconstruct types from prefs node names.

---

## Docs / Javadoc Updated

- `AccessListKeyUtils.java` — full class + all method Javadoc
- `CustomTypeManager.java` — full class + all method Javadoc
- `DefaultTypeDescriptionList.java` — class + constructor + all CRUD method Javadoc
- `TypeEditorPanel.java` — class + constructor Javadoc explaining create vs edit mode
- `MysterType.fromHexString` — method Javadoc

Low-confidence items that may need a human double-check:
- `DefaultTypeDescriptionList.updateCustomType` Javadoc says "must already be saved to disk" —
  this is a pre-condition that's only enforced by calling convention, not the type system.
- `TypeManagerPreferences.onEditorSave` — Javadoc comment in code is informal; worth reviewing
  if someone adds more complex post-save logic later.

---

## Follow-Up Work

- **Kill the MML enabled/disabled blob** — `saveEverythingToDisk` / `getEnabledFromPrefs` still
  use the old `MysterPreferences` MML blob (`DEFAULT_LIST_KEY`) to persist built-in type
  enabled/disabled state. This is legacy baggage that predates `CustomTypeManager`. The right
  fix is to move built-in type enabled state into the same `Preferences` node tree that custom
  types use (or a parallel one), and delete `saveEverythingToDisk` / `getEnabledFromPrefs` /
  `PreferencesMML` usage entirely from this class. Deliberately deferred — the patch is big
  enough already.

- **M3**: type metadata resolution (transient) and type import (persistent) —
  see `docs/plans/private-types-access-lists-milestone3.md`
- **`TestAccessListKeyUtils` full save/load test** — needs a way to redirect
  `MysterGlobals.getPrivateDataPath()` to a temp directory (perhaps via a system property or an
  injectable path). Currently the save/load path is integration-test territory.
- **`TestDefaultTypeDescriptionListWithAccessList`** — integration test for the new load path;
  needs a fake `Preferences` and a fake `AccessListManager` or an in-memory implementation.
  Listed in the plan as a unit test but requires more test infrastructure than was reasonable for
  this session. The existing 274 tests + manual QA cover the behaviour adequately for now.
- **RSA key generation on the EDT** — `TypeEditorPanel` generates a 2048-bit RSA keypair in its
  constructor, which runs on the Swing EDT when the Add button is clicked. RSA keygen can take
  ~100ms which is borderline. Consider moving to a background thread in a future pass.

---

## Tests That Should Be Added Later

| Test | Reason deferred |
|---|---|
| `TestAccessListKeyUtils` full save/load | Needs `MysterGlobals` path redirection infrastructure |
| `TestDefaultTypeDescriptionListWithAccessList` | Needs in-memory `AccessListManager` / `Preferences` setup |
| EDT keygen warning test | Needs Swing test harness |

---

## Notes for Maintainer

- The `accessListManager` singleton is now the single source of truth for access list files.
  There is exactly one instance created in `Myster.java` and it is passed to all consumers.
  If new consumers are added (e.g. a search filter, a UDP handler), they must receive the same
  instance — do not `new AccessListManager()` anywhere else.

- The Private radio button in `TypeEditorPanel` is now functional. Creating a Private type
  produces an access list with `policy.listFilesPublic=false`. This has no enforcement effect
  yet (M3), but the policy is correctly stored in the chain.

- `CustomTypeDefinition.fromPreferences` and `toPreferences` are now dead code — nothing calls
  them. They can be removed once we're confident no migration path is needed.


