# Private Types Access Lists — Milestone 2: GUI & Type Lifecycle

## Summary

Wire the existing custom-type GUI (`TypeManagerPreferences`, `TypeEditorPanel`) and the
`DefaultTypeDescriptionList` / `CustomTypeManager` persistence layer into the
`AccessListManager` / `AccessList` infrastructure built in Milestone 1.

After this milestone:

- Creating a custom type saves an `AccessList` to disk (via `AccessListManager`) and stores
  only the `MysterType` shortBytes + enabled flag in Java Preferences.
- The access list is the **canonical, authoritative** store for all type metadata
  (name, description, extensions, searchInArchives, policy, public key).
- `CustomTypeManager` is reduced to: "which types exist (by `MysterType` key), and are they
  enabled?"  All other data comes from the access list.
- Loading types on startup reconstructs `CustomTypeDefinition` objects from the access list.
  A prefs node with no corresponding access list is treated as corrupt and silently removed.
- The GUI offers a "Create Type" editor (public or private) that creates a genesis block, and
  an "Edit Type" flow that appends signed blocks to the chain.
- **Types the user does not own** (no `.key` file on disk) open in the editor as read-only.
  This covers both built-in types and future imported types — no special casing needed.

---

## Implementation Status (as of 2026-02-24)

| Component | Status | Notes |
|---|---|---|
| `Policy.java` | ✅ Done | Single `listFilesPublic` field; legacy keys silently ignored |
| `AccessListManager.java` | ✅ Done | Load/save/remove/cache; from M1 |
| All `*Op` classes, `AccessList`, `AccessListState`, `AccessListStorageUtils` | ✅ Done | From M1 |
| `AccessListKeyUtils.java` | ✅ Done | Save/load/hasKeyPair/deleteKeyPair; Ed25519 + EdDSA fallback |
| `MysterType.fromHexString()` | ✅ Done | Added to support prefs node name parsing |
| `CustomTypeManager.java` | ✅ Done | Enabled-index only; `saveEnabled` / `loadEnabledTypes` |
| `DefaultTypeDescriptionList.java` | ✅ Done | Loads custom types from access lists; stale nodes deleted |
| `TypeEditorPanel.java` | ✅ Done | Create: genesis block + admin key; Edit: diff + append; read-only guard |
| `TypeManagerPreferences.java` | ✅ Done | Passes `AccessListManager` through to `TypeEditorPanel` |
| `Myster.java` | ✅ Done | Single shared `AccessListManager`; passed to all consumers |
| `TestCustomTypeManager` | ✅ Done | 8 tests |
| `TestAccessListKeyUtils` | ✅ Done | 3 tests (file-format + sign/verify + missing-file) |

**All 274 tests pass.**

---

## Goals

1. **Access list becomes canonical metadata store** for custom types.
2. **Prefs are reduced** to `{mysterType_hex} → enabled` only.  The full `CustomTypeDefinition`
   (name, description, extensions, etc.) is no longer stored in prefs.
3. **`DefaultTypeDescriptionList`** loads custom types by reading access lists from
   `AccessListManager`, not from `CustomTypeManager`.
4. **`TypeEditorPanel`** (create/edit) generates/appends access list blocks and saves them via
   `AccessListManager`, rather than directly writing to `CustomTypeManager`.
5. **`CustomTypeManager`** survives only as an enabled/disabled index.
   All richer metadata is purged from its storage.
6. **Ed25519 admin key** for the type is generated and persisted alongside the access list
   so future edits can append signed blocks.

---

## Non-Goals (Milestone 2)

- **Type metadata resolution** (transient, display-only fetch of an unknown type's name from a
  remote node) — deferred to Milestone 3.
- **Type import** (user deliberately adds a remote type to their local list) — deferred to
  Milestone 3.
- TCP access list fetching from remote servers (used by both of the above) — deferred to
  Milestone 3.
- Access-control enforcement in file serving or search (later milestone).
- Discovery (UDP type lister) filtering.
- Multi-admin / multi-writer flows — only the genesis admin writes blocks in this milestone.
- Any changes to built-in / default types (they remain without access lists for now).
- **Migration from old prefs data** — there is no live user data to migrate; stale prefs nodes
  are simply deleted.

