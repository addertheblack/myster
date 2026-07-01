# Myster 3DNS - Part 1b: Tracker UI Integration

Prerequisite plan: [Myster 3DNS - Part 1a: Core Data Structures](myster-3dns-part-1a.md).

Companion plan: [Myster 3DNS - Part 2: Protocol and Lookup](myster-3dns-part-2.md).

## 1. Summary

Expose the Part 1a `ThreeDnsServerList` in TrackerWindow through a dedicated 3DNS selection, reusing the existing `MysterServer` table and list-change flow so developers can inspect retained 3DNS servers without adding protocol or lookup behavior.

## 2. Non-goals

- Do not change `Cid128`, `IdentityTracker`, pool closest lookup, or 3DNS retention algorithms in Part 1b except for tiny accessors needed by the UI.
- Do not add the UDP `FIND_CLOSEST` transaction.
- Do not add iterative network lookup or public-key candidate validation.
- Do not add custom 3DNS-only table columns in the first UI patch unless the existing table cannot display the list coherently.
- Do not treat 3DNS as a `MysterType` or a normal type-shaped `ServerList`.

## 3. Assumptions & open questions

- Assumption: Part 1a has landed and `Tracker` owns a `ThreeDnsServerList`.
- Assumption: Part 1a exposes enough snapshot/accessor state to return retained 3DNS servers for display.
- Assumption: the first UI view can reuse the existing `JMCList<MysterServer>` and existing TrackerWindow columns.
- Assumption: type-specific file count and rank cells can remain blank in the 3DNS view because no `MysterType` is selected.
- Assumption: `TypeChoice` is the right place to add a selectable 3DNS extra item beside LAN/bookmarks.
- Decision: Part 1b is a visibility/debugging bridge. Protocol, maintenance lookup, and richer 3DNS diagnostics remain in Part 2 or later UI work.

## 4. Proposed design

TrackerWindow gets a new 3DNS selection alongside file types, LAN, and bookmarks. `TypeChoice` should model this as an extra non-type choice, not as a synthetic `MysterType`.

`Tracker` exposes a UI-facing list accessor such as `List<MysterServer> getAllThreeDns()` that returns the retained 3DNS servers from `ThreeDnsServerList` in a stable order. The accessor should preserve the Part 1a rule that display rows are restored list references but should not show unusable/down rows if the chosen `ThreeDnsServerList` snapshot method is intended to expose currently usable servers only. If a restored-down row must remain visible for debugging, that should be an explicit method name and table behavior, not an accidental leak.

TrackerWindow's extraction and refresh paths treat 3DNS like LAN/bookmarks: no selected `MysterType`, existing row objects, existing context menus. Bookmark actions should continue to work because the selected rows are still `MysterServer` objects.

Selection persistence should reuse TrackerWindow's existing `SELECTED_TYPE` / `SELECTED_ITEM` scheme. Today the window stores `SELECTED_TYPE` as `Type`, `LAN`, or `Bookmark` and stores the selected type hex in `SELECTED_ITEM` only for real Myster types. Part 1b should add a `THREE_DNS` marker beside those existing markers, save an empty `SELECTED_ITEM` for it, and restore it through `choice.selectThreeDns()`. Existing saved file-type selections, LAN, and bookmarks should continue to restore as before.

## 5. Architecture connections

Part 1b is a UI layer over the local state introduced in Part 1a. It does not create new routing data or network behavior.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| 3DNS extra choice | `TypeChoice` | `TrackerWindow` | Existing LAN/bookmark extra-item behavior, `MysterType` selection flow |
| 3DNS UI accessor | `Tracker` | `TrackerWindow.extractServers()` | `ThreeDnsServerList.snapshot()` or equivalent Part 1a accessor |
| 3DNS list-change notification | `Tracker` / listener path | `TrackerWindow` reload logic | Existing `ListChangedListener` and tracker list refresh behavior |
| TrackerWindow 3DNS view | `TrackerWindow` | User/developer inspection | Existing `JMCList<MysterServer>`, context menus, rank/file-count rendering |

The UI flow is: the user selects "3DNS" in `TypeChoice`, TrackerWindow asks `Tracker` for retained 3DNS servers, the existing table renders those `MysterServer` rows, and 3DNS list-change events reload the table only while the 3DNS choice is selected.

## 6. Key decisions & edge cases

- 3DNS remains a non-type tracker view. `TypeChoice.getType()` should return empty when 3DNS is selected.
- Existing file-type, LAN, and bookmark selections must not change behavior.
- List-change reloads should be scoped so 3DNS updates do not unnecessarily rebuild unrelated views.
- If the selected 3DNS view has no rows, the table should behave like an empty LAN/bookmark view.
- Bookmark context menus should remain available for 3DNS rows because the rows are `MysterServer` instances.
- The first UI pass should avoid new table columns unless a later plan adds dedicated 3DNS diagnostics such as target bit, side, target CID, or retained/up status.

## 7. Acceptance criteria

