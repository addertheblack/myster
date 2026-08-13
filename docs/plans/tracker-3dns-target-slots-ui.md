# Tracker 3DNS Target Slots UI

## 1. Summary

Enhance TrackerWindow's 3DNS category from a deduplicated server list into a dedicated 3DNS inspection view that shows retained target-slot entries with target CID, offset ordinate, side, server CID, server name, address, status, ping, and uptime.

## 2. Non-goals

- Do not change 3DNS lookup semantics, routing behavior, retention scoring, or persistence format.
- Do not expose the mutable private `ThreeDnsServerList.TargetSlot` class outside `ThreeDnsServerList`.
- Do not force LAN, bookmark, or file-type tracker views into the 3DNS model.
- Do not make 3DNS a `MysterType` or a normal type-shaped `ServerList`.
- Do not add network protocol fields or client lookup UI in this milestone.

## 3. Assumptions & open questions

- Assumption: the 3DNS tracker view is an introspection/debug view of the local retained finger table, not a view of arbitrary `findClosestByCid(...)` results.
- Assumption: a structurally immutable snapshot is enough. The snapshot should copy slot membership lists, but it may still reference existing `MysterServer` objects whose live stats can change.
- Assumption: rows should be flat slot-entry rows, not one row per target slot with packed left/right server lists. Flat rows preserve normal table sorting, selection, double-click, and bookmark actions.
- Assumption: the object stored in the 3DNS `JMCList` should be a denormalized UI row. It should contain the `MysterServer` for existing server actions plus immutable copied 3DNS column values such as target CID, server CID, bit index, side, retained address, and update time.
- Assumption: `IdentityNeighborSet` should stay as the operational closest-neighbor result. It is the wrong shape for this UI because it represents one query target, not the retained local finger table.
- Decision: the offset column displays the target offset as a power of two, rendered as `+2^0`, `+2^1`, `+2^2`, etc. with the exponent shown as a superscript. This is a UI label convention, not an expression the UI should evaluate to recover the target; `targetCid` is authoritative and the zero-based `bitIndex` remains the internal sort/key value.

## 4. Proposed design

The design should accept the asymmetry: 3DNS is not a server list, so the TrackerWindow should not pretend it is one. Keep the existing `JMCList<MysterServer>` for file-type, LAN, and bookmark views, and add a separate 3DNS panel with its own `JMCList<ThreeDnsTrackerRow>` or equivalent row type.

`ThreeDnsServerList` should expose an immutable target-slot snapshot model. The existing private mutable `TargetSlot` remains private and synchronized inside `ThreeDnsServerList`; callers receive snapshot records that contain `bitIndex`, `targetCid`, immutable `left` entries, and immutable `right` entries. The existing flat `snapshot()` can remain for seeds and current tests, but the new UI should use slot snapshots so it can display target-level data without reverse-engineering it from flattened entries.

The UI table should render one row per retained slot entry. The row object should be denormalized for table use: it contains one `MysterServer` plus copied slot/entry values for target CID, server CID, bit index, side, retained address, and update time. This keeps the `JMCList` row type aligned with what the table does, while still keeping `MysterServer` directly available for double-click and bookmark actions. A target with two left and two right retained servers would produce four rows. Empty target slots can be omitted in the first version; they do not have a selected server to open/bookmark and would make the table noisy.

TrackerWindow should own only the selection and view switching. The 3DNS-specific table, row item, column names, bookmark menu wiring, refresh/reload behavior, and double-click behavior must live outside `TrackerWindow` in new `com.myster.tracker.ui` classes. Do not add the new panel, row model, sortables, or renderers as nested classes inside `TrackerWindow`; that class is already crowded and should stay focused on the top-level tracker window.

## 5. Architecture connections