### Note on Type Import (Milestone 3 preview)

When Milestone 3 adds Import, an imported type will have:
- An access list file on disk (fetched from the network and saved via `AccessListManager`)
- **No** `.key` file (the user is not the admin)

The `TypeEditorPanel` edit-mode logic already handles this correctly: `AccessListKeyUtils.hasKeyPair(mysterType)` returns `false` → all fields are read-only, Save is disabled. **No special "imported type" flag or code path is needed.** The key-file gate is the universal mechanism for both built-in and imported types.

Built-in types (videos, music, etc.) loaded from `typedescriptionlist.mml` never go through `CustomTypeManager` or `AccessListManager` at all, so they are also implicitly read-only.

---

## Assumptions

1. Milestone 1 is fully implemented and its tests pass:
   `AccessList`, `AccessBlock`, `AccessListState`, all `*Op` classes, `AccessListStorageUtils`,
   `AccessListManager`, and the TCP server/client.
2. `AccessListManager` uses `MysterGlobals.getAccessListPath()`.
3. The Ed25519 admin keypair will be stored in
   `{PrivateDataPath}/AccessListKeys/{mysterType_hex}.key`.
4. The `Util.generateCid(PublicKey)` helper exists in `com.myster.identity`.
5. **There is no existing user data to preserve.** A prefs node without a corresponding access
   list file is corrupt/stale and is silently removed from prefs.

---

## Proposed Design (High-Level)

### Responsibility Shift

| What is stored | **Before Milestone 2** | **After Milestone 2** |
|---|---|---|
| Type name, description, extensions, searchInArchives, isPublic, public key | Java Prefs (`CustomTypeManager`) | Access list on disk (`AccessListManager`) |
| Enabled / disabled | Java Prefs | Java Prefs (unchanged) |
| Whether a custom type *exists* | Java Prefs node name | Java Prefs node name (backed by access list) |

### New Prefs Node Structure

```
CustomTypes/
  a3f5c2d1.../      (mysterType hex — still the node name)
    enabled = true   (the only key stored)
```

No other keys are written. Any old keys (`name`, `description`, etc.) in pre-existing nodes are
irrelevant — if the access list file is missing the entire prefs node is deleted.

### Ed25519 Admin Key Storage

For each custom type the local user **created** (and is therefore the admin of), an Ed25519
keypair is saved at:

```
{PrivateDataPath}/AccessListKeys/{mysterType_hex}.key
```

The filename is `{mysterType_hex}.key` — i.e. the MD5 hex of the type's RSA public key. This
is fully deterministic: given any `MysterType` (which is always available from the loaded
`TypeDescription`), the system can immediately check whether a `.key` file exists for it, with
no additional index or lookup table required.

This is the **gate for edit access**:
- `.key` file present → this machine created this type; the user is the admin; edit is allowed.
- `.key` file absent → this machine did not create this type; the Save button is disabled and
  the form fields are read-only. The type can still be viewed but not modified.

There is no separate "am I an admin?" flag stored anywhere else. The key file's presence *is*
the proof of admin status on this machine.

Format: a single `DataOutputStream` binary blob:
- `[4 bytes]` key version = 1  
- `[4 bytes]` private key length  
- `[N bytes]` PKCS8-encoded private key  
- `[4 bytes]` public key length  
- `[M bytes]` X.509-encoded public key  

A new utility class `AccessListKeyUtils` (static methods only → `Utils` suffix) handles
read/write.

### Loading Order on Startup

`DefaultTypeDescriptionList` constructor:

1. Read `CustomTypes/*` nodes from prefs → collect `(mysterType, enabled)` pairs.
2. For each pair, call `accessListManager.loadAccessList(mysterType)`.
   - If found → reconstruct `CustomTypeDefinition` from `AccessListState`.
   - If **not** found → delete the stale prefs node; log a warning; skip.
3. Build `TypeDescriptionElement` list as today.

### Create Flow (New Type)

`TypeEditorPanel.handleOk()` (for new type):

1. Generate RSA keypair → `typeRsaKeyPair` (same as today).
2. Generate Ed25519 keypair → `adminKeyPair` (new).
3. Parse form fields → name, description, extensions, searchInArchives.
4. Derive `Policy` from the Public/Private radio buttons:
   - Public → `Policy.defaultPermissive()` (`listFilesPublic=true`)
   - Private → `Policy.defaultRestrictive()` (`listFilesPublic=false`)
   - `new Policy(isPublic)`
