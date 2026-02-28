# Private Types Access Lists — Milestone 3: Type Metadata Resolution & Import

## Summary

Two distinct features that both involve access lists from remote nodes, but serve
fundamentally different purposes:

1. **Type metadata resolution** — when the `ClientWindow` (`TypeListerThread`) receives a
   `MysterType` from a remote server that is not in the local `TypeDescriptionList`, it
   currently displays the raw hex string. After this milestone, the app can fetch the access
   list for that type from the remote node on-demand and display a human-readable name instead.
   Nothing is saved to disk; this is a transient, display-only lookup.

2. **Type import** — a deliberate user action that permanently adds a remote type to the local
   `TypeDescriptionList` so it can be enabled, searched, and used going forward.  The access
   list is fetched, verified, and saved to disk.  No admin key is created (the user is not the
   type owner), so the type opens as read-only in `TypeEditorPanel`.

These are separated because they have completely different lifecycles, user intent, and
persistence requirements.

---

## Goals

1. **Metadata resolution**: unknown `MysterType` hex strings in `ClientWindow` are resolved
   to human-readable names by fetching the type's access list from the remote node.
2. **Resolution is transient**: fetched-for-display metadata is cached in memory for the
   session but not written to disk or to `CustomTypeManager`.
3. **Type import**: user can import a type (by hex, by URL, or from the `ClientWindow` type
   list), which fetches the access list, verifies its integrity, saves it to disk via
   `AccessListManager`, and registers the type in `CustomTypeManager` as enabled/disabled.
4. **Imported types are read-only in the editor**: no `.key` file is created; `TypeEditorPanel`
   opens in read-only mode automatically (key-file gate, already implemented in M2).
5. **`TypeDescriptionList.get(MysterType)`** is the single resolution point: code that already
   calls it (like `ClientWindow.addItemToTypeList`) gets the resolved name for free once the
   type is known, whether it was imported or just resolved in-memory.

---

## Non-Goals (Milestone 3)

- Access-control enforcement in file serving or search (later milestone).
- Multi-admin / multi-writer flows.
- Pushing updates to a remote access list (admin-only, not applicable for imported types).
- Resolving types in the Tracker window or Search window (can follow the same pattern later).
- Auto-importing all types seen on the network (user must explicitly import).

---

## Background: How the Current Display Works

`ClientWindow.addItemToTypeList(MysterType t)` already does the right thing:

```java
typeDescriptionList.get(t)
    .map(TypeDescription::getDescription)
    .orElse(t.toString())   // ← falls back to hex string today
```

So the fix for metadata resolution is: make `typeDescriptionList.get(t)` return a result for
previously-unknown types by fetching their access list on-demand, without persisting anything.

`TypeListerThread` fetches the list of `MysterType` values from the remote server (UDP or TCP)
and calls `listener.addItemToTypeList(type)` for each.  It does **not** currently fetch any
metadata — that is the gap this milestone fills.

---

## Proposed Design (High-Level)

### Concept 1: Transient Type Name Cache

A new lightweight component — `TypeMetadataCache` — sits between `TypeListerThread` and
`ClientWindow`.  After receiving a `MysterType` from the remote server:

1. Check `TypeDescriptionList.get(type)` — if already known, nothing to do.
2. Check `TypeMetadataCache.get(type)` — if previously resolved this session, use cached name.
3. Otherwise, fetch the access list from the remote server via `AccessListGetClient`
   (already implemented in M1).  This is a background TCP call.
4. On success: parse the access list, extract name from `AccessListState`; store in
   `TypeMetadataCache` (in-memory only).
5. Call `listener.addItemToTypeList(type)` — the `ClientWindow` calls
   `typeDescriptionList.get(t).orElseGet(() -> cache.getName(t))` to get the display name.
6. On failure: display raw hex string (existing behaviour, no regression).

