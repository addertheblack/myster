# Private Types — Milestone 3: Type Metadata Resolution & Import

**Milestone**: 3 of N  
**Depends on**: M2 complete (`private-types-access-lists-milestone2.md`)  
**Status**: Implemented  
**Last revised**: 2026-03-03

---

## 1. Summary

Two related features, both driven by the same underlying operation — fetching a type's access
list from a remote node over TCP:

1. **Transient name resolution** — `ClientWindow` currently shows unknown types as raw hex
   strings. After this milestone the app silently fetches the access list from the connected
   server and replaces the hex string with a human-readable name. Nothing is saved; it
   resolves again each session.

2. **Right-click import** — the user right-clicks an unrecognised type in `ClientWindow` and
   chooses "Add this type". The app fetches the access list, permanently saves it, enables the
   type, and the rest of the app sees it immediately via the existing `typeEnabled` event.

---

## 2. Non-Goals

- Standalone import dialog / paste-hex / disk-file import (later milestone).
- "Import…" button in `TypeManagerPreferences`.
- Access-control enforcement in file serving or search.
- Resolving type names anywhere other than `ClientWindow` (Tracker, Search, etc.).
- Auto-importing types seen on the network — import is always an explicit user action.

---

## 3. Assumptions & Open Questions

None outstanding. All design questions from pre-planning review resolved.

---

## 4. Proposed Design

### Name resolution (transient)

When `TypeListerThread` finishes listing types from a server, for each type not already in
`TypeDescriptionList` it fires an async fetch of that type's access list. On success the
display name in the existing row is updated in-place. The cache is per-`ClientWindow`,
in-memory only, and discarded when the window closes or reconnects.

The row update uses a mutable name cell (`MutableSortableString`) stored alongside the row at
insert time, so the name can be replaced without removing and re-adding the row. This avoids
any selection disruption or row movement.

### Import (permanent)

Right-clicking an unrecognised type row shows a single menu item: "Add this type". Clicking
it fetches the access list from the currently-connected server (address already known),
validates and saves it via `TypeDescriptionList.importType()`, and enables the type. The
`typeEnabled` event then propagates to all subscribers (`TypeManagerPreferences`,
`FileTypeListManager`, `Tracker`) without any additional wiring.

Imported types have no admin key file on this machine, so `TypeEditorPanel` opens them
read-only automatically (key-file gate from M2).

Watchout for the case where the server doesn't have the access list for that type AND/OR where the types might not YET be populated with their access lists.

### Error feedback

`MessageField` (the status bar `JLabel` in `ClientWindow`) gains a `sayError()` mode: FlatLaf
themeable red foreground (`UIManager.getColor("Actions.Red")`) and a bundled warning SVG icon
sized to the font height to avoid layout reflow. The next `say()` call resets to normal.
See `myster-coding-conventions.md` → Icon Loading and `myster-important-patterns.md` →
FlatLaf Theming for the conventions behind this.

---

## 5. Architecture Connections

### New objects and how they plug in

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `TypeMetadataCache` | `ClientWindow` (one per window) | `TypeListerThread`, `ClientWindow` | `MysterStream.getAccessList` |
| `MysterStream.getAccessList` | n/a — new method on existing interface | `TypeMetadataCache`, `ClientWindow.importSelectedType` | `AccessListGetClient` (static, existing) |
| `TypeDescriptionList.importType` | n/a — new method on existing interface | `ClientWindow.importSelectedType` | `AccessListManager`, `CustomTypeManager`, `typeEnabled` event |
| `MutableSortableString` | `ClientWindow.addItemToTypeList` | `ClientWindow.refreshTypeDisplay` | `MCList` row (replaces bare `SortableString` for the name cell) |
| `MessageField.sayError` | n/a — new method on existing class | `ClientWindow.importSelectedType` | FlatLaf `UIManager` colour keys |

### Data flow — name resolution

`TypeListerThread` receives types from the server → for each unknown type, calls
`cache.resolveAsync(type, address, callback)` → cache fires `MysterStream.getAccessList` on a
background thread → on success updates the cached name → fires callback →
`TypeListerThread`'s `Util.invokeLater` wrapper dispatches to EDT →
`ClientWindow.refreshTypeDisplay` mutates the `MutableSortableString` in the existing row
and repaints.

### Data flow — import

User right-clicks → "Add this type" → `ClientWindow.importSelectedType` →
`protocol.getStream().getAccessList(address, type)` → on success →
`typeDescriptionList.importType(accessList)` → saves access list to disk, enables type, fires
`typeEnabled` → `TypeManagerPreferences`, `FileTypeListManager`, `Tracker` all update via
existing listener wiring → `ClientWindow.refreshTypeDisplay` updates the row name immediately.