5. Call `AccessList.createGenesis(...)` with all of the above.
6. Call `accessListManager.saveAccessList(accessList)`.
7. Call `AccessListKeyUtils.saveKeyPair(adminKeyPair, mysterType)`.
8. Produce a `CustomTypeDefinition` from the form fields (using `typeRsaKeyPair.getPublic()`).
9. Call `tdList.addCustomType(def)` → stores only `(mysterType, enabled=false)` in prefs.

### Edit Flow (Existing Type)

`TypeEditorPanel.handleOk()` (for existing type):

1. Load admin keypair via `AccessListKeyUtils.loadKeyPair(mysterType)`.
   - If not found → Save button is disabled; editing is read-only.
2. Load access list via `accessListManager.loadAccessList(mysterType)`.
3. For each changed field, append the appropriate block:
   - name changed → `new SetNameOp(newName)`
   - description changed → `new SetDescriptionOp(...)`
   - extensions changed → `new SetExtensionsOp(...)`
   - searchInArchives changed → `new SetSearchInArchivesOp(...)`
   - either policy radio changed → `new SetPolicyOp(new Policy(publicRadio.isSelected()))`
4. Save updated access list via `accessListManager.saveAccessList(accessList)`.
5. Produce an updated `CustomTypeDefinition` from the new state.
6. Call `tdList.updateCustomType(type, def)`.

---

## Affected Modules / Packages

| Package | Component | Change |
|---|---|---|
| `com.myster.type` | `DefaultTypeDescriptionList` | Load custom types from access lists; remove metadata write to `CustomTypeManager`; delete stale prefs nodes |
| `com.myster.type` | `CustomTypeManager` | Reduce to enabled-index only; strip all metadata fields |
| `com.myster.type` | `CustomTypeDefinition` | No structural changes |
| `com.myster.type.ui` | `TypeEditorPanel` | Accept `AccessListManager`; create/append access list on save |
| `com.myster.type.ui` | `TypeManagerPreferences` | Pass `AccessListManager` through to `TypeEditorPanel` |
| `com.myster.access` | `AccessListManager` | Already done in M1; no structural changes |
| `com.myster.access` | `AccessListKeyUtils` (new) | Static helper for reading/writing Ed25519 admin keypairs |

---

## Files / Classes to Create or Change

### Create

#### 1. `com/myster/access/AccessListKeyUtils.java`

Static-methods-only class (`Utils` naming convention).

```java
/**
 * Utilities for persisting the Ed25519 admin keypair that authorises
 * appending blocks to a type's access list.
 *
 * <p>Keys are stored in {@code {PrivateDataPath}/AccessListKeys/{mysterType_hex}.key}.
 */
public class AccessListKeyUtils {
    private static final int KEY_VERSION = 1;

    /** Saves an Ed25519 admin keypair for the given type. */
    public static void saveKeyPair(KeyPair keyPair, MysterType mysterType) throws IOException { ... }

    /** Loads the admin keypair for the given type, or empty if not present. */
    public static Optional<KeyPair> loadKeyPair(MysterType mysterType) throws IOException { ... }

    /** Returns true if an admin key exists for this type. */
    public static boolean hasKeyPair(MysterType mysterType) { ... }

    private static File keyFile(MysterType mysterType) {
        return new File(getAccessListKeysPath(), mysterType.toHexString() + ".key");
    }

    private static File getAccessListKeysPath() {
        File dir = new File(MysterGlobals.getPrivateDataPath(), "AccessListKeys");
        dir.mkdirs();
        return dir;
    }
}
```

### Modify

#### 2. `com/myster/type/CustomTypeManager.java`

Reduce to enabled-index only. Remove all metadata methods.

- Replace `saveCustomType(CustomTypeDefinition def)` with `saveEnabled(MysterType type, boolean enabled)`:
  writes only the `enabled` boolean.
- Replace `loadCustomTypes()` with `loadEnabledTypes()` → `Map<MysterType, Boolean>`:
  iterates child nodes; reads node name as hex → `MysterType`; reads `enabled` boolean only.