The core split is between the 3DNS maintenance structure and the tracker UI. `ThreeDnsServerList` owns mutable target slots; `Tracker` exposes immutable snapshots; `TrackerWindow` switches between the old generic server list panel and a dedicated 3DNS panel.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Immutable target-slot snapshot | `com.myster.threedns.ThreeDnsTargetSlotSnapshot` | `ThreeDnsServerList.snapshotTargetSlots()`, `Tracker.getThreeDnsTargetSlots()` | Private `ThreeDnsServerList.TargetSlot`, `ThreeDnsFingerEntry`, `ServerCid` |
| 3DNS tracker row model | `com.myster.tracker.ui.ThreeDnsTrackerRow` | `TrackerThreeDnsPanel` / `JMCList` | `ThreeDnsTargetSlotSnapshot`, `ThreeDnsFingerEntry`, `MysterServer` |
| Dedicated 3DNS panel | `com.myster.tracker.ui.TrackerThreeDnsPanel` | `TrackerWindow` | `JMCList`, `Tracker.getThreeDnsTargetSlots()`, bookmark APIs, `ClientWindow` |
| View switching in TrackerWindow | `TrackerWindow` | user selection through `TypeChoice` | Existing `TypeChoice.isThreeDns()`, existing generic tracker list |
| CID/offset display helpers | `TrackerThreeDnsPanel` initially, or small `ServerCid` helper if needed | 3DNS row sortables/renderers | `ServerCid.asHex()`, `ServerCid.bytes()`, `bitIndex`, Swing JLabel HTML |

Data flow:

1. `ThreeDnsServerList` maintains private mutable target slots under synchronization.
2. `Tracker.getThreeDnsTargetSlots()` returns immutable structural snapshots.
3. `TrackerThreeDnsPanel.load()` flattens each snapshot into one denormalized UI row per retained entry.
4. Selecting `3DNS` in `TrackerWindow` shows the 3DNS panel and hides the existing server-list pane.
5. Selecting any non-3DNS category shows the existing server-list pane and hides the 3DNS panel.

No on-disk or network format changes are required. The existing 3DNS preferences still store external names by `bit.<index>.<side>`.

## 6. Key decisions & edge cases

- Use a dedicated 3DNS panel despite the asymmetry. The current table is typed around `MysterServer`, but the 3DNS UI row is target-slot plus server-entry data.
- Keep 3DNS UI implementation out of `TrackerWindow`. `TrackerWindow` may hold a `TrackerThreeDnsPanel` field and switch cards, but it should not gain 3DNS-specific nested classes or detailed table/action code.
- Store denormalized 3DNS row objects in the panel's `JMCList`. Do not make the list hold `ThreeDnsTargetSlotSnapshot` directly because a slot may produce many visible rows, and do not make it hold `MysterServer` directly because that loses the target-specific columns.
- Keep `TargetSlot` private. Exposing it would leak mutable internals and synchronization assumptions from `ThreeDnsServerList`.
- Do not use `IdentityNeighborSet` for the tracker UI. It is correct for "closest to X" operations, but the tracker view is "what local target slots are currently retained."
- Keep row-level server actions. Because each row includes a `MysterServer`, double-click can still open `ClientWindow`, and bookmark context actions can still operate on the row's server.
- Preserve existing generic tracker behavior. File-type, LAN, and bookmark views should keep their current columns and code paths.
- Reload the 3DNS panel on 3DNS list-change events. Pool refresh/ping events can refresh visible server status cells without requiring a structural reload unless the list-change flag is also set.
- Repeated server entries are expected. The same server may appear in several target slots or on different sides; deduping would erase the information this view exists to show.
- Restored/down entries may appear in snapshots. The row should render status/ping like the generic tracker list and not assume every retained entry is currently usable.
- Offset display renders the target offset as `+2` with a superscript `bitIndex` exponent, producing labels such as `+2^0`, `+2^1`, `+2^2`, etc.; sorting still uses `bitIndex`, and `targetCid` remains the source of truth for the actual CID target.

## 7. Acceptance criteria