### Key interface changes (architecture-level)

- **`MysterStream`** (`com.myster.net.client`) gains `getAccessList(MysterAddress, MysterType)`.
  Access list fetching belongs in the standard stream suite alongside `getTypes`,
  `getServerStats`, etc. `AccessListGetClient` stays all-static; `MysterStreamImpl` delegates
  to it. Only one implementation exists.

  **Note**: unlike all other `MysterStream` methods, `getAccessList` takes a `MysterAddress`
  rather than an existing `MysterSocket` — because `AccessListGetClient` opens its own
  fresh TCP connection internally. This is intentional: the access list fetch is a
  separate, independent connection, not piggybacked on the type-listing socket.

- **`TypeDescriptionList`** (`com.myster.type`) gains `importType(AccessList)`. This is the
  permanent-save path, distinct from `addCustomType` (which requires an admin key). It fires
  `typeEnabled`; there is no separate "type added" event.

- **`TypeListerThread.TypeListener`** (inner interface, `com.myster.client.ui`) gains
  `refreshTypeDisplay(MysterType)`. The constructor's `Util.invokeLater` wrapper already
  dispatches all listener calls to the EDT; the new method is covered by the same wrapper.
  Currently only `ClientWindow.startConnect()`'s anonymous class implements this interface.

---

## 6. Key Decisions & Edge Cases

**Access list fetching goes through `MysterStream`, not a new class.** `AccessListGetClient`
is all-static; the right integration point is the existing standard stream suite interface so
callers receive it through the normal `protocol.getStream()` dependency path.

**Resolution is transient by design.** Names are not saved. Import is the explicit opt-in
path. This avoids silently populating the type list.

**Import enables the type immediately.** The `typeEnabled` event handles all UI propagation
for free — no manual refresh calls needed anywhere in the app.

**Concurrent resolution prevented by sentinel.** `TypeMetadataCache` uses
`ConcurrentHashMap.putIfAbsent` with an empty-string sentinel so only one fetch per type runs,
even under concurrent access.

**`Util.invokeLater` double-dispatch hazard.** `TypeListerThread`'s constructor already wraps
all `TypeListener` calls with `Util.invokeLater`. The `onResolved` callback must be fired on
the background thread — do not add a second `invokeLater` inside `TypeMetadataCache`. See
`myster-important-patterns.md` → Threading & Concurrency.

**`typeDisplayNames` must be cleared on reconnect.** Every call site in `ClientWindow` that
clears `fileTypeList` must also clear `typeDisplayNames`, or stale `MutableSortableString`
references will accumulate.

---

## 7. Acceptance Criteria

- [ ] Unknown `MysterType` hex strings in `ClientWindow` resolve to human-readable names
      without row movement or selection disruption.
- [ ] Resolution is transient — reopening the window starts with hex strings again.
- [ ] Right-clicking any type row shows a popup. "Add this type" is enabled for unrecognised
      types and disabled (reading "Type already added") for known types.
- [ ] "Add this type" imports the type, enables it, and updates the row name immediately.
- [ ] Imported type appears in `TypeManagerPreferences` and all type pickers via `typeEnabled`
      propagation — no manual refresh required.
- [ ] Imported type opens read-only in the editor (no admin key on this machine).
- [ ] Import failure surfaces as a dark-red + warning-icon status bar message; no modal dialog.
- [ ] Error appearance adapts correctly on both light and dark FlatLaf themes.
- [ ] `say(...)` after an error resets the status bar to normal appearance.
- [ ] All M1/M2 tests still pass.

---

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected Files / Classes

**New:**
- `com/general/mclist/MutableSortableString.java`
- `com/myster/client/ui/TypeMetadataCache.java`
- `src/main/resources/com/general/util/warning-icon.svg`

**Modified:**
- `com/myster/net/client/MysterStream.java` — add `getAccessList`
- `com/myster/net/stream/client/MysterStreamImpl.java` — implement `getAccessList`
- `com/myster/type/TypeDescriptionList.java` — add `importType`
- `com/myster/type/DefaultTypeDescriptionList.java` — implement `importType`
- `com/myster/client/ui/TypeListerThread.java` — new `TypeListener` method; new constructor params; refactor `run()`
- `com/myster/client/ui/ClientWindow.java` — cache field; mutable name cells; right-click menu; `importSelectedType`
- `com/general/util/MessageField.java` — add `sayError`; update `say` to reset foreground/icon