- Keep `deleteCustomType(MysterType)` unchanged.
- Remove `updateCustomType`, `exists(MysterType)` — no longer needed.
- All old metadata fields (`name`, `description`, `publicKey`, `extensions`, etc.) that may exist
  in prefs nodes are silently ignored during read; they are not written.

#### 3. `com/myster/type/DefaultTypeDescriptionList.java`

Signature change: constructor gains `AccessListManager accessListManager`.

- Replace the `customTypeManager.loadCustomTypes()` loop with:
  ```
  for each (mysterType, enabled) in customTypeManager.loadEnabledTypes():
      Optional<AccessList> al = accessListManager.loadAccessList(mysterType)
      if al.present:
          def = buildCustomTypeDefinition(al.get().getState())
          add TypeDescriptionElement(def, enabled)
      else:
          log.warning("No access list for type " + mysterType.toHexString() + " — removing stale prefs node")
          customTypeManager.deleteCustomType(mysterType)
          // skip — type is gone
  ```
- Add private `buildCustomTypeDefinition(AccessListState state)`:
  maps `state.getTypePublicKey()`, `state.getName()`, `state.getDescription()`,
  `state.getExtensions()`, `state.isSearchInArchives()`, `state.getPolicy().isListFilesPublic()`
  → `CustomTypeDefinition`.
- `addCustomType(CustomTypeDefinition def)`:
  replace `customTypeManager.saveCustomType(def)` with `customTypeManager.saveEnabled(type, false)`.
  **Pre-condition**: access list and admin key must already be saved before this is called.
- `removeCustomType(MysterType type)`:
  add `accessListManager.removeAccessList(type)` call.
- `updateCustomType(MysterType type, CustomTypeDefinition def)`:
  remove `customTypeManager.updateCustomType(type, def)`;
  reload `TypeDescription` from the already-updated access list state via
  `accessListManager.loadAccessList(type)`.
- `saveEverythingToDisk()`: no change needed.

#### 4. `com/myster/type/ui/TypeEditorPanel.java`

Signature change: constructor gains `AccessListManager accessListManager`.

The existing Public/Private radio buttons map directly onto the single `Policy` field
(`listFilesPublic`). The only change to the visible UI is that the Private radio button is now
**enabled** (previously `setEnabled(false)` with a "not yet implemented" tooltip). Remove that
disabling and the tooltip.

**Create mode** (`existingType == null`):
- In constructor: generate a fresh RSA keypair and a fresh Ed25519 keypair. Store both.
- On `handleOk()`:
  1. `Policy policy = publicRadio.isSelected() ? Policy.defaultPermissive() : Policy.defaultRestrictive()`.
  2. `AccessList.createGenesis(rsaPublicKey, ed25519KeyPair, List.of(), List.of(), policy, name, description, extensions, searchInArchives)`.
  3. `accessListManager.saveAccessList(accessList)` — on `IOException` show error dialog, return early.
  4. `AccessListKeyUtils.saveKeyPair(ed25519KeyPair, mysterType)` — on `IOException` show error dialog, return early.
  5. Build `CustomTypeDefinition` from form fields (using `rsaPublicKey`) and call `onSave.run()`.

**Edit mode** (`existingType != null`):
- In constructor:
  - Derive `mysterType` from `existingType.toMysterType()`.
  - Check `AccessListKeyUtils.hasKeyPair(mysterType)`.
    - If `false` → set all form fields to read-only (`setEditable(false)` on text fields,
      `setEnabled(false)` on checkboxes and radios); disable Save button; show a small label:
      *"Read-only: this type was not created on this machine."*
    - If `true` → load `Optional<KeyPair> adminKeyPair = AccessListKeyUtils.loadKeyPair(mysterType)`;
      load `Optional<AccessList> accessList = accessListManager.loadAccessList(mysterType)`.
      If either load fails → disable Save with tooltip explaining the specific error.
  - Populate form fields from `accessList.getState()` (not from `existingType`):
    - `publicRadio.setSelected(state.getPolicy().isListFilesPublic())`
    - `privateRadio.setSelected(!state.getPolicy().isListFilesPublic())`