- [ ] Selecting `3DNS` in TrackerWindow displays a dedicated table with target-slot-aware rows rather than the generic deduplicated server table.
- [ ] The 3DNS table includes target CID, offset ordinate label, side, server CID, server name, address, status, ping, and uptime columns.
- [ ] Offset labels render with superscript exponents, such as `+2^0`, `+2^1`, `+2^2`, etc., while sorting in target-slot order.
- [ ] The same server can appear in multiple rows when it is retained for multiple target slots.
- [ ] The 3DNS table stores denormalized row objects that contain one `MysterServer` plus copied target-slot column values.
- [ ] The 3DNS table uses immutable snapshot data from `Tracker` and does not expose mutable `ThreeDnsServerList.TargetSlot`.
- [ ] Double-clicking a 3DNS row opens the selected server in `ClientWindow`.
- [ ] Bookmark and remove-bookmark context actions work for 3DNS rows.
- [ ] File-type, LAN, and bookmark tracker views keep their current table and behavior.
- [ ] Existing 3DNS seed and lookup APIs keep their current behavior.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `src/main/java/com/myster/threedns/ThreeDnsTargetSlotSnapshot.java` - new public immutable record for target-slot snapshots.
- `src/main/java/com/myster/threedns/ThreeDnsServerList.java` - add `snapshotTargetSlots()` and keep `TargetSlot` private.
- `src/main/java/com/myster/tracker/Tracker.java` - add `getThreeDnsTargetSlots()` as the UI/debug snapshot accessor.
- `src/main/java/com/myster/tracker/ui/TrackerThreeDnsPanel.java` - new dedicated Swing panel for the 3DNS table.
- `src/main/java/com/myster/tracker/ui/ThreeDnsTrackerRow.java` - new immutable denormalized row record for the 3DNS MCList.
- `src/main/java/com/myster/tracker/ui/TrackerWindow.java` - switch between the existing generic server-list pane and `TrackerThreeDnsPanel`.
- `src/main/java/com/myster/tracker/ui/OpenConnectionHandler.java` - either leave generic-list-only or extract a small reusable open-server helper.
- `src/test/java/com/myster/threedns/TestThreeDnsServerList.java` - add snapshot immutability and bit/target assertions.
- `src/test/java/com/myster/tracker/ui/...` - add focused tests if the project has practical Swing coverage; otherwise document manual smoke tests.

## 9. Step-by-step implementation

1. Add the immutable slot snapshot model.
   - Create `com.myster.threedns.ThreeDnsTargetSlotSnapshot`.
   - Shape:
     - `int bitIndex`
     - `ServerCid targetCid`
     - `List<ThreeDnsFingerEntry> left`
     - `List<ThreeDnsFingerEntry> right`
   - In the compact constructor, validate non-null fields and use `List.copyOf(...)` for both entry lists.
   - Optional convenience method: `List<ThreeDnsFingerEntry> entries()` returning left followed by right, copied or unmodifiable.

2. Add a slot snapshot accessor to `ThreeDnsServerList`.
   - Add `public synchronized List<ThreeDnsTargetSlotSnapshot> snapshotTargetSlots()`.
   - Iterate the private `targets` list in bit order.
   - For each private `TargetSlot`, create a `ThreeDnsTargetSlotSnapshot(target.bitIndex, target.targetCid, target.left, target.right)`.
   - Return `List.copyOf(...)`.
   - Do not expose `TargetSlot` or its mutable lists.
   - Keep existing `snapshot()` intact for current tests, seed generation, and any callers that want a flat entry list.

3. Add the tracker-level accessor.
   - In `Tracker`, add `public synchronized List<ThreeDnsTargetSlotSnapshot> getThreeDnsTargetSlots()`.
   - Implement it as `threeDns.map(ThreeDnsServerList::snapshotTargetSlots).orElseGet(List::of)`.
   - Leave `getAllThreeDns()` in place unless all callers are intentionally migrated. It is still a simple debug/server accessor and removing it is unnecessary churn.