- [ ] TrackerWindow has a selectable 3DNS view alongside existing type, LAN, and bookmark views.
- [ ] The 3DNS selection is persisted and restored without breaking existing saved type/LAN/bookmark selections.
- [ ] Selecting 3DNS displays retained 3DNS `MysterServer` rows from `Tracker`.
- [ ] Type-specific file count and rank columns remain blank or otherwise harmless when no `MysterType` is selected.
- [ ] 3DNS list changes refresh the table when the 3DNS view is selected and do not disrupt unrelated selected views.
- [ ] Existing tracker, LAN, bookmark, type-list, search, and server-stats UI behavior remains unchanged.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `src/main/java/com/myster/util/TypeChoice.java` - add a selectable "3DNS" extra item, selection helpers, and rebuild preservation for the 3DNS state.
- `src/main/java/com/myster/tracker/ui/TrackerWindow.java` - add 3DNS selected-type persistence, extraction, and refresh handling.
- `src/main/java/com/myster/tracker/Tracker.java` - add `getAllThreeDns()` or equivalent UI accessor and a 3DNS list-change notification hook if Part 1a did not add one.
- `src/test/java/com/myster/util/...` - add focused `TypeChoice` selection/restoration tests if existing UI utilities are testable.
- `src/test/java/com/myster/tracker/ui/...` - add TrackerWindow 3DNS extraction/refresh tests if the project has Swing test coverage; otherwise document a manual smoke check.

## 9. Step-by-step implementation

1. Add Tracker accessor support.
   - Add `List<MysterServer> getAllThreeDns()` or a clearly named equivalent in `Tracker`.
   - Source the list from `ThreeDnsServerList.snapshot()` or another Part 1a accessor.
   - Return a defensive copy or immutable list.
   - If Part 1a did not add notifications, add either a 3DNS method to `ListChangedListener` or a small dedicated listener. Prefer the smallest change that matches existing tracker refresh style.

2. Extend `TypeChoice`.
   - Add a selectable "3DNS" extra item beside LAN/bookmarks.
   - Add helpers such as `isThreeDns()` and `selectThreeDns()`.
   - Ensure `getType()` returns empty when 3DNS is selected.
   - Update `rebuildTypeList()` alongside its existing `wasLan` / `wasBookmark` handling: capture `wasThreeDns`, rebuild the model, then call `selectThreeDns()` if that was the selected extra item.
   - Keep LAN and bookmark selection behavior unchanged.

3. Update TrackerWindow selection persistence.
   - Add a `THREE_DNS` selected-type marker beside the existing `TYPE`, `LAN`, and `BOOKMARK` persisted values.
   - In the `WindowPrefDataKeeper.addFrame(...)` saver, extend the existing ternary so `choice.isThreeDns()` writes `THREE_DNS`; keep `SELECTED_ITEM` as the selected type hex only for real types.
   - In `initWindowLocations(...)`, extend the existing restore chain so `data.selectedType().equals(THREE_DNS)` calls `choice.selectThreeDns()`.
   - Keep the existing `SELECTED_TYPE` values compatible with old saved choices. While touching this code, prefer content checks such as `!data.selectedItem().isEmpty()` rather than reference comparison against `""`.
   - Confirm existing `SELECTED_TYPE` values still restore old choices.

4. Update TrackerWindow data extraction.
   - In `extractServers()`, return `tracker.getAllThreeDns()` when `TypeChoice.isThreeDns()` is true.
   - Keep the existing type, LAN, and bookmark branches unchanged.
   - Ensure `getMysterType()` or equivalent returns empty in the 3DNS branch so file count/rank renderers do not query type-specific data.

5. Update refresh handling.
   - Wire the new 3DNS list-change notification to reload the table only when 3DNS is selected.
   - Confirm normal type list, LAN, and bookmark notifications still reload their corresponding selected views.

6. Verify context menus and empty state.
   - Confirm rows in the 3DNS view are ordinary `MysterServer` rows.
   - Confirm bookmark context-menu actions still work on those rows.
   - Confirm an empty 3DNS retained list displays as an empty table without errors.

7. Stop Part 1b here.
   - Do not add datagram constants, lookup orchestration, validation, maintenance lookup, or custom 3DNS diagnostics.
   - Run focused UI/unit tests or manual Swing smoke checks.
   - Write `docs/impl_summary/myster-3dns-part1b.md` after implementation.

## 10. Tests to write

- `TestTypeChoiceThreeDns`
  - exposes/selects/restores a 3DNS extra item
  - returns empty `MysterType` while 3DNS is selected
  - preserves LAN/bookmark/type selection behavior

- `TestTrackerWindowThreeDns`
  - `extractServers()` uses `tracker.getAllThreeDns()` for the 3DNS view
  - 3DNS list-change notification reloads only when the 3DNS view is selected
  - selected 3DNS preference restores the 3DNS view
  - file count/rank rendering tolerates absent `MysterType`

- Manual smoke check if Swing UI tests are not practical
  - open TrackerWindow
  - select 3DNS
  - verify retained rows display or the empty table renders cleanly
  - verify bookmark context-menu actions still appear for 3DNS rows
  - close/reopen TrackerWindow and verify the 3DNS selection restores

## 11. Docs / Javadoc to update

- Add a short code comment in `TypeChoice` explaining that 3DNS is an extra tracker view, not a `MysterType`.
- Add a short code comment in `TrackerWindow` near selection persistence explaining the 3DNS marker.
- Add `docs/impl_summary/myster-3dns-part1b.md` after implementation.