- On `handleOk()` (only reachable if key + access list both loaded):
  1. Compare each field to `accessList.getState().*`; for each that changed, append the
     appropriate block using `adminKeyPair`.
  2. If policy radio changed: `new SetPolicyOp(publicRadio.isSelected() ? Policy.defaultPermissive() : Policy.defaultRestrictive())`.
  3. If no fields changed → skip disk write and call `onSave.run()` directly.
  4. `accessListManager.saveAccessList(accessList)`.
  5. Build updated `CustomTypeDefinition` from the new `accessList.getState()` and call `onSave.run()`.

Remove `privateRadio.setEnabled(false)` and its "not yet implemented" tooltip from `initComponents()`.

---

## Step-by-Step Implementation Plan

### Phase 1: `AccessListKeyUtils`

**Goal**: standalone utility that can save/load Ed25519 keypairs.

1. Create `src/main/java/com/myster/access/AccessListKeyUtils.java`.
2. Implement `saveKeyPair(KeyPair, MysterType)`:
   - Ensure `{PrivateDataPath}/AccessListKeys/` exists (`dir.mkdirs()`).
   - Write `DataOutputStream`: version int, private key length + bytes (PKCS8), public key length + bytes (X.509).
3. Implement `loadKeyPair(MysterType)` → `Optional<KeyPair>`:
   - Return `Optional.empty()` if file does not exist.
   - Read version, private key bytes → `KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec)`.
   - Also try `"EdDSA"` algorithm name as a fallback (same pattern as `AccessListStorageUtils.decodePublicKey()`).
   - Read public key bytes → `KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec)`.
   - Return `Optional.of(new KeyPair(publicKey, privateKey))`.
4. Implement `hasKeyPair(MysterType)` → `boolean`.
5. **Unit test** `TestAccessListKeyUtils`:
   - Round-trip: generate, save, load; verify `privateKey.getEncoded()` and `publicKey.getEncoded()` match.
   - Verify key can sign data and the loaded key can verify.
   - Missing file → returns `Optional.empty()`.

### Phase 2: Slim Down `CustomTypeManager`

**Goal**: prefs-based enabled-index only; no metadata.

1. Replace `saveCustomType(CustomTypeDefinition def)` with `saveEnabled(MysterType type, boolean enabled)`:
   - Node name = `type.toHexString()`.
   - Only write `prefs.putBoolean("enabled", enabled)`.
2. Replace `loadCustomTypes()` with `loadEnabledTypes()` → `Map<MysterType, Boolean>`:
   - Iterate `customTypesRoot.childrenNames()`.
   - For each node, attempt to parse the node name as a `MysterType` from hex.
     If parsing fails (e.g. a garbage node name), **log a warning and skip** — do not throw.
   - Read `enabled` boolean (default `false` if key absent); ignore all other keys entirely.
   - Return whatever successfully parsed entries were found.
3. Keep `deleteCustomType(MysterType)` unchanged.
4. Remove `updateCustomType`, `exists(MysterType)`, and all metadata-related methods.
5. **Unit tests** (update `TestCustomTypeManager`):
   - `saveEnabled` + `loadEnabledTypes` round-trips correctly for multiple types.
   - A prefs node containing old metadata keys (`name`, `publicKey`, etc.) is loaded without error;
     only the `enabled` value is returned.
   - A prefs node with a malformed (non-hex) node name is skipped without throwing.

### Phase 3: Update `DefaultTypeDescriptionList`

**Goal**: load custom types from access lists; delete stale prefs nodes.

1. Add `AccessListManager accessListManager` parameter to the constructor.
2. Replace the `loadCustomTypes()` loop with the new load logic (see design above):
   - `accessListManager.loadAccessList(type)` present → `buildCustomTypeDefinition(state)`.
   - Absent → `customTypeManager.deleteCustomType(type)` + log warning + skip.
3. Implement `private CustomTypeDefinition buildCustomTypeDefinition(AccessListState state)`.
4. In `addCustomType(CustomTypeDefinition def)`:
   - Replace `customTypeManager.saveCustomType(def)` with `customTypeManager.saveEnabled(type, false)`.
  **Pre-condition**: access list and admin key must already be saved before this is called.
5. In `removeCustomType(MysterType type)`:
   - Add `accessListManager.removeAccessList(type)`.