---

## 9. Step-by-Step Implementation

### Phase 0 — `getAccessList` on `MysterStream`

1. Add to `MysterStream` interface:
   ```java
   PromiseFuture<Optional<AccessList>> getAccessList(MysterAddress server, MysterType type);
   ```
2. Implement in `MysterStreamImpl`:
   ```java
   @Override
   public PromiseFuture<Optional<AccessList>> getAccessList(MysterAddress server, MysterType type) {
       return AccessListGetClient.fetchAccessList(server, type);
   }
   ```
3. Compile — only `MysterStreamImpl` implements `MysterStream`.

### Phase 1 — `MutableSortableString`

```java
/** SortableString whose display value can be updated in-place on the EDT. */
public class MutableSortableString extends SortableString {
    public MutableSortableString(String s) { super(s); }
    public void setValue(String s) { this.string = s; }
}
```

### Phase 2 — `warning-icon.svg` + `MessageField.sayError`

1. Create `src/main/resources/com/general/util/warning-icon.svg`:
   ```xml
   <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16">
     <path d="M8 1 L15 14 L1 14 Z" fill="none" stroke="#DB5860" stroke-width="1.5"
           stroke-linejoin="round"/>
     <line x1="8" y1="6" x2="8" y2="10" stroke="#DB5860" stroke-width="1.5"
           stroke-linecap="round"/>
     <circle cx="8" cy="12" r="0.75" fill="#DB5860"/>
   </svg>
   ```
2. Update `MessageField`:
   - `say(String)`: reset with `setForeground(UIManager.getColor("Label.foreground"))`,
     `setIcon(null)`, then `setText(...)`.
   - Add `sayError(String)`:
     ```java
     public void sayError(String s) {
         setForeground(Optional.ofNullable(UIManager.getColor("Actions.Red"))
                               .orElse(new Color(0xDB, 0x58, 0x60)));
         int h = getFontMetrics(getFont()).getHeight();
         try { setIcon(IconLoader.loadSvg(MessageField.class, "warning-icon", h)); }
         catch (Exception ignored) { setIcon(null); }
         setText(s);
     }
     ```
3. Manual test on light + dark FlatLaf theme.

### Phase 3 — `TypeMetadataCache`

```java
/**
 * Per-ClientWindow transient cache of type display names fetched from remote nodes.
 * Nothing is written to disk. Thread-safe. resolveAsync fires its callback on the
 * background thread — EDT dispatch is the caller's responsibility.
 */
public class TypeMetadataCache {
    interface Fetcher { // package-private for testing
        PromiseFuture<Optional<AccessList>> fetch(MysterAddress address, MysterType type);
    }
    private final Fetcher fetcher;
    private final ConcurrentHashMap<MysterType, String> cache = new ConcurrentHashMap<>();

    public TypeMetadataCache(MysterStream stream) { this.fetcher = stream::getAccessList; }
    TypeMetadataCache(Fetcher fetcher) { this.fetcher = fetcher; }

    public String getDisplayName(MysterType type) {
        String v = cache.get(type);
        return (v != null && !v.isEmpty()) ? v : type.toHexString();
    }
    public boolean hasAttempted(MysterType type) { return cache.containsKey(type); }

    public void resolveAsync(MysterType type, MysterAddress from, Runnable onResolved) {
        if (cache.putIfAbsent(type, "") != null) return; // sentinel guards duplicate fetches
        PromiseFutures.execute(() -> {
            try {
                fetcher.fetch(from, type).get().ifPresent(al -> {
                    String name = al.getState().getName();
                    cache.put(type, (name != null && !name.isBlank()) ? name : type.toHexString());
                });
            } catch (Exception ignored) { /* sentinel stays; getDisplayName returns hex */ }
            onResolved.run(); // NOT on EDT — TypeListerThread's wrapper dispatches it
            return null;
        });
    }
}
```

### Phase 4 — `TypeDescriptionList.importType`

1. Add to `TypeDescriptionList` interface:
   ```java
   /** Saves the access list to disk, enables the type, fires typeEnabled.
    *  @throws IllegalArgumentException if the type is already known
    *  @throws IllegalStateException    if chain validation fails
    *  @throws IOException              if disk write fails */
   void importType(AccessList accessList) throws IOException;
   ```
