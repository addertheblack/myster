# Private Types Access Lists — Milestone 4: Member Management GUI

## Summary

Add a Members tab to `TypeEditorPanel` so that an admin (a node holding the Ed25519 admin key
for a type) can view, add, and remove members of a private type directly from the Type Manager
preferences panel.

A new `ServerPickerDialog` lets the admin locate any known Myster server in the local pool by
name or address and resolve it to a `Cid128` for use in a signed `AddMemberOp` block.

After this milestone the access list can be fully managed from the GUI, but enforcement is not
yet active — that comes in Milestone 5.

---

## Goals

1. **`TypeEditorPanel` Members tab**: visible only when the type is open in edit mode with an
   admin key present. Displays current members (server name if resolvable, role, abbreviated
   Cid128) and provides Add, Remove, and Change Role operations, each backed by a signed block
   appended to the access list.
2. **`ServerPickerDialog`**: a modal dialog sourced from `MysterServerPool` that lets the admin
   search by name or address and returns a `PickedServer` (Cid128 + display name) on
   confirmation.
3. **`TypeManagerPreferences`** and **`Myster.java`** wiring to pass `MysterServerPool` through
   to the editor panel.

---

## Non-Goals (Milestone 4)

- Access enforcement in any TCP or UDP handler — deferred to Milestone 5.
- Client-only node join requests — deferred to Milestone 6.
- Writers tab, Onramps tab in TypeEditorPanel — deferred.
- Multi-admin / multi-writer flows.
- Any policy knob changes beyond the existing `listFilesPublic` radio.

---

## Background

### The server-picker problem

When an admin wants to add a member, they know the target by a human name or IP address, not by
a raw `Cid128`.  The resolution chain is:

```
  server name / IP address
        ↓
  MysterServer  (from pool.forEach(...) or tracker.getQuickServerStats())
        ↓
  MysterServer.getIdentity()  →  MysterIdentity  →  PublicKey  (PublicKeyIdentity)
        ↓
  com.myster.identity.Util.generateCid(publicKey)  →  Cid128
```

Only servers with a `PublicKeyIdentity` can be added — we need the public key to derive the
`Cid128`.  Servers seen only as `MysterAddressIdentity` (no known public key) are excluded from
the picker list.

`MysterServerPool.lookupIdentityFromCid` goes the reverse direction: useful for resolving the
display name of existing members already in the access list.

---

## Proposed Design (High-Level)

### `TypeEditorPanel` — Members tab

The existing panel is a single flat form.  For types opened in edit mode **with an admin key**,
the flat form is wrapped in a `JTabbedPane`:

- **Metadata tab** — existing layout, unchanged.
- **Members tab** — new (see below).

Read-only edit mode (no admin key) and create mode keep the flat layout unchanged.

**Members tab layout**:
- `MCList` with columns: `Server Name | Role | Identity (Cid128 hex, abbreviated)`.
- Buttons: **Add Member…**, **Remove Member**, **Change Role** (toggles MEMBER ↔ ADMIN).
- Server name column resolved via `pool.lookupIdentityFromCid(cid)` →
  `pool.getCachedMysterServer(identity)` → `getServerName()`, falling back to Cid128 hex if
  the server is not in the pool.

**Add Member…**:
1. Opens `ServerPickerDialog`.
2. On confirmation: `editAccessList.appendBlock(new AddMemberOp(picked.cid(), Role.MEMBER), editAdminKeyPair.get())`
3. `accessListManager.saveAccessList(editAccessList)`
4. Refresh table.

**Remove Member**:
1. `editAccessList.appendBlock(new RemoveMemberOp(selectedCid), editAdminKeyPair.get())`
2. Save, refresh.

**Change Role**:
1. Read current role from the selected row.
2. Append `new AddMemberOp(selectedCid, toggledRole)` — `AccessListState.applyOperation`
   overwrites the existing entry for the same `Cid128`.
3. Save, refresh.

All errors displayed via `AnswerDialog` (never `JOptionPane`).

### `ServerPickerDialog`

A small modal `JDialog`:

```
[ Filter by name or address: ________________ ]
[ -----------------------------------------  ]
[ Server Name          | Address   | Status  ]
[ -----------------------------------------  ]
[ My Home Server       | 1.2.3.4   | Up      ]
[ ...                                         ]
[ -----------------------------------------  ]
[ Cancel ]         [ Add Selected ]
```