6. In `updateCustomType(MysterType type, CustomTypeDefinition def)`:
   - Remove `customTypeManager.updateCustomType(type, def)`.
   - Re-read access list state to refresh in-memory `TypeDescription`.
7. **Unit tests** (`TestDefaultTypeDescriptionListWithAccessList`):
   - Load path: given a populated `AccessListManager` mock, types load with correct name/description.
   - Stale node: prefs node present but `AccessListManager` returns empty → node deleted, type absent.
   - Add: `saveEnabled` called, access list not created here.
   - Remove: `accessListManager.removeAccessList` called.

### Phase 4: Update `TypeEditorPanel`

**Goal**: create/edit operations produce signed access list blocks; edit is gated by the presence
of the Ed25519 admin key file.

1. Add `AccessListManager accessListManager` field + constructor parameter to both constructors.
2. **Enable the Private radio button**: remove `privateRadio.setEnabled(false)` and its
   tooltip from `initComponents()`. No other UI changes needed.
3. **Create mode constructor**:
   - Generate RSA keypair via `KeyPairGenerator.getInstance("RSA")` (replace the awkward
     `CustomTypeDefinition.generateNew("temp", ...)` hack).
   - Generate Ed25519 keypair: `KeyPairGenerator.getInstance("Ed25519").generateKeyPair()`.
   - Store both keypairs as fields.
4. **Edit mode constructor**:
   - Derive `mysterType = existingType.toMysterType()`.
   - Check `AccessListKeyUtils.hasKeyPair(mysterType)`.
   - If absent: set all fields read-only, disable Save, show label
     *"Read-only: this type was not created on this machine."*
   - If present: `loadKeyPair(mysterType)` and `accessListManager.loadAccessList(mysterType)`.
     If either fails: disable Save with a descriptive tooltip.
   - Populate form from `accessList.getState()` including policy radio state.
5. **`handleOk()` create mode**:
   - `Policy policy = publicRadio.isSelected() ? Policy.defaultPermissive() : Policy.defaultRestrictive()`.
   - `AccessList.createGenesis(rsaPublicKey, ed25519KeyPair, List.of(), List.of(), policy, name, description, extensions, searchInArchives)`.
   - `accessListManager.saveAccessList(accessList)` — `IOException` → error dialog, return early.
   - `AccessListKeyUtils.saveKeyPair(ed25519KeyPair, mysterType)` — `IOException` → error dialog, return early.
   - Build `CustomTypeDefinition` and call `onSave.run()`.
6. **`handleOk()` edit mode**:
   - Diff each field against `accessList.getState().*`; append blocks only for changed fields.
   - Policy diff: compare `Policy(publicRadio.isSelected())` against `state.getPolicy()`.
   - If nothing changed, skip disk write and call `onSave.run()` directly.
   - Otherwise `accessListManager.saveAccessList(accessList)`.
   - Build updated `CustomTypeDefinition` from `accessList.getState()` and call `onSave.run()`.
7. **Manual integration test**:
   - Create type with Public radio → `policy.listFilesPublic=true`; `.accesslist` and `.key` files appear.
   - Create type with Private radio → `policy.listFilesPublic=false`.
   - Edit type (admin machine) → access list gains the correct block type(s) for changed fields only.
   - Open editor on a machine without `.key` file → all fields read-only, Save disabled.
   - Restart app → type loads with updated metadata from access list.

### Phase 5: Wire `AccessListManager` Through the App

**Goal**: singleton `AccessListManager` available everywhere.

1. `TypeManagerPreferences`: add `AccessListManager` constructor parameter; pass to `TypeEditorPanel`.
2. `DefaultTypeDescriptionList`: constructor already updated in Phase 3.
3. Find the `new DefaultTypeDescriptionList(...)` call site (likely `Myster.java`) and pass the
   singleton `AccessListManager`.
4. Find the `new TypeManagerPreferences(...)` call site and pass the same singleton.
5. Ensure `AccessListManager` is instantiated **before** `DefaultTypeDescriptionList` in the
   startup sequence.

---

## Tests / Verification

### Unit Tests (new or updated)