2. Implement in `DefaultTypeDescriptionList` (synchronized): validate chain, guard duplicate
   with `getIndexFromType`, save via `accessListManager.saveAccessList`, enable via
   `customTypeManager.saveEnabled(type, true)`, build `TypeDescription`, add to `types` list,
   fire `dispatcher.fire().typeEnabled(new TypeDescriptionEvent(this, type))`.

### Phase 5 — Wire into `TypeListerThread` and `ClientWindow`

1. Add `refreshTypeDisplay(MysterType)` to `TypeListerThread.TypeListener`. Add it to the
   constructor's `Util.invokeLater` wrapper alongside existing methods.

2. Add nullable `TypeDescriptionList tdList` and `TypeMetadataCache cache` to
   `TypeListerThread` constructor. Existing callers pass `null`; null skips resolution.

3. Refactor `TypeListerThread.run()`: compute `MysterAddress.createMysterAddress(ip)` **once
   at the top** before the try/catch. Extract `resolveUnknownTypes(MysterType[], MysterAddress)`
   that skips known types and already-attempted types and calls `cache.resolveAsync` for the
   rest.

4. Run **Find Usages** on `TypeListener` before adding the new method — confirm only
   `ClientWindow.startConnect()` implements it.

5. In `ClientWindow`:
   - Construct `typeMetadataCache = new TypeMetadataCache(protocol.getStream())` in
     constructor body.
   - Add `Map<MysterType, MutableSortableString> typeDisplayNames = new HashMap<>()`.
   - Update `addItemToTypeList`: use `MutableSortableString` for the name cell, store ref in
     `typeDisplayNames`.
   - Add `refreshTypeDisplay(MysterType)`: look up cell, set value from `typeDescriptionList`
     (if now imported) or `typeMetadataCache`, then call `fileTypeList.repaint()`.
     **No re-sort required** — `init()` calls `fileTypeList.sortBy(-1)` which disables
     auto-sort on this list. Mutating the name cell is safe without re-sorting.
   - Update `startConnect()`: pass `typeDescriptionList` and `typeMetadataCache` to
     `TypeListerThread`; add `refreshTypeDisplay` to anonymous `TypeListener`.
   - `fileTypeList.clearAll()` is called in exactly one place: `stopConnect()`. Clear
     `typeDisplayNames` there too.

6. Add right-click `JPopupMenu` to `fileTypeList` in `init()`. Single item "Add this type".
   Use `mousePressed` + `mouseReleased` with `isPopupTrigger()` (cross-platform). Only show
   when the row's type is absent from `typeDescriptionList`.

7. Implement `importSelectedType()`: guard on selection and type not already known; resolve
   `currentip` to `MysterAddress` (call `sayError` if invalid); call
   `protocol.getStream().getAccessList(address, type).addCallListener(new CallAdapter<>(){...})`;
   `handleResult` calls `importType`, `refreshTypeDisplay`, `msg.say(...)`; `handleError`
   calls `msg.sayError(...)`. Both callbacks run on EDT by default — no `invokeLater` needed.

---

## 10. Tests to Write

| Test class | Verifies |
|---|---|
| `TestTypeMetadataCache` | resolve success; failure → hex; duplicate call → fetcher called once; null/blank name → hex |
| `TestDefaultTypeDescriptionListImport` | valid import → enabled + event fired; duplicate → `IllegalArgumentException`; bad chain → `IllegalStateException` |

Manual QA:
- [ ] Unknown types show hex then resolve in-place; no row movement.
- [ ] Reopen window → hex again (not persisted).
- [ ] Right-click unrecognised type → menu appears. Right-click known type → no menu.
- [ ] Import succeeds → row updates, type in Type Manager enabled, opens read-only.
- [ ] Import failure (server has no access list) → red + icon in status bar.
- [ ] Dark FlatLaf theme → colours adapt correctly.
- [ ] `say(...)` after error → status bar resets to normal.
- [ ] `typeEnabled` propagates to `Tracker`, `FileTypeListManager` without NPE.

---

## 11. Docs / Javadoc to Update

- `MutableSortableString` — class Javadoc: EDT-only mutation; repaint required after.
- `TypeMetadataCache` — class Javadoc: transient; `onResolved` fires on background thread.
- `MessageField.sayError` — resets on next `say()`; uses `"Actions.Red"` UIManager key.
- `MysterStream.getAccessList` — delegates to `AccessListGetClient`; standard suite member.
- `TypeDescriptionList.importType` — no admin key required; fires `typeEnabled`; contrast with `addCustomType`.
- `TypeListerThread` — class Javadoc: note async name-resolution side-effect.
- `docs/impl_summary/private-types-access-lists-milestone3.md` — create after implementation.