- Sources list from `pool.forEach(...)`, keeping only servers with `PublicKeyIdentity`.
- Live-filters on the text field input (case-insensitive substring on name and address string).
- Shows "No servers found" placeholder when the list is empty.
- On "Add Selected": derives `Cid128` from `PublicKeyIdentity.getPublicKey()` via
  `com.myster.identity.Util.generateCid(publicKey)`. Returns a `PickedServer` record.
- On "Cancel" or close: returns `null`.

---

## Affected Modules / Packages

| Package | Component | Change |
|---|---|---|
| `com.myster.type.ui` | `TypeEditorPanel` | Add `JTabbedPane`; Members tab; accept `MysterServerPool` |
| `com.myster.type.ui` | `ServerPickerDialog` (new) | Server search & selection modal |
| `com.myster.type.ui` | `TypeManagerPreferences` | Accept and pass through `MysterServerPool` |
| `com.myster.Myster` | main wiring | Pass `pool` to `TypeManagerPreferences` |

---

## Files / Classes to Create or Change

### Create

#### 1. `com/myster/type/ui/ServerPickerDialog.java`

```java
/**
 * Modal dialog for picking a Myster server from the known server pool.
 *
 * <p>Used by {@link TypeEditorPanel}'s Members tab to resolve a server selection to a
 * {@link com.myster.identity.Cid128} for use in an {@link com.myster.access.AddMemberOp}.
 *
 * <p>Only servers with a {@link com.myster.tracker.PublicKeyIdentity} are listed — a public
 * key is required to derive the {@link com.myster.identity.Cid128}.
 */
public class ServerPickerDialog extends JDialog {

    /** Returned on confirmation; null if the dialog was cancelled. */
    public record PickedServer(Cid128 cid, String displayName) {}

    public ServerPickerDialog(Frame parent, MysterServerPool pool) { ... }

    /** Shows the dialog modally. Returns the chosen server, or null if cancelled. */
    public PickedServer showAndWait() { ... }
}
```

### Modify

#### 2. `com/myster/type/ui/TypeEditorPanel.java`

- Accept a new `MysterServerPool pool` parameter in the constructor (both the create-mode and
  edit-mode overloads).
- In edit mode with admin key: wrap the existing form in a `JTabbedPane`; "Metadata" is tab 0,
  "Members" is tab 1.
- Members tab implemented as described in the design section above.
- Constructor overloads that omit `pool` (for tests or create-mode) pass `null`; the Members
  tab is simply not added if `pool` is null or no admin key is present.

#### 3. `com/myster/type/ui/TypeManagerPreferences.java`

- Add `MysterServerPool pool` to constructor.
- Pass it through when constructing `TypeEditorPanel`.

#### 4. `com/myster/Myster.java`

- Pass `pool` when constructing `TypeManagerPreferences`.

---

## Step-by-Step Implementation Plan

### Phase 1: `ServerPickerDialog`

1. Create `ServerPickerDialog.java`.
2. Constructor stores `pool` reference; `showAndWait()` initialises the UI on first call.
3. Build the filtered list: call `pool.forEach(server -> ...)`, keep only those whose
   `getIdentity()` is a `PublicKeyIdentity`.
4. Populate `MCList` columns: server name, best address string, "Up" / "Down" / "Untried".
5. Wire the filter text field: `DocumentListener` on every change re-runs the filter (case-
   insensitive substring match against name and address string).
6. "Add Selected" button: enabled only when a row is selected. On click, derives `Cid128` and
   closes with `PickedServer` result.
7. "Cancel" / window close: closes with `null` result.
8. **Manual test**: open dialog from a test main, verify list populates, filter narrows results,
   selecting a row and clicking Add Selected returns a non-null `PickedServer`.

### Phase 2: `TypeEditorPanel` Members tab

1. Add `MysterServerPool pool` parameter to both constructor overloads. Store as field.
2. In `layoutComponents()`, detect edit-mode-with-admin-key:
   - If true: create a `JTabbedPane`, add the existing form panel as "Metadata", add a new
     `buildMembersTab()` panel as "Members".
   - If false: lay out as before (flat form, no tabs).
3. `buildMembersTab()`:
   - Creates `MCList` with three columns.
   - Calls `populateMembers()` to fill rows from
     `editAccessList.get().getState().getMembers().entrySet()`.
   - Name resolution: for each `Cid128`, call `pool.lookupIdentityFromCid(cid)` →
     `pool.getCachedMysterServer(identity)` → `getServerName()`. Fall back to
     `cid.asHex().substring(0, 12) + "…"` if not found.
   - Wires the three buttons.