4. Build the 3DNS row item.
   - Create `com.myster.tracker.ui.ThreeDnsTrackerRow` as an immutable denormalized row record, not as a nested type in `TrackerWindow`.
   - Shape:
     - `MysterServer server`
     - `ServerCid targetCid`
     - `ServerCid serverCid`
     - `int bitIndex`
     - `ThreeDnsFingerEntry.Side side`
     - `MysterAddress retainedAddress`
     - `long updateTimeMs`
   - Construct rows from `ThreeDnsTargetSlotSnapshot` plus each `ThreeDnsFingerEntry`, copying the slot and entry values into the row.
   - Keep `server` as a first-class field rather than burying it in a nested entry object because row actions need it frequently.
   - Treat the copied 3DNS fields as immutable row facts; live status, ping, uptime, and bookmark state should still be refreshed from `server`.
   - Add an `AbstractMCListItemInterface<ThreeDnsTrackerRow>` implementation in `TrackerThreeDnsPanel` or a small package-private helper class, but not in `TrackerWindow`.
   - Suggested columns:
     - bookmark icon
     - offset
     - side
     - target CID
     - server CID
     - server name
     - address
     - status
     - ping
     - uptime
   - Use short CID display in table cells, for example first 12 hex characters plus ellipsis, but sort on the full hex string or `ServerCid`.
   - Reuse or extract the existing tracker sortables for bookmark, status, ping, and uptime if practical. If extraction creates too much churn, duplicate these tiny private sortables in the new panel for this milestone.

5. Implement `TrackerThreeDnsPanel`.
   - Constructor dependencies:
     - `Tracker tracker`
     - `MysterFrameContext context`
   - Create `JMCList<ThreeDnsTrackerRow>` with the 3DNS-specific column count.
   - Configure column names and widths in the panel, not in `TrackerWindow`.
   - Implement:
     - `Container getPane()` or make the panel itself the component added to `TrackerWindow`
     - `load()`
     - `refresh()`
     - `hasVisiblePane()` if useful for timer gating
   - `load()`:
     - preserve selected row index
     - clear the list
     - call `tracker.getThreeDnsTargetSlots()`
     - flatten slots to denormalized rows by adding all `left` entries then all `right` entries
     - add row items
     - reselect the previous row if still valid
   - `refresh()`:
     - update row sortables from the row's live `MysterServer`
     - repaint the list

6. Wire row actions in `TrackerThreeDnsPanel`.
   - Double-click should open `ClientWindow` with the row server's best address.
   - The selected `MysterType` should be empty/null for 3DNS, matching current generic 3DNS behavior.
   - Bookmark menu actions should call `tracker.addBookmark(...)` and `tracker.removeBookmark(...)` using `row.server().getIdentity()`.
   - Bookmark menu enabled state should be based on `tracker.getBookmark(row.server().getIdentity())`.
   - Reuse the existing bookmark icon renderer if practical. If not, duplicate the renderer locally and consider extraction in a later cleanup.

7. Update `TrackerWindow` layout and switching.
   - Replace the direct `add(list.getPane(), ...)` call with a holder panel, for example `JPanel listHolder = new JPanel(new CardLayout())`.
   - Add the existing generic list pane under a card such as `"servers"`.
   - Add `TrackerThreeDnsPanel` under a card such as `"threeDns"`.
   - Keep the `TrackerWindow` changes limited to constructing the `TrackerThreeDnsPanel`, switching cards, and delegating `load()` / `refresh()` calls.
   - In `ChoiceListener.itemStateChanged(...)`, call a new method like `showSelectedViewAndLoad()`.
   - When `choice.isThreeDns()`:
     - show the 3DNS card
     - call `threeDnsPanel.load()`
   - Otherwise:
     - show the generic server-list card
     - call existing `loadTheList()`
   - Keep selection persistence logic unchanged from Part 1b.