The cache is keyed by `MysterType`, value is the name string (or a sentinel for "tried and
failed").  It is per-`ClientWindow` session — there is no global name cache.

> **Note**: The remote node whose type list we're browsing is the natural server to ask for the
> access list, since it presumably serves that type.  The fetch goes to `mysterAddress` (the
> node already connected to in `TypeListerThread`).

### Concept 2: Type Import

Import flow:

1. User initiates import. Entry points (any one suffices for M3):
   - **"Import" button in `TypeManagerPreferences`** — user pastes a hex `MysterType` string
     and an optional server address to fetch from.
   - **Right-click "Import this type"** on an unrecognised type row in `ClientWindow`'s type
     list (where the type is showing as a hex string because it's unrecognised).
2. App fetches the access list from the specified (or connected) server via `AccessListGetClient`.
3. Verify the access list: call `accessList.validate()`.
4. Confirm with user: show name, description, policy from `accessList.getState()`.  Ask
   "Add this type to your type list?"
5. On confirm:
   - `accessListManager.saveAccessList(accessList)` — saves to disk.
   - `customTypeManager.saveEnabled(mysterType, false)` — registers as disabled by default.
   - `defaultTypeDescriptionList` fires a type-added event.
6. The type now appears in `TypeManagerPreferences` where the user can enable it.
7. The type opens as read-only in `TypeEditorPanel` (no `.key` file — already handled by M2).

---

## Affected Modules / Packages

| Package | Component | Change |
|---|---|---|
| `com.myster.client.ui` | `TypeListerThread` | After fetching type list, also kick off background metadata resolution for unknown types |
| `com.myster.client.ui` | `ClientWindow` | Use `TypeMetadataCache` for display fallback; add right-click "Import" on unknown type rows |
| `com.myster.client.ui` | `TypeMetadataCache` (new) | In-memory transient name cache keyed by `MysterType`; fetches access list on miss |
| `com.myster.type.ui` | `TypeManagerPreferences` | Add "Import" button; show import dialog |
| `com.myster.type.ui` | `TypeImportDialog` (new) | Dialog for paste-hex / confirm-import flow |
| `com.myster.access` | `AccessListGetClient` | Already implemented in M1; no changes needed |
| `com.myster.type` | `DefaultTypeDescriptionList` | Add `importType(AccessList)` convenience method (wraps saveAccessList + saveEnabled) |

---

## Files / Classes to Create or Change

### Create

#### 1. `com/myster/client/ui/TypeMetadataCache.java`

```java
/**
 * Transient, in-memory cache of type names fetched from remote nodes.
 *
 * <p>Used when a {@link MysterType} is not in the local {@link TypeDescriptionList}.
 * Fetches the type's access list from the remote node and caches the name for
 * the duration of the session. Nothing is written to disk.
 *
 * <p>A failed lookup is cached as a sentinel so we don't retry on every UI refresh.
 */
public class TypeMetadataCache {
    /** Returns the cached name, or the hex string if not yet resolved. */
    public String getDisplayName(MysterType type) { ... }

    /**
     * Asynchronously fetches metadata for an unknown type from the given address.
     * On success, updates the cache and fires the provided callback on the EDT.
     * On failure, caches a sentinel and fires the callback with the hex string.
     */
    public void resolveAsync(MysterType type, MysterAddress from,
                             AccessListGetClient client,
                             Runnable onResolved) { ... }

    /** Returns true if a resolution attempt (successful or not) has been made. */
    public boolean hasAttempted(MysterType type) { ... }
}
```

#### 2. `com/myster/type/ui/TypeImportDialog.java`

```java
/**
 * Dialog for importing a remote type into the local TypeDescriptionList.
 *
 * <p>Accepts a hex MysterType string and optional server address.
 * Fetches the access list, shows the type's metadata for confirmation,
 * then saves and registers it on user approval.
 */
public class TypeImportDialog extends JDialog {
    public TypeImportDialog(Frame parent,
                            AccessListGetClient client,
                            AccessListManager accessListManager,
                            DefaultTypeDescriptionList tdList) { ... }
}
```

### Modify

#### 3. `com/myster/client/ui/TypeListerThread.java`

- After the loop that calls `listener.addItemToTypeList(types[i])`, kick off background
  metadata resolution for each type not already in `TypeDescriptionList`:
  ```java
  for (MysterType type : types) {
      listener.addItemToTypeList(type);
      if (!tdList.get(type).isPresent() && !cache.hasAttempted(type)) {
          cache.resolveAsync(type, mysterAddress, accessListGetClient,
                             () -> listener.refreshTypeDisplay(type));
      }
  }
  ```
- `TypeListener` gains a new method `refreshTypeDisplay(MysterType)` so `ClientWindow` can
  update the display name of an already-added row when the async fetch completes.
- Constructor gains `TypeDescriptionList tdList`, `TypeMetadataCache cache`, and
  `AccessListGetClient accessListGetClient` parameters.

#### 4. `com/myster/client/ui/ClientWindow.java`

- Add a `TypeMetadataCache` field (one per window).
- In `addItemToTypeList`: use `cache.getDisplayName(type)` as the display-name fallback
  instead of `t.toString()`.
- Implement `refreshTypeDisplay(MysterType type)`: find the row in `fileTypeList` for that
  type and update its display string from the cache.
- Add right-click context menu on `fileTypeList` rows: if the selected type is not in
  `TypeDescriptionList`, show "Import this type…" which opens `TypeImportDialog`.

#### 5. `com/myster/type/ui/TypeManagerPreferences.java`

- Add an "Import Type…" button to the button bar alongside "Add" and "Edit".
- Opens `TypeImportDialog` with a blank initial state (user pastes hex manually).

#### 6. `com/myster/type/DefaultTypeDescriptionList.java`

- Add convenience method:
  ```java
  /**
   * Imports a type from a remotely-fetched access list.
   * Saves the access list to disk and registers the type as disabled.
   * No admin key is created; the type will be read-only in the editor.
   *
   * @throws IllegalArgumentException if the type is already known
   */
  public void importType(AccessList accessList) throws IOException { ... }
  ```

---

## Step-by-Step Implementation Plan

### Phase 1: `TypeMetadataCache`

**Goal**: in-memory transient resolution of unknown type names.

1. Create `src/main/java/com/myster/client/ui/TypeMetadataCache.java`.
2. Internal map: `ConcurrentHashMap<MysterType, String>` where the value is either a name
   or `""` (sentinel for "tried and failed, use hex string").
3. `getDisplayName(MysterType type)`:
   - `String cached = map.get(type)` — if non-null and non-empty, return it; if empty string
     sentinel, return `type.toHexString()`; if null, return `type.toHexString()` (not yet resolved).
4. `resolveAsync(MysterType, MysterAddress, AccessListGetClient, Runnable onResolved)`:
   - If `hasAttempted(type)`, return immediately.
   - Put a sentinel immediately to prevent concurrent duplicate fetches.
   - Start a background thread / executor task: call
     `accessListGetClient.getAccessList(address, type)`.
   - On success: `map.put(type, state.getName())`.
   - On failure: leave sentinel (`""`).
   - Either way: `SwingUtilities.invokeLater(onResolved)`.
5. `hasAttempted(MysterType)` → `map.containsKey(type)`.
6. **Unit test** `TestTypeMetadataCache`:
   - Successful resolve → `getDisplayName` returns name.
   - Failed resolve → `getDisplayName` returns hex string.
   - Second call to `resolveAsync` on an already-attempted type is a no-op.

### Phase 2: `DefaultTypeDescriptionList.importType()`

**Goal**: clean single entry-point for the import operation.

1. Add `importType(AccessList accessList) throws IOException` to
   `DefaultTypeDescriptionList`:
   - Call `accessList.validate()` — throws if chain is invalid.
   - Derive `MysterType` from `accessList.getMysterType()`.
   - If `get(type).isPresent()` → throw `IllegalArgumentException("Type already known")`.
   - `accessListManager.saveAccessList(accessList)`.
   - `customTypeManager.saveEnabled(type, false)`.
   - Build `CustomTypeDefinition` from `accessList.getState()` (same as `buildCustomTypeDefinition`).
   - Add to in-memory type list.
   - Fire type-added event.
2. **Unit test**: import a valid access list → type appears in `getAllTypes()`; import same
   type again → `IllegalArgumentException`.

### Phase 3: `TypeImportDialog`

**Goal**: UI for the manual import flow.

1. Create `TypeImportDialog` as a modal `JDialog`.
2. Fields:
   - "Type ID (hex):" text field.
   - "Server address (optional):" text field.
   - "Fetch" button.
3. On Fetch:
   - Parse hex → `MysterType`. Show error if invalid.
   - If server address provided, use it; otherwise show error "Server address required."
   - Fetch access list via `AccessListGetClient`. Show progress indicator.
   - On success: populate a read-only preview panel with name, description, policy from state.
     Enable "Import" button.
   - On failure: show error message.
4. On Import:
   - Call `tdList.importType(accessList)`.
   - Close dialog.
   - Show brief confirmation: "Type '{name}' added. Enable it in the Type Manager."
5. **Manual test**: paste a valid type hex + server address → fetch → confirm → type appears
   in `TypeManagerPreferences`.

### Phase 4: Wire Metadata Resolution into `TypeListerThread` / `ClientWindow`

**Goal**: unknown types in `ClientWindow` get their names resolved automatically.

1. Add `refreshTypeDisplay(MysterType)` to `TypeListerThread.TypeListener` interface.
2. `TypeListerThread` constructor: add `TypeDescriptionList`, `TypeMetadataCache`,
   `AccessListGetClient` parameters.
3. In the type-listing loop: after `listener.addItemToTypeList(type)`, call
   `cache.resolveAsync(type, address, client, () -> listener.refreshTypeDisplay(type))` if the
   type is not already in `tdList`.
4. `ClientWindow`:
   - Add `TypeMetadataCache cache` field (constructed per-window).
   - `addItemToTypeList`: use `cache.getDisplayName(type)` as fallback.
   - Implement `refreshTypeDisplay(MysterType type)`: scan `fileTypeList`, find the row whose
     `getObject().equals(type)`, update its display string, repaint.
   - Pass `cache`, `tdList`, and `accessListGetClient` to `TypeListerThread` constructor.
5. Add `AccessListGetClient` singleton (or per-connection instance) where `ClientWindow` is
   constructed — likely same place `AccessListManager` is wired in Phase 5 of M2.

### Phase 5: Import from `ClientWindow` Right-Click

**Goal**: one-click import for types visible in the connection window.

1. Add right-click `JPopupMenu` to `fileTypeList` in `ClientWindow`.
2. Show "Import this type…" menu item only when the selected type is not in `tdList`
   (i.e. it's currently displaying as hex or cache-resolved name but not locally known).
3. On click: open `TypeImportDialog` pre-filled with:
   - Type hex from the selected row's `MysterType`.
   - Current connected server address as the default server.
4. After successful import, the row in `fileTypeList` updates its display name from the
   `TypeDescriptionList` (via `typeDescriptionList.get(type)` which now returns a result).

### Phase 6: Import button in `TypeManagerPreferences`

**Goal**: allow import without needing a `ClientWindow` open.

1. Add "Import…" `JButton` to the button panel in `TypeManagerPreferences`.
2. On click: open `TypeImportDialog` with no pre-filled values.
3. No other changes needed — `importType()` fires the type-added event which
   `TypeManagerPreferences` already listens to.

---

## Tests / Verification

### Unit Tests (new or updated)

| Test class | What to test |
|---|---|
| `TestTypeMetadataCache` | Successful resolve; failed resolve; no-op on second call |
| `TestDefaultTypeDescriptionListImport` | Import valid access list → type added; duplicate → exception; `validate()` failure → exception |

### Manual QA Checklist

- [ ] Connect to a remote server in `ClientWindow`. Types with no local description show as hex strings initially.
- [ ] After a brief moment, unknown type hex strings resolve to human-readable names (async fetch).
- [ ] Closing and reopening `ClientWindow` starts fresh — names re-resolve (no disk persistence).
- [ ] Right-click an unrecognised type → "Import this type…" → confirm → type appears in Type Manager.
- [ ] Open Type Manager → Import button → paste hex + server → fetch → confirm → type appears.
- [ ] Imported type is disabled by default.
- [ ] Imported type opens read-only in the editor (no admin key).
- [ ] Importing a type that already exists shows an error.
- [ ] Importing a type with a corrupted/invalid access list shows an error (validation fails).

---

## Docs / Comments to Update

1. `TypeMetadataCache.java` — full Javadoc explaining transient-only semantics.
2. `DefaultTypeDescriptionList.importType()` — Javadoc distinguishing import from create.
3. `TypeListerThread` — update Javadoc to mention metadata resolution side-effect.
4. `docs/impl_summary/private-types-import.md` (create after implementation).

---

## Acceptance Criteria

- [ ] Unknown `MysterType` hex strings in `ClientWindow` resolve to names after access list fetch.
- [ ] Resolution is never written to disk — restart shows hex, then resolves again.
- [ ] User can import a type via `TypeManagerPreferences` Import button.
- [ ] User can import a type via right-click in `ClientWindow`.
- [ ] Imported types are registered as disabled and open as read-only in the editor.
- [ ] Importing a duplicate type fails gracefully with a user-visible error.
- [ ] Importing an access list that fails `validate()` fails gracefully.
- [ ] All new unit tests pass.
- [ ] All M1 and M2 tests still pass.

---

## Risks / Edge Cases / Rollout Notes

### Risks

1. **`AccessListGetClient` availability in `ClientWindow`** — `ClientWindow` is currently
   constructed without any reference to M1/M2 infrastructure. It will need `AccessListGetClient`
   and `TypeDescriptionList` injected. Check the constructor call site carefully.

2. **Race condition in `TypeMetadataCache`**: if `resolveAsync` is called twice for the same
   type before the first fetch completes, two fetches would fire. The sentinel written
   immediately before the fetch prevents this — the second call checks `hasAttempted()` and
   returns early.

3. **`TypeListener.refreshTypeDisplay` is a new interface method** — any other implementations
   of `TypeListener` (check if there are any beyond `ClientWindow`) must be updated.

4. **Server that served the type list may not serve that type's access list** — the access
   list is fetched from the same remote node, but that node might not have the access list
   on disk (it might not be the type's origin server). In that case the fetch fails silently
   and the hex string remains. This is acceptable for M3; a future milestone could try
   fetching from onramps listed in the access list.

### Edge Cases

- **Type is resolved in cache but then imported** — after import, `typeDescriptionList.get(type)`
  returns a real result, so the cache is bypassed. The display updates correctly.
- **Multiple `ClientWindow` instances open simultaneously** — each has its own `TypeMetadataCache`.
  Resolved names may differ momentarily between windows until both complete their fetches.
  This is fine; they will converge.
- **Import during active connection** — after import, `typeDescriptionList.get(type)` returns
  a result, so future `addItemToTypeList` calls will use it. Any existing row in `fileTypeList`
  won't retroactively update unless `refreshTypeDisplay` is called; that's acceptable.

---

**Plan Version**: 1.0
**Created**: 2026-02-24
**Milestone**: 3 of 3
**Depends on**: Milestone 2 (`private-types-access-lists-milestone2.md`) complete
**Status**: Ready for review