| Test class | What to test |
|---|---|
| `TestAccessListKeyUtils` | Save/load Ed25519 keypair; round-trip sign+verify; missing file → `Optional.empty()` |
| `TestCustomTypeManager` (update) | `saveEnabled` + `loadEnabledTypes` round-trip; old metadata keys silently ignored |
| `TestDefaultTypeDescriptionListWithAccessList` | Load from access list state; stale prefs node deleted; add/remove/update paths |

### Manual QA Checklist

- [ ] Create a Public type → `policy.listFilesPublic=true`; `.accesslist` and `.key` files created.
- [ ] Create a Private type → `policy.listFilesPublic=false`.
- [ ] Restart app → type loads correctly with all metadata from access list.
- [ ] Edit a type (admin) → access list gains new block(s); metadata updates in UI.
- [ ] Edit a type (no key file) → all fields read-only, Save button disabled.
- [ ] Delete a type → access list file removed; prefs node removed.
- [ ] Private radio button is now enabled and functional.

---

## Acceptance Criteria

- [ ] `CustomTypeManager` writes only `enabled` to prefs; no metadata keys written.
- [ ] `AccessListManager.loadAccessList(type)` returns a valid list for every persisted custom type.
- [ ] `DefaultTypeDescriptionList` loads name, description, extensions, and policy from access list state.
- [ ] A prefs node with no corresponding access list is silently deleted on startup.
- [ ] Creating a type via the GUI produces an `.accesslist` file and a `.key` file on disk.
- [ ] Editing a type via the GUI (by admin) appends the correct blocks to the access list.
- [ ] Editing a type for which no admin key exists is read-only (Save disabled, all fields read-only).
- [ ] Deleting a type removes the access list file from disk.
- [ ] App restart preserves all type metadata (loaded from access list only).
- [ ] The Private radio button is enabled and creates a type with `policy.listFilesPublic=false`.
- [ ] All new and updated unit tests pass.
- [ ] All existing Milestone 1 unit tests still pass.

---

## Risks / Edge Cases / Rollout Notes

### Risks

1. **`AccessListManager` construction order** — `DefaultTypeDescriptionList` must be constructed
   *after* `AccessListManager`.  A compile-time check: confirm
   `grep -r "new DefaultTypeDescriptionList"` finds only one call site.

2. **Thread safety** — `DefaultTypeDescriptionList` is `synchronized`; `AccessListManager` uses
   `ConcurrentHashMap`.  The new disk I/O in `TypeEditorPanel.handleOk()` runs on the EDT;
   key files are tiny so a brief block is acceptable.

3. **Duplicate genesis on double-click** — disable the Save button immediately on first click
   to prevent creating two genesis blocks.

4. **Ed25519 algorithm name** — JDK 15+ uses `"Ed25519"`; some providers use `"EdDSA"`.
   `AccessListKeyUtils` must try both (same pattern as `AccessListStorageUtils.decodePublicKey()`).

5. **Pre-existing stale prefs data** — old prefs nodes with extra metadata keys (`name`,
   `description`, `publicKey`, etc.) must **never cause a crash**. The reading code simply
   ignores any key it doesn't recognise. If the corresponding access list file is missing, the
   prefs node is deleted. The old data is not preserved — that is intentional — but the presence
   of stale data must not break startup.

### Edge Cases

- **Stale prefs node (no access list)** — silently deleted; logged at WARNING level.
- **Admin key file manually deleted** — type remains in list as read-only; no crash.
- **`CustomTypeDefinition.isPublic()` mapping** — maps to `state.getPolicy().isListFilesPublic()`.
- **Legacy `discoverable` and `nodeCanJoinPublic` in serialized data** — silently discarded by
  `Policy.fromMessagePakBytes()`; does not crash; old files are read cleanly.

### Rollout

- No wire-format changes; fully backward-compatible from a network perspective.
- The only breaking compile-time change is the new `AccessListManager` constructor parameters on
  `DefaultTypeDescriptionList` and `TypeManagerPreferences`.  All call sites must be updated
  before the build compiles — there should be exactly one of each.

---

**Plan Version**: 1.1  
**Updated**: 2026-02-23 (removed migration requirement — no live data to preserve)  
**Milestone**: 2 of 3  
**Depends on**: Milestone 1 (`private-types-core-infrastructure.md`) complete  
**Status**: Ready for implementation