8. Update refresh/reload handling.
   - `resetTimer()` should check whether the visible card is showing, not only `list.getPane().isShowing()`, or it should delegate to the active panel.
   - `checkForRefresh()`:
     - if 3DNS is selected and `reload` is set, call `threeDnsPanel.load()`
     - if 3DNS is selected and `refresh` is set, call `threeDnsPanel.refresh()`
     - otherwise preserve existing generic list behavior
   - `threeDnsServerAddedRemoved()` should still set `reloadList` only when `choice.isThreeDns()`.
   - Pool `serverRefresh` and `serverPing` can continue to set `refreshList`; the active branch decides which panel refreshes.

9. Handle offset display and sorting.
   - Add one helper in `TrackerThreeDnsPanel`, for example `offsetHtmlLabel(int bitIndex)`.
   - Display the zero-based `bitIndex` as the exponent in the power-of-two label:
     - bit `0` -> `+2^0`
     - bit `1` -> `+2^1`
     - bit `2` -> `+2^2`
     - bit `n` -> `+2^n`
   - Render the exponent with Swing's JLabel HTML support, for example an offset sortable whose display string is shaped like `<html>+2<sup>1</sup></html>`.
   - Prefer a small `SortableOffset` extending or mirroring `SortableLong`: keep the sortable numeric value as `bitIndex`, and make HTML only the `toString()` / renderer text.
   - If the default `JTable` renderer does not render the HTML string as expected, add a column-specific `DefaultTableCellRenderer` in `TrackerThreeDnsPanel` that sets the JLabel text to the same HTML label.
   - Do not parse or evaluate the display label to derive routing data; use the snapshot's `targetCid` for that.
   - Sort offset by numeric `bitIndex`, not by its display string.

10. Keep existing generic list code stable.
   - `extractServers()`, `loadTheList()`, `refreshTheList()`, and `TrackerMCListItem` should continue to serve non-3DNS views.
   - Avoid making `TrackerMCListItem` aware of `ThreeDnsFingerEntry`.
   - Avoid adding 3DNS-only branches inside every generic sortable; the new panel owns those concerns.
   - Do not move the 3DNS panel implementation into `TrackerWindow` as nested classes during implementation.

## 10. Tests to write

- `TestThreeDnsServerList`
  - `snapshotTargetSlots()` returns 128 slots in bit order.
  - each snapshot exposes the expected `bitIndex` and `targetCid`.
  - left and right lists are immutable copies.
  - existing flat `snapshot()`, `seeds(...)`, and `forTarget(...)` behavior is unchanged.

- `TestTrackerThreeDnsPanel` if Swing tests are practical
  - loads one row per retained left/right entry.
  - keeps duplicate servers when they belong to multiple target slots.
  - offset column displays superscript ordinate labels and sorts by bit index.
  - bookmark actions use the row server identity.

- Manual smoke checks if Swing UI tests are not practical
  - open TrackerWindow and select `3DNS`.
  - verify the dedicated 3DNS columns are visible.
  - verify target CID, superscript offset ordinate, side, and server CID are populated.
  - verify duplicate server rows can appear for different target slots.
  - double-click a row and confirm it opens a client window for that server.
  - use bookmark and remove-bookmark context menu actions on a 3DNS row.
  - switch between 3DNS, LAN, bookmarks, and a file type and confirm each view reloads correctly.

## 11. Docs / Javadoc to update

- Add Javadoc to `ThreeDnsTargetSlotSnapshot` explaining that it is an immutable structural snapshot for UI/debug inspection, not the mutable retention slot.
- Add Javadoc to `ThreeDnsServerList.snapshotTargetSlots()` describing ordering, immutability, and restored/down entry behavior.
- Add a short class comment to `TrackerThreeDnsPanel` explaining why 3DNS uses a separate table from the generic `MysterServer` tracker view.
- After implementation, write `docs/impl_summary/tracker-3dns-target-slots-ui.md`.