4. `addMember()`: opens `ServerPickerDialog(SwingUtilities.getWindowAncestor(this), pool)`,
   gets result, appends block, saves, refreshes.
5. `removeMember()`: appends `RemoveMemberOp`, saves, refreshes.
6. `changeRole()`: appends `AddMemberOp` with toggled role, saves, refreshes.
7. All error paths use `AnswerDialog`.
8. **Manual test**: create a private type, open editor, switch to Members tab, confirm it is
   empty, add a server from the picker, confirm the row appears, check the access list file on
   disk for the new block.

### Phase 3: Wiring

1. Add `MysterServerPool pool` to `TypeManagerPreferences` constructor; pass to
   `TypeEditorPanel` at construction.
2. In `Myster.java`, pass `pool` when constructing `TypeManagerPreferences`.
3. **Compile check**: ensure no call site of `TypeEditorPanel` or `TypeManagerPreferences`
   is missed (the compiler will flag them).

---

## Tests / Verification

### Unit Tests

| Test class | Coverage |
|---|---|
| `TestServerPickerDialog` | `PickedServer` returned with correct `Cid128`; null returned on cancel; servers without `PublicKeyIdentity` excluded |

### Manual QA Checklist

- [ ] Open Type Manager. Create a private type. Open the editor — Members tab is visible.
- [ ] Members tab shows an empty list initially.
- [ ] Click "Add Member…". `ServerPickerDialog` opens and shows known servers.
- [ ] Typing in the filter box narrows the list.
- [ ] Select a server, click "Add Selected". Row appears in the Members table.
- [ ] The access list file on disk for that type contains a new `AddMemberOp` block.
- [ ] Select the added member, click "Remove Member". Row disappears; `RemoveMemberOp` block
      appears in the file.
- [ ] "Change Role" toggles the role and appends a new `AddMemberOp` block.
- [ ] Opening the editor for a type with no admin key shows no Members tab (read-only flat
      layout unchanged).
- [ ] Opening the editor for a public type with an admin key shows the Members tab (members
      are irrelevant for public types but the tab can be present; adding members there does no
      harm and prepares for future policy changes).

---

## Docs / Comments to Update

1. `ServerPickerDialog` — full Javadoc explaining the `PublicKeyIdentity` constraint and the
   `PickedServer` return type.
2. `TypeEditorPanel` — update class Javadoc to mention the Members tab and the `pool` parameter.
3. `TypeManagerPreferences` — update constructor Javadoc.
4. `docs/impl_summary/private-types-access-lists-milestone4.md` — create after implementation.

---

## Acceptance Criteria

- [ ] `TypeEditorPanel` shows a "Members" tab in edit mode when the admin key is present.
- [ ] `ServerPickerDialog` correctly resolves the selected server to a `Cid128` via
      `PublicKeyIdentity`.
- [ ] Adding a member appends a correctly signed `AddMemberOp` block and saves to disk.
- [ ] Removing a member appends a `RemoveMemberOp` block and saves to disk.
- [ ] Changing role appends an `AddMemberOp` block with the new role and saves to disk.
- [ ] Servers without a known public key do not appear in `ServerPickerDialog`.
- [ ] No `JOptionPane` calls — all errors go through `AnswerDialog`.
- [ ] All new unit tests pass.
- [ ] All existing M1–M3 tests still pass.

---

## Risks / Edge Cases / Rollout Notes

- **`TypeEditorPanel` constructor signature change** — check for all call sites including test
  classes that construct `TypeEditorPanel` directly. The compiler will flag them.

- **`ServerPickerDialog` opened before pool is populated** (immediately after startup). The
  list will be empty or sparse. Show "No servers found" placeholder; the user should wait for
  the pool to fill up.

- **Members tab on a public type** — functionally harmless. The admin can add members but
  enforcement (M5) will not check them because `listFilesPublic == true`. The tab is still
  useful for pre-populating members before switching the type to private.

- **`pool` is null** in test stubs or create-mode paths that don't pass one. Guard with
  `if (pool == null) return;` before opening the picker dialog, and omit the Members tab
  entirely if `pool` is null.

---

**Plan Version**: 1.0 (restructured 2026-03-02)
**Created**: 2026-03-01
**Milestone**: 4 of 6
**Depends on**: Milestone 3 (`private-types-access-lists-milestone3.md`) complete
**Status**: Ready for implementation



